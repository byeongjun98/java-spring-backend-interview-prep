package com.example.designreco.web;

import java.util.List;
import java.util.Locale;

/**
 * Accept-Language 헤더 → 서비스가 지원하는 locale 코드 하나로 매핑.
 * 여러 엔드포인트(에셋 조회, 추천)에서 공용으로 쓰기 때문에 별도 유틸로 뺌.
 * 직접 헤더 문자열을 파싱하지 않고 java.util.Locale.LanguageRange(자바 표준 RFC 4647 구현체)를
 * 쓰는 이유: "ko-KR,ko;q=0.9,en;q=0.8" 같은 실제 브라우저 헤더의 품질값(q=)·우선순위 파싱을
 * 직접 구현하면 버그 나기 쉬운 부분이라 표준 라이브러리에 맡김.
 */
public final class AcceptLanguageResolver {

	public static final String DEFAULT_LOCALE = "en";

	private AcceptLanguageResolver() {
	}

	public static String resolve(String acceptLanguageHeader, List<String> supportedLocales) {
		if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
			return DEFAULT_LOCALE;
		}
		// "ko" 같은 문자열 코드를 Locale.LanguageRange/Locale.lookup이 다룰 수 있는 Locale 객체로 변환.
		List<Locale> supported = supportedLocales.stream().map(Locale::forLanguageTag).toList();
		try {
			// 헤더를 (locale, 우선순위) 쌍의 목록으로 파싱. 예: "ko;q=0.9,en;q=0.5" → [ko(0.9), en(0.5)].
			List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguageHeader);
			// 파싱된 우선순위 목록 중, 우리가 실제로 지원하는(supported) locale과 가장 먼저 매치되는 걸 고름.
			// 헤더가 "fr,de"처럼 지원 목록에 하나도 안 겹치면 null이 옴 → 기본 locale로 폴백.
			Locale best = Locale.lookup(ranges, supported);
			return best == null ? DEFAULT_LOCALE : best.toLanguageTag();
		} catch (IllegalArgumentException malformedHeader) {
			// 헤더 형식 자체가 깨져 있으면(RFC 문법 위반) 파싱 단계에서 예외 — 400을 던지기보다
			// "번역 없으면 en" 폴백과 동일하게 조용히 기본값으로 처리 (로케일 협상 실패는 치명적이지 않음).
			return DEFAULT_LOCALE;
		}
	}
}
