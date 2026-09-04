package com.example.designreco.dto;

// API 응답 전용 DTO. Asset 엔티티를 그대로 반환하지 않는 이유:
// - embedding(임베딩 벡터)은 내부 계산용이라 클라이언트에 노출할 필요 없음.
// - locale/title/description은 Asset과 AssetTranslation 두 테이블을 합쳐야 나오는 값이라
//   엔티티 하나에는 애초에 존재하지 않음.
public record AssetResponse(Long id, String category, String locale, String title, String description) {
}
