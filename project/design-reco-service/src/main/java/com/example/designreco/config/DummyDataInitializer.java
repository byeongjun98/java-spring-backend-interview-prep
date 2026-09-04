package com.example.designreco.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.designreco.domain.Asset;
import com.example.designreco.domain.AssetTranslation;
import com.example.designreco.repository.AssetRepository;
import com.example.designreco.repository.AssetTranslationRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 로컬 실습용 더미 에셋 데이터.
 * 같은 카테고리끼리 임베딩을 비슷하게 잡아둬서, 코사인 유사도 실습에서
 * "포스터끼리 서로 추천됨"이 바로 눈에 보이게 함.
 * CommandLineRunner를 구현하면 스프링 부트가 애플리케이션 기동 완료 직후 run()을 자동 호출함
 * — 별도로 어디서 호출해줄 필요 없이 컴포넌트 스캔에 잡히기만 하면 됨.
 */
@Component
public class DummyDataInitializer implements CommandLineRunner {

	private final AssetRepository assetRepository;
	private final AssetTranslationRepository translationRepository;

	// 필드 주입(@Autowired private ...)이 아니라 생성자 주입을 쓰는 이유: 테스트에서 목(mock)을
	// 직접 넣어 생성하기 쉽고, final로 선언 가능해서 "생성 이후 재할당 없음"이 컴파일 타임에 보장됨.
	public DummyDataInitializer(AssetRepository assetRepository, AssetTranslationRepository translationRepository) {
		this.assetRepository = assetRepository;
		this.translationRepository = translationRepository;
	}

	// 이 클래스 안에서만 쓰는 임시 데이터 구조라 톱레벨 클래스로 안 빼고 private record로 선언.
	private record Seed(String category, Double[] embedding, String titleKo, String titleEn, String titleJa) {
	}

	private static final List<Seed> SEEDS = List.of(
			new Seed("poster", vec(0.9, 0.1, 0.1, 0.8, 0.0, 0.2, 0.1, 0.9), "가을 세일 포스터", "Autumn Sale Poster",
					"秋のセールポスター"),
			new Seed("poster", vec(0.85, 0.15, 0.05, 0.75, 0.05, 0.15, 0.1, 0.95), "겨울 세일 포스터", "Winter Sale Poster",
					"冬のセールポスター"),
			new Seed("card", vec(0.1, 0.9, 0.8, 0.1, 0.9, 0.1, 0.2, 0.0), "생일 카드", "Birthday Card", "誕生日カード"),
			new Seed("card", vec(0.15, 0.85, 0.75, 0.15, 0.95, 0.05, 0.25, 0.05), "감사 카드", "Thank You Card",
					"感謝カード"),
			new Seed("presentation", vec(0.2, 0.2, 0.9, 0.9, 0.1, 0.9, 0.8, 0.1), "사업계획서 템플릿",
					"Business Plan Deck", "事業計画書テンプレート"));

	// double... 가변인자로 받아서 리터럴을 짧게 쓰고(new Seed("poster", vec(0.9, 0.1, ...), ...)),
	// 내부적으로 Double[] 박싱만 여기서 한 번 처리 — Asset.embedding이 Double[]이라 타입을 맞춰줌.
	private static Double[] vec(double... values) {
		Double[] boxed = new Double[values.length];
		for (int i = 0; i < values.length; i++) {
			boxed[i] = values[i]; // double → Double 오토박싱
		}
		return boxed;
	}

	@Override
	public void run(String... args) {
		// count()가 0보다 크면 이미 시딩된 것 — 앱을 재시작할 때마다 더미데이터가 중복 쌓이는 걸 방지.
		Boolean alreadySeeded = assetRepository.count().map(count -> count > 0).block();
		if (Boolean.TRUE.equals(alreadySeeded)) {
			return;
		}

		// 애플리케이션 기동 시 1회만 도는 시드 작업이라 block() 사용 — 요청 처리 경로가 아니므로
		// WebFlux 논블로킹 원칙 예외로 둠 (HTTP 요청을 처리하는 스레드가 아니라 기동 스레드에서만 블로킹됨).
		Flux.fromIterable(SEEDS)
			// concatMap: 각 시드를 "앞의 저장이 끝난 뒤 다음 저장 시작"으로 순서대로 처리.
			// flatMap을 썼다면 5개의 save()가 동시에 날아가서 어떤 asset이 먼저 insert되는지가
			// 매번 달라짐 — 여기선 순서가 결과에 영향 없어서 사실 상관없지만, "각 asset을 저장한
			// 직후 그 asset의 id로 번역 3건을 저장"하는 2단계 흐름을 한 줄로 명확하게 표현하려고 씀.
			.concatMap(seed -> assetRepository.save(new Asset(null, seed.category(), seed.embedding(), LocalDateTime.now()))
				.flatMap(saved -> saveTranslations(saved.getId(), seed)))
			.blockLast(); // 이 Flux 전체(5개 시드 처리)가 끝날 때까지 기동 스레드를 대기시킴.
	}

	private Mono<Void> saveTranslations(Long assetId, Seed seed) {
		// saveAll: 여러 엔티티를 한 번에 저장 요청 (내부적으로는 각각 insert가 나감).
		// .then(): 저장된 값 자체는 필요 없고 "다 끝났다"는 신호(Mono<Void>)만 상위로 넘기면 됨.
		return translationRepository
			.saveAll(List.of(new AssetTranslation(null, assetId, "ko", seed.titleKo(), null),
					new AssetTranslation(null, assetId, "en", seed.titleEn(), null),
					new AssetTranslation(null, assetId, "ja", seed.titleJa(), null)))
			.then();
	}
}
