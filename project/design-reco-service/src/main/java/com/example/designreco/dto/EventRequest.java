package com.example.designreco.dto;

/** eventType은 문자열로 받아서 컨트롤러에서 EventType enum으로 검증 — 잘못된 값이면 400. */
public record EventRequest(Long userId, Long assetId, String eventType) {
}
