package com.example.designreco.recommend;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.designreco.domain.Asset;

/**
 * 순수 동기 로직으로 분리 — reactive 파이프라인(리포지토리 I/O)과 추천 알고리즘을 섞으면
 * 테스트할 때마다 R2DBC/DB를 목킹해야 해서, 여기선 "이미 메모리에 올라온 후보 목록 중 Top-N"만 책임짐.
 * DB I/O(누가 후보고, 결과를 어떻게 캐싱하는지)는 컨트롤러가 맡고, 이 클래스는 순수 계산만 함
 * — 그래서 RecommendationServiceTest가 스프링 컨텍스트/DB 없이 순식간에 돈다.
 * 카탈로그가 커지면 이 부분을 DB 쿼리(pgvector ORDER BY embedding <=> :target LIMIT n)로 이전.
 */
@Service
public class RecommendationService {

	/** item-based: 특정 에셋과 비슷한 에셋. 자기 자신은 제외 대상에 넣어서 topSimilarToVector에 위임. */
	public List<Asset> topSimilar(Asset target, List<Asset> candidates, int limit) {
		return topSimilarToVector(target.getEmbedding(), candidates, Set.of(target.getId()), limit);
	}

	/**
	 * user-based: 유저의 최근 관심사 centroid와 비슷한 에셋 (이미 본 에셋은 제외).
	 * item-based(topSimilar)와 user-based(UserController에서 centroid 넘겨 호출) 둘 다
	 * "기준 벡터 하나 vs 후보 목록"이라는 같은 모양이라 이 메서드 하나로 합쳐둠 — target이
	 * 특정 에셋의 임베딩이냐 여러 에셋의 centroid냐 차이만 있을 뿐 정렬 로직은 동일.
	 */
	public List<Asset> topSimilarToVector(Double[] targetEmbedding, List<Asset> candidates, Set<Long> excludeIds,
			int limit) {
		return candidates.stream()
			.filter(candidate -> !excludeIds.contains(candidate.getId()))
			// Comparator.comparingDouble(...)  : 각 후보를 "기준 벡터와의 코사인 유사도" 값으로 변환해 비교.
			// .reversed()                      : 기본은 오름차순(유사도 낮은 게 먼저)이라 뒤집어서 내림차순(높은 게 먼저).
			.sorted(Comparator.comparingDouble(
					(Asset candidate) -> CosineSimilarity.of(targetEmbedding, candidate.getEmbedding()))
				.reversed())
			.limit(limit) // 정렬 후 앞에서 N개만 — 이게 "Top-N 추천".
			.toList();
	}
}
