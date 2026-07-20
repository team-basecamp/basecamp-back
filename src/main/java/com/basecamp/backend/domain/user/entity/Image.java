package com.basecamp.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 공용 이미지 저장소. {@code images} 테이블과 매핑된다.
 *
 * <p>V3에서 {@code user_images} 를 일반화한 테이블로, 프로필/게시글/리뷰/캠핑장 이미지를 모두 담는다.
 *
 * <p>한 테이블에 성격이 다른 두 종류가 섞여 있다는 점이 중요하다. {@link ImageStorageType#EXTERNAL} 은 소셜 로그인이 준 남의 URL 이라 지울
 * 수 없고, {@link ImageStorageType#MINIO} 는 우리가 올린 것이라 참조가 끊기면 저장소 객체까지 지워야 한다. 이 구분이 없으면 외부 URL 을 지우려
 * 시도하거나, 반대로 우리 객체를 저장소에 고아로 남기게 된다.
 *
 * <p>{@link #imageUrl} 에는 조립이 필요 없는 완성된 절대 URL 이 들어간다. 덕분에 응답 DTO 는 저장 방식을 몰라도 값을 그대로 내보낼 수 있다.
 */
@Getter
@Entity
@Table(name = "images")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "image_id")
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "storage_type", nullable = false, length = 20)
  private ImageStorageType storageType;

  /**
   * 저장소 안에서의 객체 키(예: {@code posts/ab12.jpg}). {@link ImageStorageType#EXTERNAL} 이면 {@code null}.
   */
  @Column(name = "object_key")
  private String objectKey;

  @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
  private String imageUrl;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  private Image(ImageStorageType storageType, String imageUrl, String objectKey) {
    this.storageType = storageType;
    this.imageUrl = imageUrl;
    this.objectKey = objectKey;
  }

  /** 외부 URL 을 그대로 참조한다. 소셜 로그인이 준 프로필 이미지에 쓴다. */
  public static Image ofExternal(String imageUrl) {
    return new Image(ImageStorageType.EXTERNAL, imageUrl, null);
  }

  /** 저장소에 올린 이미지. 삭제할 때 필요하므로 객체 키를 함께 남긴다. */
  public static Image ofMinio(String imageUrl, String objectKey) {
    return new Image(ImageStorageType.MINIO, imageUrl, objectKey);
  }

  /**
   * 기존 행을 외부 URL 로 바꿔 재사용한다. 회원당 이미지가 1:1 이라 새 행을 만들면 옛 행이 고아로 남기 때문이다.
   *
   * <p>저장소에 있던 이미지에서 외부 URL 로 바뀌는 경우 객체 키를 지워야 한다. 남겨두면 EXTERNAL 인데 키가 있는 모순된 행이 된다. 저장소 객체 자체의 삭제는
   * 호출자가 이전 키를 들고 처리한다.
   */
  public void updateExternal(String imageUrl) {
    this.storageType = ImageStorageType.EXTERNAL;
    this.imageUrl = imageUrl;
    this.objectKey = null;
  }

  /** 기존 행을 저장소 이미지로 바꿔 재사용한다. 이전 저장소 객체의 삭제는 호출자가 이전 키를 들고 처리한다. */
  public void updateMinio(String imageUrl, String objectKey) {
    this.storageType = ImageStorageType.MINIO;
    this.imageUrl = imageUrl;
    this.objectKey = objectKey;
  }

  /** 저장소에 실물이 있어 참조가 끊길 때 함께 지워야 하는 이미지인지. */
  public boolean isStoredByUs() {
    return storageType == ImageStorageType.MINIO;
  }
}
