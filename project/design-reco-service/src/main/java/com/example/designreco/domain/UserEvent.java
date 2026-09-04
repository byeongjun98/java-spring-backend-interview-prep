package com.example.designreco.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 에셋을 보거나(VIEW) 실제로 캔버스에 넣어 쓴(USE) 이력. 추천 신호로 사용.
 * member 테이블이 따로 없음 — 이 서비스는 회원 도메인을 소유하지 않는 별도 마이크로서비스라는
 * 가정이라, userId는 그냥 "다른 서비스가 발급한 회원 id"를 받아 저장만 하는 opaque 값.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("user_event")
public class UserEvent {

	@Id
	private Long id;

	private Long userId;

	private Long assetId;

	// eventType은 EventType enum이 아니라 String으로 저장.
	// R2DBC 기본 컨버터로 enum ↔ varchar 매핑을 하려면 별도 설정이 필요해서,
	// "API 입력 검증은 EventType enum으로, DB 저장은 String으로" 역할을 나눔
	// (검증 로직은 UserController.recordEvent 참고).
	private String eventType;

	private LocalDateTime createdAt;
}
