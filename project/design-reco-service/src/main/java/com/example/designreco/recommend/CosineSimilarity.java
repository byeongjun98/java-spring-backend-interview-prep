package com.example.designreco.recommend;

/**
 * 두 임베딩 벡터의 코사인 유사도. 차원 수가 다르면 계산 불가하므로 예외.
 * 코사인 유사도 = 두 벡터가 "가리키는 방향"이 얼마나 비슷한지 (-1: 정반대, 0: 무관/직교, 1: 완전히 같은 방향).
 * 유클리드 거리와 달리 벡터의 크기(길이)는 무시하고 방향만 비교 — 임베딩에서 "크기"는
 * 보통 의미가 없고(값의 스케일이 학습 방식에 따라 달라짐) 방향이 의미(카테고리/속성)를 담기 때문에
 * 추천 시스템에서 거리 대신 코사인 유사도를 쓰는 게 일반적.
 */
public final class CosineSimilarity {

	// 인스턴스를 만들 이유가 없는 순수 정적 유틸 클래스라 private 생성자로 인스턴스화를 막음.
	private CosineSimilarity() {
	}

	public static double of(Double[] a, Double[] b) {
		if (a.length != b.length) {
			throw new IllegalArgumentException("embedding dimension mismatch: %d vs %d".formatted(a.length, b.length));
		}
		// 코사인 유사도 공식: dot(a,b) / (|a| * |b|)
		// dot = 두 벡터의 내적(같은 위치 성분끼리 곱해서 합산), |a|/|b| = 각 벡터의 길이(노름).
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i]; // 벡터 길이 계산의 중간값(제곱합) — 아래서 sqrt로 실제 길이로 변환.
			normB += b[i] * b[i];
		}
		if (normA == 0 || normB == 0) {
			// 영벡터는 "방향"이 정의되지 않아 0으로 나누기가 됨 — 관례상 유사도 0(무관)으로 처리.
			return 0;
		}
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}
}
