package com.example.designreco.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CosineSimilarityTest {

	@Test
	void 같은_방향_벡터는_유사도_1() {
		Double[] a = { 1.0, 2.0, 3.0 };
		Double[] b = { 2.0, 4.0, 6.0 };
		assertThat(CosineSimilarity.of(a, b)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	void 직교_벡터는_유사도_0() {
		Double[] a = { 1.0, 0.0 };
		Double[] b = { 0.0, 1.0 };
		assertThat(CosineSimilarity.of(a, b)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	void 영벡터가_있으면_0으로_처리() {
		Double[] a = { 0.0, 0.0 };
		Double[] b = { 1.0, 1.0 };
		assertThat(CosineSimilarity.of(a, b)).isEqualTo(0.0);
	}

	@Test
	void 차원이_다르면_예외() {
		Double[] a = { 1.0, 2.0 };
		Double[] b = { 1.0, 2.0, 3.0 };
		assertThatThrownBy(() -> CosineSimilarity.of(a, b)).isInstanceOf(IllegalArgumentException.class);
	}
}
