package com.example.designreco.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.designreco.domain.Asset;
import com.example.designreco.domain.EventType;
import com.example.designreco.domain.UserEvent;
import com.example.designreco.dto.AssetResponse;
import com.example.designreco.dto.EventRequest;
import com.example.designreco.recommend.EmbeddingMath;
import com.example.designreco.recommend.RecommendationService;
import com.example.designreco.repository.AssetRepository;
import com.example.designreco.repository.UserEventRepository;
import com.example.designreco.web.AcceptLanguageResolver;
import com.example.designreco.web.AssetResponseAssembler;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 사용자 행동 이벤트 수집 + 그 이력을 반영한 user-based 추천.
 * AssetController와 별도 클래스로 뺀 이유: 여기서 다루는 리소스가 "/events", "/users/{id}/..."로
 * asset이 아니라 user·event 중심이라 URL 네임스페이스가 다름 — 다만 응답 조립(AssetResponseAssembler)과
 * 추천 계산(RecommendationService)은 그대로 재사용.
 */
@RestController
public class UserController {

	private static final List<String> SUPPORTED_LOCALES = List.of("ko", "en", "ja");
	private static final int DEFAULT_RECOMMENDATION_LIMIT = 3;

	private final UserEventRepository userEventRepository;
	private final AssetRepository assetRepository;
	private final RecommendationService recommendationService;
	private final AssetResponseAssembler responseAssembler;

	public UserController(UserEventRepository userEventRepository, AssetRepository assetRepository,
			RecommendationService recommendationService, AssetResponseAssembler responseAssembler) {
		this.userEventRepository = userEventRepository;
		this.assetRepository = assetRepository;
		this.recommendationService = recommendationService;
		this.responseAssembler = responseAssembler;
	}

	@PostMapping("/events")
	public Mono<ResponseEntity<Void>> recordEvent(@RequestBody EventRequest request) {
		// 신뢰 경계(외부 HTTP 요청)에서의 최소 검증 — null이면 DB insert 시점에 NOT NULL 제약으로
		// 터지긴 하지만, 그때는 에러 메시지가 "constraint violation" 같은 DB 용어라 원인 파악이
		// 어려움. 여기서 먼저 걸러서 "무엇이 왜 잘못됐는지" 사람이 읽을 수 있는 메시지로 400 반환.
		if (request.userId() == null || request.assetId() == null) {
			// 컨트롤러 메서드 안에서 직접 throw하지 않고 Mono.error()로 감싸는 이유: 반환 타입이
			// Mono<...>인 reactive 파이프라인에서는 예외도 "에러 시그널을 담은 Mono"로 표현해야
			// WebFlux가 이후 단계(GlobalExceptionHandler)에서 정상적으로 잡아냄.
			return Mono.error(new IllegalArgumentException("userId and assetId are required"));
		}
		EventType eventType;
		try {
			// EventType.valueOf("VIEW") → EventType.VIEW. 목록에 없는 문자열이면 IllegalArgumentException,
			// request.eventType()이 null이면 NullPointerException — 둘 다 "잘못된 입력"이라 같이 잡음.
			eventType = EventType.valueOf(request.eventType());
		} catch (IllegalArgumentException | NullPointerException invalidType) {
			return Mono.error(new IllegalArgumentException(
					"eventType must be one of " + java.util.Arrays.toString(EventType.values())));
		}
		// id는 null로 넘겨서 DB가 채우게 함(IDENTITY 컬럼), eventType은 검증된 enum의 이름을 저장.
		UserEvent event = new UserEvent(null, request.userId(), request.assetId(), eventType.name(),
				LocalDateTime.now());
		// save()가 끝나면(저장된 엔티티는 필요 없고) 201 Created 응답만 반환.
		// thenReturn: 앞 Mono의 결과값은 버리고 정해진 값으로 교체.
		return userEventRepository.save(event).thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
	}

	/**
	 * user-based 추천: 이 유저의 최근 조회/사용 에셋들의 임베딩 centroid와 가장 비슷한, 아직 안 본 에셋들.
	 * 이력이 없으면 빈 목록 — "콜드 스타트" 문제는 이 토이 프로젝트 범위 밖.
	 */
	@GetMapping("/users/{userId}/recommendations")
	public Flux<AssetResponse> recommendationsForUser(@PathVariable Long userId,
			@RequestParam(defaultValue = "" + DEFAULT_RECOMMENDATION_LIMIT) int limit,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
		String locale = AcceptLanguageResolver.resolve(acceptLanguage, SUPPORTED_LOCALES);

		// 최근 이벤트 최대 10건 → 어떤 asset을 봤는지 id만 추출 → 같은 asset을 여러 번 봤을 수
		// 있으니 distinct()로 중복 제거 → 리스트로 모음(collectList: Flux<Long> → Mono<List<Long>>,
		// centroid 계산은 "전체가 다 모인 뒤"에만 가능해서 스트림인 채로는 넘길 수 없음).
		Mono<List<Long>> recentAssetIds = userEventRepository.findFirst10ByUserIdOrderByCreatedAtDesc(userId)
			.map(UserEvent::getAssetId)
			.distinct()
			.collectList();

		// flatMapMany: Mono<List<Long>> 하나를 받아서 그 안의 로직에 따라 Flux(0개 이상)를
		// 만들어 반환. 이 유저가 겪은 asset이 없으면(콜드 스타트) 빈 Flux로 바로 끝냄.
		return recentAssetIds.flatMapMany(assetIds -> {
			if (assetIds.isEmpty()) {
				return Flux.empty();
			}
			Mono<List<Asset>> recentAssets = assetRepository.findAllById(assetIds).collectList();
			Mono<List<Asset>> allAssets = assetRepository.findAll().collectList();
			// 최근 본 asset들의 실제 임베딩(recentAssets)과 전체 카탈로그(allAssets)가 둘 다
			// 필요해서 zipWith로 병렬 조회 후 합침 — AssetController의 캐시 계산 경로와 같은 패턴.
			return recentAssets.zipWith(allAssets).flatMapMany(tuple -> {
				// 최근 관심사 여러 개(예: poster1 + card3)를 벡터 하나로 뭉쳐서(centroid),
				// "이 유저는 대체로 이런 방향의 에셋을 좋아한다"는 기준점으로 사용.
				Double[] centroid = EmbeddingMath
					.centroid(tuple.getT1().stream().map(Asset::getEmbedding).collect(Collectors.toList()));
				// excludeIds에 이미 본 assetIds를 넣어서 "본 적 있는 걸 또 추천"하지 않게 함
				// (item-based인 topSimilar와 달리 자기 자신 하나가 아니라 여러 개를 제외해야 해서
				// Set<Long>을 받는 topSimilarToVector 오버로드를 직접 호출).
				List<Asset> recommended = recommendationService.topSimilarToVector(centroid, tuple.getT2(),
						Set.copyOf(assetIds), limit);
				return Flux.fromIterable(recommended);
			});
			// flatMap은 완료 순서대로 흘려보내 유사도 순위가 뒤섞임 — flatMapSequential로 입력 순서 보존.
		}).flatMapSequential(asset -> responseAssembler.toResponse(asset, locale));
	}
}
