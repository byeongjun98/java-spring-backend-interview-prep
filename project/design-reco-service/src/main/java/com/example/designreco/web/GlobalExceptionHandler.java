package com.example.designreco.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 @RestController에 공통 적용되는 예외 처리기.
 * 지금은 UserController.recordEvent에서 던지는 IllegalArgumentException(잘못된 eventType,
 * 누락된 userId/assetId) 하나만 처리 — 컨트롤러마다 try-catch를 반복하지 않고 여기 한 곳에서
 * "이 예외 타입이면 이 HTTP 상태코드"라는 규칙을 관리.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
	}
}
