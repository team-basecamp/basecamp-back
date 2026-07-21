package com.basecamp.backend.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basecamp.backend.domain.user.entity.Image;

public interface ImageRepository extends JpaRepository<Image, Long> {
}
