# design-reco-service 권장 학습 순서

`docs/00`~`11`(backend-prep 가이드 학습 트랙)과는 별도 문서. 저긴 "직접 짜면서 배우는" 트랙이고,
이건 "Claude가 완성한 구현체를 읽으면서 이해하는" 트랙이라 성격이 다름 — 그래서 번호 순서 체계(`docs/`)에
안 넣고 이 프로젝트 폴더에 따로 둠.

## 0단계 — 큰 그림 (5분)

`README.md`를 먼저 읽는다. 아키텍처 절, 시나리오, TODO 표, 특히 **"실제로 발견하고 고친 버그"** 절.
코드를 보기 전에 "이 서비스가 뭘 하는지"부터 박아두면 이후 코드가 훨씬 빨리 읽힘.

## 1단계 — 어휘: 도메인 모델

로직이 없어서 제일 빠르게 넘어갈 수 있는 구간. `schema.sql`을 옆에 펴놓고 클래스와 테이블을 1:1로 대응시키며 읽는다.

1. `src/main/java/.../domain/Asset.java`
2. `domain/AssetTranslation.java`
3. `domain/UserEvent.java`
4. `domain/EventType.java`

**체크포인트**: "R2DBC 엔티티는 JPA와 달리 연관관계 매핑(@ManyToOne 등)이 없다"를 자기 말로 설명할 수 있으면 통과.

## 2단계 — 순수 로직 (reactive 없음, 제일 쉬움)

테스트 코드가 스펙 역할을 함 — **테스트 이름·assert부터 먼저 읽고** "이 코드가 뭘 보장해야 하는지" 예상한 다음 구현을 확인하는 순서로.

1. `recommend/CosineSimilarity.java` + `CosineSimilarityTest.java`
2. `recommend/EmbeddingMath.java` + `EmbeddingMathTest.java`
3. `recommend/RecommendationService.java` + `RecommendationServiceTest.java`

직접 돌려보면서 읽기:
```
./gradlew test --tests "com.example.designreco.recommend.*" --info
```

**체크포인트**: 코사인 유사도가 뭘 재는 값인지(방향 유사도, 크기 무시), item-based/user-based가 왜
메서드 하나(`topSimilarToVector`)로 합쳐지는지 설명 가능하면 통과.

## 3단계 — 얕은 reactive: 유틸/조립 레이어

1. `web/AcceptLanguageResolver.java` + `AcceptLanguageResolverTest.java` — reactive 전혀 없음, 표준 `Locale.LanguageRange` API 사용법만.
2. `web/AssetResponseAssembler.java` — `Mono` 처음 등장. `switchIfEmpty`/`map`이 뭘 하는지.

## 4단계 — 진짜 몸통: 컨트롤러

제일 밀도 높은 구간. 메서드 하나 끝날 때마다 "이게 왜 Mono가 아니라 Flux인지", "이 시점에 실제로
DB를 때리는지 아닌지"를 스스로 말해보고 넘어갈 것.

1. `controller/AssetController.java` — Redis 캐시 hit/miss 분기, `flatMapSequential` 버그 스토리를 코드로 재확인.
2. `controller/UserController.java` — `zipWith`로 두 조회 병렬 실행, centroid 계산, 이벤트 검증(`EventType.valueOf` + `Mono.error`).

## 5단계 — 기동 흐름

`config/DummyDataInitializer.java` — `CommandLineRunner`, `concatMap`을 쓴 이유(순서 보장이 필요해서가 아니라
"asset 저장 → 그 id로 번역 저장"이라는 2단계 흐름을 명확히 표현하려고).

## 6단계 — 프론트엔드까지 붙여서 전체 흐름 보기

`project/design-reco-frontend/`가 이 백엔드를 실제로 호출하는 클라이언트. 화면에서 버튼 하나 누른 게
어느 API를 거쳐 어느 컨트롤러 메서드까지 가는지 끝까지 추적해보면 "실제 서비스는 이렇게 엮이는구나"가
체감됨. 프론트 자체(바닐라 JS)를 깊게 공부할 필요는 없음 — `app.js`의 `api.*` 함수와 그게 호출하는
백엔드 엔드포인트를 짝지어 보는 정도로 충분.

## 이해 검증 방법 (읽기만 하면 금방 휘발됨)

1. **직접 실행 + curl/브라우저** — README "실행 방법"대로 로컬에서 띄우고 각 엔드포인트 직접 호출.
   로그 찍어가며 요청이 어느 클래스 어느 라인을 타는지 추적.
2. **일부러 버그 재현** — `AssetController`의 `flatMapSequential`을 도로 `flatMap`으로 바꾸고
   `limit=3` 이상으로 여러 번 호출, 순서가 뒤섞이는 걸 직접 눈으로 본 다음 원복. 읽어서 아는 것과
   겪어서 아는 것 차이가 여기서 남는다.
3. **말로 설명해보기 (설계 면접 시뮬레이션)** — 코드 안 보고 스스로 질문에 답해보기:
   - pgvector 안 쓰고 인메모리 코사인 유사도로 시작한 이유는?
   - item-based/user-based 추천이 왜 메서드 하나로 합쳐지나?
   - 캐시 TTL 5분의 트레이드오프는?
   - `flatMap`과 `flatMapSequential`의 차이, 언제 어느 쪽을 써야 하나?
   막히는 지점만 다시 코드/README로 확인.
4. **작게 기능 추가/버그 찾아보기** — 예: 지원 locale에 `fr` 추가해보기. 또는 지금 캐시 키(`reco:asset:{id}:{limit}`)에
   locale이 안 들어가 있어서 item-based 추천 응답의 title/description이 locale별로 다른데도 캐시가 하나로
   공유되는 여지가 있음 — 직접 재현해보고 고쳐보는 것도 좋은 연습.
5. **backend-prep이랑 대조** — 같은 "목록 조회" 패턴을 `backend-prep`(MVC+JPA, 동기)과 여기(WebFlux+R2DBC,
   비동기)에서 어떻게 다르게 짜는지 나란히 비교. sync/reactive 감각 차이가 여기서 확실해짐.

## 곁들일 이론 문서 (backend-prep 학습 트랙)

막히는 개념이 나올 때마다 해당 문서로 점프:

- `docs/09-webflux-reactive.md` — Mono/Flux 기본
- `docs/05-nosql-redis-mongo-dynamo.md` — Redis 캐싱 패턴
- `docs/10-search-opensearch.md` — 벡터/유사도 검색 배경
