package com.example.designreco.controller;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.designreco.domain.Asset;
import com.example.designreco.dto.AssetResponse;
import com.example.designreco.recommend.RecommendationService;
import com.example.designreco.repository.AssetRepository;
import com.example.designreco.web.AcceptLanguageResolver;
import com.example.designreco.web.AssetResponseAssembler;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// @RestController는 반환값(Mono/Flux)을 Jackson이 알아서 JSON으로 직렬화 + WebFlux가
// 구독(subscribe)해서 실제로 HTTP 응답을 흘려보냄 — 컨트롤러 메서드 안에서 .block()이나
// .subscribe()를 직접 호출하지 않는 게 WebFlux의 기본 규칙 (블로킹하는 순간 논블로킹 장점이 사라짐).
@RestController
@RequestMapping("/assets")
public class AssetController {

	// ponytail: locale 목록 하드코딩. locale이 DB에서 동적으로 늘어나는 시점에 테이블화.
	private static final List<String> SUPPORTED_LOCALES = List.of("ko", "en", "ja");
	private static final int DEFAULT_RECOMMENDATION_LIMIT = 3;
	// 글로벌 트래픽 대응 흉내: item-based 추천은 요청마다 재계산할 필요 없어서 짧게 캐싱.
	private static final Duration RECOMMENDATION_CACHE_TTL = Duration.ofMinutes(5);

	private final AssetRepository assetRepository;
	private final RecommendationService recommendationService;
	private final AssetResponseAssembler responseAssembler;
	// Spring Boot가 spring-boot-starter-data-redis-reactive 의존성만 보고 자동으로 만들어주는 빈.
	// 별도 @Configuration 없이 생성자에 타입만 선언하면 주입됨 (localhost:6379 기본값,
	// application.properties의 spring.data.redis.host/port로 재정의 가능).
	private final ReactiveStringRedisTemplate redisTemplate;

	public AssetController(AssetRepository assetRepository, RecommendationService recommendationService,
			AssetResponseAssembler responseAssembler, ReactiveStringRedisTemplate redisTemplate) {
		this.assetRepository = assetRepository;
		this.recommendationService = recommendationService;
		this.responseAssembler = responseAssembler;
		this.redisTemplate = redisTemplate;
	}

	@GetMapping
	public Flux<AssetResponse> list(@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
		String locale = AcceptLanguageResolver.resolve(acceptLanguage, SUPPORTED_LOCALES);
		// findAll()이 Flux<Asset>(0개 이상의 스트림)을 반환 → 각 Asset을 flatMap으로
		// "번역까지 조회해서 AssetResponse로 바꾸는 비동기 작업"에 흘려보냄.
		// flatMap은 각 Asset에 대한 내부 작업(번역 조회)을 동시에 시작하고 완료되는 대로 결과를
		// 내보냄 — 여기선 최종 순서가 중요하지 않은 "목록 전체 보여주기"라 상관없음.
		return assetRepository.findAll().flatMap(asset -> responseAssembler.toResponse(asset, locale));
	}

	@GetMapping("/{id}")
	public Mono<AssetResponse> get(@PathVariable Long id,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
		String locale = AcceptLanguageResolver.resolve(acceptLanguage, SUPPORTED_LOCALES);
		return assetRepository.findById(id).flatMap(asset -> responseAssembler.toResponse(asset, locale));
	}

	@GetMapping("/{id}/recommendations")
	public Flux<AssetResponse> recommendations(@PathVariable Long id,
			@RequestParam(defaultValue = "" + DEFAULT_RECOMMENDATION_LIMIT) int limit,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
		String locale = AcceptLanguageResolver.resolve(acceptLanguage, SUPPORTED_LOCALES);
		// 캐시 키에 limit까지 넣는 이유: ?limit=2와 ?limit=5는 다른 결과라 같은 키를 쓰면 안 됨.
		String cacheKey = "reco:asset:%d:%d".formatted(id, limit);

		// 1) Redis에서 캐시된 id 목록을 먼저 찾아본다 (있으면 "2,5" 같은 콤마 구분 문자열).
		//    2) 캐시가 비어 있으면(Mono가 빈 채로 끝나면) switchIfEmpty가 계산 경로로 대체.
		Mono<List<Long>> recommendedIds = redisTemplate.opsForValue()
			.get(cacheKey)
			.map(cached -> Arrays.stream(cached.split(",")).map(Long::parseLong).toList())
			.switchIfEmpty(computeAndCacheRecommendations(id, limit, cacheKey));

		// flatMap은 완료 순서대로 결과를 흘려보내 유사도 순위가 뒤섞임 — flatMapSequential로 입력 순서 보존.
		// (recommendedIds에 담긴 id 목록은 이미 유사도 내림차순으로 정렬돼 있으므로, 이후 단계에서도
		// 그 순서가 그대로 유지돼야 클라이언트가 받는 추천 목록의 순위가 맞음.)
		return recommendedIds.flatMapMany(Flux::fromIterable)
			.flatMapSequential(assetRepository::findById)
			.flatMapSequential(asset -> responseAssembler.toResponse(asset, locale));
	}

	// 캐시 미스일 때만 실제로 실행되는 계산 경로.
	// target/allAssets는 여기서 "정의"만 될 뿐 이 메서드가 호출될 때 바로 DB를 때리지 않음 —
	// Mono/Flux는 구독(subscribe)돼야 실행되는 콜드 퍼블리셔라, switchIfEmpty가 이 Mono를
	// 실제로 구독하는 시점(=캐시가 비어 있을 때)에만 findById/findAll 쿼리가 나감.
	private Mono<List<Long>> computeAndCacheRecommendations(Long id, int limit, String cacheKey) {
		Mono<Asset> target = assetRepository.findById(id);
		Mono<List<Asset>> allAssets = assetRepository.findAll().collectList();
		// zipWith: 두 Mono가 각자 완료될 때까지 기다렸다가 결과를 (target, allAssets) 쌍으로 합침.
		// 서로 의존관계가 없는 두 조회라 순차로 기다리지 않고 동시에 실행됨.
		return target.zipWith(allAssets)
			.map(tuple -> recommendationService.topSimilar(tuple.getT1(), tuple.getT2(), limit))
			.map(assets -> assets.stream().map(Asset::getId).toList())
			.flatMap(ids -> redisTemplate.opsForValue()
				// set(key, value, ttl): 계산 결과를 Redis에 저장하면서 TTL도 같이 지정 —
				// TTL이 지나면 Redis가 알아서 키를 지워서 "카탈로그가 바뀌어도 언젠가는 최신화"됨
				// (변경 즉시 캐시 무효화까지는 이 토이 프로젝트 범위 밖).
				.set(cacheKey, ids.stream().map(String::valueOf).collect(Collectors.joining(",")),
						RECOMMENDATION_CACHE_TTL)
				// Redis SET 자체의 반환값(Boolean)은 필요 없고, 그다음 단계로는 id 목록을 넘겨야
				// 하므로 thenReturn으로 "저장 끝나면 ids를 흘려보내라"고 지정.
				.thenReturn(ids));
	}
}
