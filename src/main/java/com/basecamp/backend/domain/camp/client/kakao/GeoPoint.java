package com.basecamp.backend.domain.camp.client.kakao;

import java.math.BigDecimal;

/**
 * 지오코딩 결과 좌표.
 *
 * @param mapX 경도 (longitude)
 * @param mapY 위도 (latitude)
 */
public record GeoPoint(BigDecimal mapX, BigDecimal mapY) {}
