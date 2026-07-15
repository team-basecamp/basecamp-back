package com.basecamp.backend.domain.camp.entity;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 캠핑장 운영 상태. DB 컬럼(manage_sttus)과 고캠핑 API 응답 값은 한글 라벨 그대로 유지한다 — CampManageStatusConverter 참고.
@Getter
public enum CampManageStatus {
    OPERATING("운영"),
    SUSPENDED("휴장"),
    CLOSED("폐장");

    private static final Logger log = LoggerFactory.getLogger(CampManageStatus.class);

    private final String label;

    CampManageStatus(String label) {
        this.label = label;
    }

    // DB/외부 API 어디서 오든 값을 100% 신뢰하지 않는다. 매칭 안 되면 예외 대신 기본값으로 처리해서
    // 로우 하나의 이상값 때문에 목록/검색 같은 읽기 API 전체가 죽는 걸 막는다.
    // TODO: 알 수 없는 상태값을 OPERATING으로 기본 처리하는 게 맞는지 팀 논의 필요합니다.
    // (CodeRabbit 리뷰 지적: 미확인 값이 "운영중"으로 노출될 수 있음 — UNKNOWN 상태 분리 여부 등을 논의 후 결정)
    public static CampManageStatus fromLabel(String label) {
        return switch (label) {
            case "운영" -> OPERATING;
            case "휴장" -> SUSPENDED;
            case "폐장" -> CLOSED;
            case null, default -> {
                log.warn("알 수 없는 운영 상태 값 '{}' - 기본값(OPERATING)으로 처리합니다", label);
                yield OPERATING;
            }
        };
    }
}
