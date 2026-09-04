package com.example.designreco.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.designreco.domain.Asset;

class RecommendationServiceTest {

	private final RecommendationService service = new RecommendationService();

	private Asset asset(long id, String category, Double... embedding) {
		return new Asset(id, category, embedding, LocalDateTime.now());
	}

	@Test
	void 자기_자신은_추천에서_제외() {
		Asset target = asset(1, "poster", 1.0, 0.0);
		List<Asset> candidates = List.of(target, asset(2, "poster", 0.9, 0.1));

		List<Asset> result = service.topSimilar(target, candidates, 5);

		assertThat(result).extracting(Asset::getId).containsExactly(2L);
	}

	@Test
	void 유사도_높은_순으로_limit개만_반환() {
		Asset target = asset(1, "poster", 1.0, 0.0);
		Asset veryClose = asset(2, "poster", 0.95, 0.05);
		Asset somewhatClose = asset(3, "poster", 0.5, 0.5);
		Asset opposite = asset(4, "card", 0.0, 1.0);

		List<Asset> result = service.topSimilar(target, List.of(opposite, somewhatClose, veryClose), 2);

		assertThat(result).extracting(Asset::getId).containsExactly(2L, 3L);
	}
}
