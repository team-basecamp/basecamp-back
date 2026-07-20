package com.basecamp.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * <p>V3에서 {@code user_images} 를 일반화한 테이블로, 프로필/게시글/리뷰 이미지를 모두 담는다. 현재는 회원 프로필 이미지({@link
 * User#getProfileImage()})만 사용한다. (게시글/리뷰가 붙을 때 공용 위치로 이동 검토)
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

  @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
  private String imageUrl;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  private Image(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public static Image of(String imageUrl) {
    return new Image(imageUrl);
  }

  public void updateUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }
}
