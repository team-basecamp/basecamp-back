package com.basecamp.backend.domain.camp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "camp_wishlists")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampWishlist {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wishlist_id")
  private Long wishlistId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "camp_id", nullable = false)
  private Camp camp;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private CampWishlist(Long userId, Camp camp) {
    this.userId = userId;
    this.camp = camp;
  }
}
