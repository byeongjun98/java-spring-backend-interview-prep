package com.example.designreco.domain;

/**
 * user_event.event_type 컬럼에 들어갈 수 있는 값의 화이트리스트.
 * DB 컬럼은 varchar라 자유 문자열이 들어갈 수 있으므로, API 입력 단계에서 이 enum으로 검증해서 막음.
 * (신뢰 경계 = 외부에서 들어오는 HTTP 요청. DB 제약이 아니라 여기서 막아야
 * "eventType": "asdf" 같은 값이 애초에 저장되지 않음.)
 */
public enum EventType {
	VIEW, USE
}
