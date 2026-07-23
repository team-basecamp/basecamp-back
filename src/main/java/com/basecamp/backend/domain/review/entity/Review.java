package com.basecamp.backend.domain.review.entity;

import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.user.entity.Image;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

// 리뷰 엔티티. reviews 테이블과 매핑되며, 예약 체크아웃 이후 작성된 캠핑장 리뷰 한 건을 표현한다.
// 작성자는 별도 컬럼 없이 연결된 예약(reservation)의 회원으로 식별한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reviews")
public class Review {

  // 리뷰 PK (AUTO_INCREMENT)
  @Id
  @Column(name = "review_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long reviewId;

  // --- 연관 관계 매핑 ---

  // 리뷰가 연결된 예약 (reviews.reservation_id FK → reservations, UNIQUE).
  // 체크아웃 검증 및 작성자 식별에 쓰며, 예약당 리뷰는 최대 한 건이다.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservation_id", nullable = false, unique = true)
  private Reservation reservation;

  // 리뷰 대상 캠핑장 (reviews.camp_id FK → camps).
  // 예약을 거치지 않고 캠핑장별 리뷰/평점 집계를 빠르게 하기 위해 직접 연결한다.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "camp_id", nullable = false)
  private Camp camp;

  // 리뷰 첨부 이미지 (1 리뷰 : N 이미지). V3의 공용 저장소(images)와 중간 테이블(review_images)로 연결한다.
  @ManyToMany(cascade = CascadeType.PERSIST)
  @JoinTable(
      name = "review_images",
      joinColumns = @JoinColumn(name = "review_id"),
      inverseJoinColumns = @JoinColumn(name = "image_id"))
  @OrderColumn(name = "sort_order")
  @BatchSize(size = 100)
  private List<Image> images = new ArrayList<>();

  // --- 기본 예약 필드 ---

  // 평점 (1.0 ~ 5.0). DECIMAL(3,1)과 매핑되도록 BigDecimal로 둔다.
  @Column(name = "rating", nullable = false, precision = 3, scale = 1)
  private BigDecimal rating;

  // 리뷰 본문 (TEXT)
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  // 작성 일시
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  // 수정 일시 (수정 전에는 null)
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // 리뷰 생성자. 대상 예약·캠핑장·평점·본문을 받고 작성 시각은 여기서 채운다.
  @Builder
  private Review(Reservation reservation, Camp camp, int rating, String content) {
    this.reservation = reservation;
    this.camp = camp;
    this.rating = BigDecimal.valueOf(rating); // 클라이언트에서 정수로 받음
    this.content = content;
    // DB DEFAULT가 있어도 JPA가 NULL로 밀어넣으면 적용되지 않아 자바단에서 초기값을 채운다.
    this.createdAt = LocalDateTime.now();
  }

  // 리뷰 수정. 평점·본문을 갈아끼우고 수정 시각을 현재로 채운다.
  // 관리 상태(영속 컨텍스트) 엔티티에서 호출하면 트랜잭션 커밋 시 변경 감지로 UPDATE가 나간다.
  public void update(int rating, String content) {
    this.rating = BigDecimal.valueOf(rating);
    this.content = content;
    this.updatedAt = LocalDateTime.now();
  }

  // 리뷰에 이미지를 첨부한다. 저장소에 올린 뒤 만든 Image들을 순서대로 붙인다(전달 순서가 곧 노출 순서).
  public void attachImages(List<Image> images) {
    if (images == null || images.isEmpty()) {
      return;
    }
    this.images.addAll(images);
  }

  // 비교는 Image 인스턴스 동일성 기준. 남기는 이미지는 이미 이 컬렉션에 로딩된 바로 그 객체를 다시 넘겨야 한다.
  public List<Image> replaceImages(List<Image> newImages) {
    List<Image> detached = new ArrayList<>(this.images);
    detached.removeAll(newImages);

    this.images.clear();
    this.images.addAll(newImages);
    this.updatedAt = LocalDateTime.now();

    return detached;
  }
}
