package com.basecamp.backend.domain.camp.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// CampManageStatus <-> DB 컬럼(manage_sttus, 한글 라벨) 변환.
// autoApply = true 라서 이 타입을 쓰는 필드(Camp.manageSttus)에 자동으로 붙는다.
@Converter(autoApply = true)
public class CampManageStatusConverter implements AttributeConverter<CampManageStatus, String> {

  @Override
  public String convertToDatabaseColumn(CampManageStatus attribute) {
    return attribute == null ? null : attribute.getLabel();
  }

  @Override
  public CampManageStatus convertToEntityAttribute(String dbData) {
    return dbData == null ? null : CampManageStatus.fromLabel(dbData);
  }
}
