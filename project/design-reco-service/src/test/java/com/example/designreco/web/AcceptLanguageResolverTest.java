package com.example.designreco.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AcceptLanguageResolverTest {

	private static final List<String> SUPPORTED = List.of("ko", "en", "ja");

	@Test
	void 헤더가_없으면_기본_locale() {
		assertThat(AcceptLanguageResolver.resolve(null, SUPPORTED)).isEqualTo("en");
		assertThat(AcceptLanguageResolver.resolve("  ", SUPPORTED)).isEqualTo("en");
	}

	@Test
	void 지원하는_locale_중_우선순위가_가장_높은_것을_선택() {
		assertThat(AcceptLanguageResolver.resolve("fr-FR,ko;q=0.8,en;q=0.5", SUPPORTED)).isEqualTo("ko");
	}

	@Test
	void 지원하지_않는_locale만_있으면_기본_locale로_폴백() {
		assertThat(AcceptLanguageResolver.resolve("fr-FR,de-DE;q=0.8", SUPPORTED)).isEqualTo("en");
	}

	@Test
	void 잘못된_헤더_형식이면_기본_locale로_폴백() {
		assertThat(AcceptLanguageResolver.resolve("not a valid header !!", SUPPORTED)).isEqualTo("en");
	}
}
