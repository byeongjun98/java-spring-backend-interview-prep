package com.example.designreco.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class EmbeddingMathTest {

	@Test
	void 벡터_여러개의_평균을_차원별로_계산() {
		Double[] a = { 1.0, 3.0 };
		Double[] b = { 3.0, 5.0 };

		Double[] centroid = EmbeddingMath.centroid(List.of(a, b));

		assertThat(centroid).containsExactly(2.0, 4.0);
	}

	@Test
	void 벡터_하나면_그대로_반환() {
		Double[] a = { 1.0, 2.0 };
		assertThat(EmbeddingMath.centroid(List.<Double[]>of(a))).containsExactly(1.0, 2.0);
	}

	@Test
	void 빈_목록이면_예외() {
		assertThatThrownBy(() -> EmbeddingMath.centroid(List.of())).isInstanceOf(IllegalArgumentException.class);
	}
}
