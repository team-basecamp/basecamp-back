package com.basecamp.backend.domain.post.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.storage.FileStorageService;
import com.basecamp.backend.common.storage.ImageCategory;
import com.basecamp.backend.common.storage.MinioProperties;
import com.basecamp.backend.common.storage.StoredObject;
import com.basecamp.backend.domain.comment.dto.PostCommentCount;
import com.basecamp.backend.domain.comment.repository.CommentRepository;
import com.basecamp.backend.domain.post.dto.request.PostCreateRequest;
import com.basecamp.backend.domain.post.dto.request.PostCursorRequest;
import com.basecamp.backend.domain.post.dto.request.PostReportRequest;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.MyPostCursorResponse;
import com.basecamp.backend.domain.post.dto.response.PostDetailResponse;
import com.basecamp.backend.domain.post.dto.response.PostListCursorResponse;
import com.basecamp.backend.domain.post.dto.response.PostReportResponse;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.PostCategory;
import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.post.entity.PostStatus;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import com.basecamp.backend.domain.post.repository.PostReportRepository;
import com.basecamp.backend.domain.post.repository.PostRepository;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

// 게시글 비즈니스 로직. 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 따로 건다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

  // "전체" 탭을 위한 값. DB에 ALL이라는 카테고리를 만들지 않고, 카테고리 조건 자체를 빼는 것으로 처리한다.
  private static final String CATEGORY_ALL = "ALL";

  // 한 번에 내려줄 수 있는 게시글 수 상한.
  // size는 클라이언트가 마음대로 넣는 값이라 막지 않으면 size=100000 한 방으로 테이블을 통째로 퍼갈 수 있다.
  private static final int MAX_PAGE_SIZE = 50;

  // 게시글 저장/조회 리포지토리
  private final PostRepository postRepository;
  // 작성자 회원 조회 리포지토리
  private final UserRepository userRepository;
  // 게시글 신고 저장/조회 리포지토리
  private final PostReportRepository postReportRepository;
  // 목록의 댓글 수 집계용 리포지토리 (post_id 단위 GROUP BY COUNT)
  private final CommentRepository commentRepository;
  // 첨부 이미지를 저장소(MinIO)에 올리고 공개 URL을 돌려주는 저장 서비스
  private final FileStorageService fileStorageService;
  // 게시글에서 떨어져 나간 이미지 행을 정리하기 위한 리포지토리
  private final ImageRepository imageRepository;
  // 첨부 개수 상한 등 업로드 정책 (수정은 기존+신규 합계로 상한을 봐야 한다)
  private final MinioProperties minioProperties;

  // 게시글 목록 조회: 카테고리로 걸러 최신순 한 페이지를 반환한다. (클래스 기본 readOnly 트랜잭션)
  // category가 없거나 ALL이면 3개 카테고리 전부, 즉 카테고리 조건을 걸지 않은 결과를 준다.
  // 목록에는 ACTIVE만 노출한다. 삭제된 글은 물론이고, 블라인드된 글도 제목이 남으면 가린 의미가 없다.
  //
  // 커서 페이징이다. cursor가 없으면 첫 페이지, 있으면 그 커서 "다음"부터 size건을 준다.
  public PostListCursorResponse getList(String category, String cursor, int size) {
    PostCategory filter = resolveCategory(category);
    int limit = resolveSize(size);
    PostCursorRequest decoded = PostCursorRequest.decode(cursor);

    // 다음 페이지 존재 여부를 알아내려고 한 건 더 조회한다. 초과분은 응답 DTO가 잘라낸다.
    List<Post> lookahead = findPage(filter, decoded, Limit.of(limit + 1));

    // 이 페이지 게시글들의 댓글 수를 한 번에 집계한다. (게시글마다 count를 날리면 N+1)
    Map<Long, Integer> commentCounts = countCommentsByPost(lookahead);

    return PostListCursorResponse.of(lookahead, limit, commentCounts);
  }

  // 마이페이지 "내가 쓴 게시글" 목록 조회: 로그인 회원 본인의 글을 최신순 한 페이지로 반환한다. (클래스 기본 readOnly 트랜잭션)
  // 공용 목록(getList)과 커서 방식 · 댓글 수 집계는 동일하고, 카테고리 대신 작성자(userId)로 거른다.
  // userId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
  // 목록에는 ACTIVE만 노출한다. 삭제(DELETED)·블라인드(BLINDED)된 글은 공용 목록과 같은 기준으로 가린다.
  public MyPostCursorResponse getMyPosts(Long userId, String cursor, int size) {
    int limit = resolveSize(size);
    PostCursorRequest decoded = PostCursorRequest.decode(cursor);

    // 다음 페이지 존재 여부를 알아내려고 한 건 더 조회한다. 초과분은 응답 DTO가 잘라낸다.
    List<Post> lookahead =
        (decoded == null)
            ? postRepository.findFirstPageByUser(PostStatus.ACTIVE, userId, Limit.of(limit + 1))
            : postRepository.findNextPageByUser(
                PostStatus.ACTIVE,
                userId,
                decoded.createdAt(),
                decoded.postId(),
                Limit.of(limit + 1));

    // 이 페이지 게시글들의 댓글 수를 한 번에 집계한다. (게시글마다 count를 날리면 N+1)
    Map<Long, Integer> commentCounts = countCommentsByPost(lookahead);

    return MyPostCursorResponse.of(lookahead, limit, commentCounts);
  }

  // 목록에 실린 게시글들의 댓글 수를 post_id → count 맵으로 만든다.
  // lookahead 전체(초과분 1건 포함)의 post_id로 집계하지만, 응답에 안 쓰이는 그 한 건은 버려질 뿐 문제 없다.
  // 댓글이 없는 게시글은 집계 결과에 아예 없으므로 맵에도 키가 없고, 조회측에서 0으로 채운다.
  private Map<Long, Integer> countCommentsByPost(List<Post> posts) {
    if (posts.isEmpty()) {
      return Map.of();
    }

    List<Long> postIds = posts.stream().map(Post::getPostId).toList();

    return commentRepository.countByPostIds(postIds).stream()
        .collect(Collectors.toMap(PostCommentCount::postId, row -> Math.toIntExact(row.count())));
  }

  // 카테고리 유무 · 커서 유무 4가지 조합을 각 전용 쿼리로 보낸다.
  private List<Post> findPage(PostCategory category, PostCursorRequest cursor, Limit limit) {
    if (category == null) {
      return (cursor == null)
          ? postRepository.findFirstPage(PostStatus.ACTIVE, limit)
          : postRepository.findNextPage(
              PostStatus.ACTIVE, cursor.createdAt(), cursor.postId(), limit);
    }

    return (cursor == null)
        ? postRepository.findFirstPageByCategory(PostStatus.ACTIVE, category, limit)
        : postRepository.findNextPageByCategory(
            PostStatus.ACTIVE, category, cursor.createdAt(), cursor.postId(), limit);
  }

  // 요청 size를 검증한다. 0 이하는 의미가 없고, 상한을 넘으면 조용히 깎지 않고 400으로 알려준다.
  // 말없이 50으로 줄이면 클라이언트는 100건을 받은 줄 알고 51번째 글부터 건너뛴다.
  private int resolveSize(int size) {
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    return size;
  }

  // 요청으로 들어온 category를 조회 조건으로 변환한다.
  // 전체 조회(=조건 없음)면 null, 특정 카테고리면 그 값. 정의되지 않은 값이면 400으로 막는다.
  // 값을 검증하지 않고 그대로 넘기면 오타난 카테고리가 조용히 빈 목록을 반환해 프런트가 원인을 못 찾는다.
  private PostCategory resolveCategory(String category) {
    if (category == null || category.isBlank()) {
      return null;
    }

    // "전체" 탭은 특정 카테고리가 아니라 "카테고리 조건 없음"이다. enum 파싱 전에 먼저 걸러 null로 돌려준다.
    if (CATEGORY_ALL.equalsIgnoreCase(category.trim())) {
      return null;
    }

    // 정규화·검증은 PostCategory.from이 맡는다. 정의되지 않은 값이면 여기서 400(INVALID_INPUT_VALUE).
    return PostCategory.from(category);
  }

  // 게시글 상세 조회: 단건을 조회해 조회수를 1 올리고 상세 응답으로 반환한다. (쓰기 트랜잭션)
  //
  // 조회인데 @Transactional(readOnly = false)인 이유: 조회수 증가가 쓰기다.
  // 클래스 기본값인 readOnly 트랜잭션 안에서 update를 실행하면 Hibernate가 FlushMode.MANUAL이라
  // 예외도 없이 조용히 반영되지 않는다. 반드시 메서드에서 readOnly를 덮어써야 한다.
  //
  // viewerId는 조회한 회원 id다. 작성자 본인의 조회는 조회수에 넣지 않는다(아래 shouldCountView 참고).
  // 상세 조회는 SecurityConfig의 anyRequest().authenticated()에 걸리는 인증 필수 경로라 항상 값이 있다.
  //
  // 노출 정책 — posts.status에 따라 갈린다.
  //   ACTIVE  : 정상 반환
  //   DELETED : 소프트 삭제된 글. 없는 글과 구분되면 "삭제된 글이 여기 있었다"는 사실이 새므로 404로 통일.
  //   BLINDED : 관리자가 가린 글. 삭제와 달리 존재 자체는 감출 필요가 없어 사유를 알 수 있는 403으로 구분.
  @Transactional
  public PostDetailResponse getDetail(Long postId, Long viewerId) {
    // 응답에 nickname과 첨부 이미지가 필요하므로 작성자·이미지까지 fetch join으로 함께 로딩한다.
    Post post =
        postRepository
            .findWithUserByPostId(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

    if (post.getStatus() == PostStatus.DELETED) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }

    if (post.getStatus() == PostStatus.BLINDED) {
      throw new BusinessException(ErrorCode.POST_BLINDED);
    }

    // 노출 정책을 통과한 뒤에 올린다. 위 두 분기는 예외로 빠져나가므로
    // 404·403으로 가려진 글의 조회수는 오르지 않는다.
    if (!shouldCountView(post, viewerId)) {
      return PostDetailResponse.from(post);
    }

    postRepository.increaseViewCount(postId);

    // 벌크 update는 DB만 바꾸고 영속성 컨텍스트의 post는 옛 값을 그대로 들고 있어 +1을 직접 얹어 응답한다.
    return PostDetailResponse.from(post, post.getViewCount() + 1);
  }

  // 이 조회를 조회수에 반영할지 판단한다. 작성자 본인의 조회는 반영하지 않는다.
  //
  // 본인 조회를 빼는 이유는 두 가지다.
  //   1. 프런트가 수정 폼 초기값을 채우려고 상세 조회를 그대로 재사용한다. 이걸 세지 않으면
  //      글쓴이가 수정 화면에 들어갈 때마다 자기 글 조회수가 오른다.
  //      (서버가 판단하므로 프런트가 "수정 진입일 때는 다른 API" 같은 규칙을 기억할 필요가 없다.)
  //   2. 본인 새로고침으로 조회수가 부풀지 않는다.
  //
  // 다만 이것은 정책이지 어뷰징 방어가 아니다. 다른 계정으로 열면 그대로 오른다.
  // 같은 사람의 반복 조회까지 막으려면 조회 이력을 따로 저장해야 하는데, 지금은 그 저장소를 두지 않는다.
  private boolean shouldCountView(Post post, Long viewerId) {
    // viewerId는 인증 필수 경로라 null이 아니지만, 비로그인 허용으로 정책이 바뀌면 null이 들어올 수 있다.
    // 그때 NPE로 상세 조회 전체가 깨지는 대신 "익명은 카운트한다"로 안전하게 떨어지도록 먼저 걸러둔다.
    if (viewerId == null) {
      return true;
    }
    return !viewerId.equals(post.getUser().getId());
  }

  // 게시글 작성: 작성자를 검증하고 첨부 이미지를 저장한 뒤 새 글을 저장하고 상세 응답으로 반환한다. (쓰기 트랜잭션)
  // images는 선택 사항(null/빈 목록 가능)이며, 있으면 로컬 저장소에 올린 상대경로로 Image를 만들어 함께 저장한다.
  @Transactional
  public PostDetailResponse createPost(
      Long userId, PostCreateRequest request, List<MultipartFile> images) {
    // 작성자 회원을 먼저 조회한다. 응답에 nickname을 담아야 하므로 프록시(getReferenceById)가 아닌
    // findById로 실제 로딩하고, 존재하지 않으면 예외로 막는다.
    // (JWT는 통과했지만 탈퇴/삭제 등으로 회원이 사라졌을 수 있어 DB 존재 여부를 최종 검증한다.)
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // 카테고리 문자열을 enum으로 확정한다. 요청 DTO의 @Pattern이 1차로 걸러 주지만,
    // 서비스 경계에서 다시 검증해 유효하지 않으면 400(INVALID_INPUT_VALUE)으로 막는다.
    Post post =
        new Post(user, PostCategory.from(request.category()), request.title(), request.content());

    // 첨부 이미지를 저장소에 올려 공개 URL과 객체 키를 받고, 그것으로 Image를 만들어 게시글에 붙인다.
    // storeAll이 null/빈 목록·형식·개수 검증을 담당하므로 여기서는 반환값만 매핑한다.
    List<StoredObject> storedObjects = fileStorageService.storeAll(images, ImageCategory.POST);
    List<String> storedKeys = storedObjects.stream().map(StoredObject::objectKey).toList();
    List<Image> attachedImages =
        storedObjects.stream().map(o -> Image.ofMinio(o.url(), o.objectKey())).toList();
    post.attachImages(attachedImages);

    // 객체는 이미 저장소에 올라갔지만 DB 트랜잭션은 아직 커밋 전이다. save() 이후의 flush/커밋 실패로
    // 트랜잭션이 롤백되면 객체만 고아로 남으므로, 커밋이 성공하지 못한 경우(afterCompletion status가
    // COMMITTED가 아닌 모든 경우 = 롤백·커밋 실패)에 한해 저장했던 객체를 되돌린다.
    if (!storedKeys.isEmpty()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_COMMITTED) {
                return;
              }
              for (String key : storedKeys) {
                try {
                  fileStorageService.deleteByKey(key);
                } catch (RuntimeException e) {
                  // 보상 삭제 실패가 원래 실패 원인을 덮지 않도록 로그만 남긴다.
                  log.warn("게시글 작성 롤백 중 이미지 삭제 실패. objectKey={}", key, e);
                }
              }
            }
          });
    }

    // 저장한 뒤 방금 쓴 게시글을 그대로 볼 수 있게 응답 DTO로 변환해 반환.
    // user가 이미 로딩된 상태라 from()에서 nickname 접근 시 추가 쿼리가 발생하지 않는다.
    // cascade=PERSIST 로 Image 행과 post_images 연결이 함께 저장된다.
    Post saved = postRepository.save(post);
    return PostDetailResponse.from(saved);
  }

  // 게시글 수정: 작성자 본인의 글을 조회해 본문과 첨부 이미지를 바꾸고 상세 응답으로 반환한다. (쓰기 트랜잭션)
  // userId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
  //
  // 이미지는 PostMapping 의미대로 "전체 교체"다. 최종 목록 = request.keepImageUrls(남길 기존 것) + images(새로 올린 것) 이고,
  // 남기지 않은 기존 이미지는 게시글에서 떼고 images 행과 디스크 파일까지 지운다.
  // (남는 파일을 방치하면 디스크만 계속 불어나고, 경로를 아는 사람은 삭제한 사진을 계속 열어볼 수 있다.)
  @Transactional
  public PostDetailResponse update(
      Long userId, Long postId, PostUpdateRequest request, List<MultipartFile> images) {
    // 수정할 게시글 조회. 응답의 nickname·기존 이미지가 모두 필요하므로 작성자·이미지까지 함께 로딩한다.
    Post post =
        postRepository
            .findWithUserByPostId(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

    // 상세 조회와 같은 노출 정책. 삭제된 글은 404로 통일하고, 블라인드된 글은 수정으로 되살릴 수 없게 403.
    if (post.getStatus() == PostStatus.DELETED) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
    if (post.getStatus() == PostStatus.BLINDED) {
      throw new BusinessException(ErrorCode.POST_BLINDED);
    }

    // 소유권 확인: 내 글이 아니면 수정 거부(403). 삭제와 동일하게 서비스에서 막는다.
    if (!post.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 남길 기존 이미지를 확정한다. 요청의 경로 문자열을 그대로 쓰지 않고, 이 게시글에 실제로 붙어 있는
    // Image 인스턴스로만 되짚는다. (아래 resolveKeptImages 참고)
    List<Image> keptImages = resolveKeptImages(post, request.keepImageUrls());

    // 새 이미지를 저장소에 올린다. 형식·크기 검증과 실패 시 롤백은 storeAll이 담당한다.
    List<StoredObject> storedObjects = fileStorageService.storeAll(images, ImageCategory.POST);
    List<String> storedKeys = storedObjects.stream().map(StoredObject::objectKey).toList();

    // 객체는 이미 저장소에 올라갔지만 DB는 아직 커밋 전이다. 이 아래 어디서든 실패해 롤백되면
    // 방금 올린 객체만 고아로 남으므로, storeAll 직후 곧바로 되돌림 훅을 건다.
    // 동시에, 커밋에 성공한 경우에만 떨어져 나간 옛 객체를 지운다 — 롤백됐는데 객체를 먼저 지워버리면
    // DB에는 살아 있는 이미지의 실물이 사라져 깨진 링크가 된다.
    List<String> removedKeys = new ArrayList<>();
    registerImageCleanup(storedKeys, removedKeys);

    List<Image> newImages =
        storedObjects.stream().map(o -> Image.ofMinio(o.url(), o.objectKey())).toList();

    // 최종 개수 상한은 여기서 본다. storeAll은 이번에 올린 파일 수만 세므로,
    // 기존 3장을 남긴 채 새로 3장을 올리는 식으로 상한을 넘기는 것을 잡지 못한다.
    if (keptImages.size() + newImages.size() > minioProperties.getMaxCount()) {
      throw new BusinessException(ErrorCode.IMAGE_COUNT_EXCEEDED);
    }

    // 변경 감지(dirty checking)로 트랜잭션 커밋 시점에 UPDATE 반영
    post.update(PostCategory.from(request.category()), request.title(), request.content());

    // 남길 기존 것 + 새로 올린 것 순서로 최종 목록을 만든다. 이 순서가 곧 노출 순서다.
    List<Image> finalImages = new ArrayList<>(keptImages);
    finalImages.addAll(newImages);
    List<Image> detachedImages = post.replaceImages(finalImages);

    // 떨어져 나간 이미지는 이 게시글 전용이라 다른 곳에서 참조하지 않는다. 연결(post_images)만 끊고 두면
    // images 행이 고아로 쌓이므로 행까지 지운다.
    // 삭제 순서는 Hibernate가 보장한다 — 같은 flush 안에서 컬렉션 삭제(post_images)가 엔티티 삭제(images)보다 먼저 나간다.
    if (!detachedImages.isEmpty()) {
      imageRepository.deleteAll(detachedImages);
      // 저장소 객체 삭제는 커밋 성공 후에. 위에서 건 훅이 이 목록을 보고 지운다.
      // 외부 URL 이미지는 키가 없어 걸러진다 — 남의 이미지를 지우려 시도하지 않는다.
      detachedImages.stream()
          .filter(Image::isStoredByUs)
          .map(Image::getObjectKey)
          .forEach(removedKeys::add);
    }

    return PostDetailResponse.from(post);
  }

  // 요청의 keepImageUrls를 이 게시글에 실제로 붙어 있는 Image 인스턴스 목록으로 되짚는다.
  //   null       : 필드를 안 보낸 것 = 이미지는 건드리지 않는 수정. 기존 전부 유지.
  //   빈 목록     : 전부 삭제하겠다는 뜻. 그대로 존중한다.
  // 경로 문자열을 믿고 Image.of(url)로 새로 만들면 안 된다. 남의 게시글 이미지 경로를 keepImageUrls에 실어
  // 내 글에 붙이거나, 임의 경로를 DB에 심을 수 있다. 그래서 "이 게시글에 지금 붙어 있는 것"만 통과시키고
  // 그 밖의 값은 400으로 막는다. 중복도 막는다 — 같은 Image가 목록에 두 번 들어가면 post_images 키가 깨진다.
  private List<Image> resolveKeptImages(Post post, List<String> keepImageUrls) {
    if (keepImageUrls == null) {
      return List.copyOf(post.getImages());
    }
    if (keepImageUrls.isEmpty()) {
      return List.of();
    }

    Map<String, Image> currentByUrl =
        post.getImages().stream().collect(Collectors.toMap(Image::getImageUrl, image -> image));

    List<Image> kept = new ArrayList<>(keepImageUrls.size());
    for (String url : keepImageUrls) {
      Image image = currentByUrl.get(url);
      // 이 게시글의 이미지가 아니거나(위조·오타), 같은 것을 두 번 보냈으면 400.
      if (image == null || kept.contains(image)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
      }
      kept.add(image);
    }
    return kept;
  }

  // 수정·삭제 트랜잭션의 객체 정리 훅. 저장소는 트랜잭션에 참여하지 않으므로 커밋 결과를 보고 한쪽만 정리한다.
  //   커밋 성공 : 게시글에서 떨어져 나간 옛 객체(removedKeys)를 지운다. 새 객체는 DB가 참조하므로 남긴다.
  //   롤백/실패 : 방금 올린 새 객체(storedKeys)를 지운다. 옛 객체는 DB에 그대로 살아 있으므로 건드리지 않는다.
  // 삭제 경로처럼 새로 올리는 객체가 없으면 storedKeys는 빈 목록으로 넘어와 롤백 시 아무것도 지우지 않는다.
  // removedKeys는 호출 후에 채워지는 것을 전제로 참조를 넘긴다. 훅은 커밋 시점에야 읽으므로 그때는 다 차 있다.
  private void registerImageCleanup(List<String> storedKeys, List<String> removedKeys) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            List<String> targets = (status == STATUS_COMMITTED) ? removedKeys : storedKeys;
            for (String key : targets) {
              try {
                fileStorageService.deleteByKey(key);
              } catch (RuntimeException e) {
                // 정리 실패로 요청 자체를 실패시키지는 않는다. 객체가 남는 것보다 나쁜 게 없으므로 로그만 남긴다.
                log.warn("게시글 수정 후 이미지 삭제 실패. objectKey={}", key, e);
              }
            }
          }
        });
  }

  // 게시글 삭제: 작성자 본인만 상태를 DELETED로 바꾸고(소프트 삭제), 첨부 이미지는 완전히 지운다. (쓰기 트랜잭션)
  // userId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
  //
  // 글 자체는 소프트 삭제지만 이미지는 하드 삭제다 — 수정(update)에서 뺀 이미지를 지우는 것과 같은 기준이다.
  // 남는 파일을 방치하면 디스크만 계속 불어나고, 경로를 아는 사람은 삭제한 사진을 계속 열어볼 수 있다.
  // 되돌릴 수 없다는 점은 알고 받아들인 선택이다: 관리자 원문 열람(AdminPostService#getPostDetail)은
  // 삭제된 글의 이미지를 더 이상 볼 수 없고, 글 복구 기능이 생기더라도 이미지는 되살릴 수 없다.
  @Transactional
  public void delete(Long userId, Long postId) {
    // 삭제할 게시글 조회, 없으면 예외
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

    // 소유권 확인: 내 글이 아니면 삭제 거부(403). 권한(ROLE)과 별개로 서비스에서 막는다.
    if (!post.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 첨부 이미지를 전부 떼어낸다. 이미 삭제된 글을 다시 지우면 빈 목록이 나와 아래가 모두 no-op이 된다.
    List<Image> detachedImages = post.replaceImages(List.of());

    // 저장소 객체는 커밋 성공 후에만 지운다. 롤백됐는데 객체를 먼저 지우면 DB에 살아 있는 이미지의
    // 실물이 사라져 깨진 링크가 된다. 이 경로에서 새로 올리는 객체는 없으므로 롤백 시 지울 목록은 비어 있다.
    List<String> removedKeys = new ArrayList<>();
    registerImageCleanup(List.of(), removedKeys);

    // 연결(post_images)만 끊고 두면 images 행이 고아로 쌓이므로 행까지 지운다.
    // 삭제 순서는 Hibernate가 보장한다 — 같은 flush 안에서 컬렉션 삭제가 엔티티 삭제보다 먼저 나간다.
    if (!detachedImages.isEmpty()) {
      imageRepository.deleteAll(detachedImages);
      detachedImages.stream()
          .filter(Image::isStoredByUs)
          .map(Image::getObjectKey)
          .forEach(removedKeys::add);
    }

    // 변경 감지로 status = DELETED 로 UPDATE 반영
    post.delete();
  }

  // 게시글 신고: 로그인 회원이 대상 게시글을 신고하고 접수된 신고 정보를 반환한다. (쓰기 트랜잭션)
  // reporterId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
  @Transactional
  public PostReportResponse report(Long reporterId, Long postId, PostReportRequest request) {
    // 신고 대상 게시글 조회. 없거나 이미 삭제된 글이면 신고할 수 없다(404).
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    if (post.getStatus() == PostStatus.DELETED) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }

    // 신고자 회원을 최종 검증한다. (JWT는 통과했지만 탈퇴/삭제로 회원이 사라졌을 수 있다.)
    User reporter =
        userRepository
            .findById(reporterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // 같은 회원이 아직 처리되지 않은(PENDING) 신고를 이미 넣었다면 중복 접수를 막는다. (빠른 선검사)
    if (postReportRepository.existsByPost_PostIdAndReporter_IdAndStatus(
        postId, reporterId, ReportStatus.PENDING)) {
      throw new BusinessException(ErrorCode.ALREADY_REPORTED_POST);
    }

    // 선검사와 저장 사이의 동시 이중 신고 경쟁은 DB 유니크 제약이 최종적으로 막는다.
    // 제약 위반이 커밋 시점이 아니라 여기서 바로 드러나도록 saveAndFlush 로 즉시 flush 해
    // DataIntegrityViolationException 을 잡아 ALREADY_REPORTED_POST 로 변환한다.
    try {
      PostReport saved =
          postReportRepository.saveAndFlush(
              new PostReport(post, reporter, request.reason(), request.description()));
      return PostReportResponse.from(saved);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.ALREADY_REPORTED_POST);
    }
  }
}
