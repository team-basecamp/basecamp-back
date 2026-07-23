package com.basecamp.backend.common.storage;

/**
 * 저장소에 올라간 객체 하나.
 *
 * @param url DB와 응답에 그대로 쓰는 공개 URL (예: {@code http://localhost:9000/basecamp/posts/ab12….jpg})
 * @param objectKey 버킷 안에서의 키 (예: {@code posts/ab12….jpg}). 삭제할 때 쓴다.
 */
public record StoredObject(String url, String objectKey) {}
