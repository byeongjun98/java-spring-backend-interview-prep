package com.example.designreco.recommend;

import java.util.List;

/**
 * 여러 임베딩 벡터의 중심점(centroid) — 유저의 "최근 관심사"를 하나의 벡터로 뭉뚱그릴 때 씀.
 * 유저가 최근에 본 에셋 5개의 임베딩을 각각 추천 기준으로 쓰면 계산이 5배로 늘고 결과도
 * 5개로 흩어지니까, "이 유저가 대체로 어떤 방향을 좋아하는지"를 평균 벡터 하나로 요약해서
 * 그 벡터 기준으로 유사도 검색을 함 (UserController.recommendationsForUser 참고).
 */
public final class EmbeddingMath {

	private EmbeddingMath() {
	}

	public static Double[] centroid(List<Double[]> vectors) {
		if (vectors.isEmpty()) {
			throw new IllegalArgumentException("cannot compute centroid of empty vector list");
		}
		// 모든 벡터가 같은 차원(8차원)이라고 가정 — 같은 스키마(asset.embedding)에서 나온
		// 값들이라 실제로 항상 참이지만, 방어 코드까진 필요 없다고 판단해서 검증 생략.
		int dimension = vectors.get(0).length;
		double[] sum = new double[dimension];
		for (Double[] vector : vectors) {
			for (int i = 0; i < dimension; i++) {
				sum[i] += vector[i]; // 차원(인덱스)별로 값을 누적
			}
		}
		Double[] result = new Double[dimension];
		for (int i = 0; i < dimension; i++) {
			result[i] = sum[i] / vectors.size(); // 차원별 평균 = centroid
		}
		return result;
	}
}
