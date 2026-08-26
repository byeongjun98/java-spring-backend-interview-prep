# 09. Spring WebFlux / Reactive Programming

공고 인용 우대사항: "Spring WebFlux 기반 Reactive Programming 개발 경험이 있으신 분". 필수 아니지만, 02/08번 문서(I/O 바운드, 대용량 트래픽)와 바로 연결되는 주제라 개념만이라도 확실히 잡아두면 인터뷰에서 우대사항까지 커버 가능.

## 1. 왜 Reactive인가 — Thread-per-request 모델의 한계 (02번 문서 연결)

- Spring MVC(기본, Tomcat): 요청마다 스레드 하나를 할당하고, 그 스레드는 DB 응답/외부 API 응답을 **기다리는 동안 블로킹**됨. I/O 바운드 서비스(백엔드 대부분)에서는 스레드가 실제 CPU 연산 없이 "그냥 기다리는" 시간이 대부분.
- 동시 접속자가 늘면 스레드 수도 같이 늘어야 하는데, 스레드는 각각 메모리(Stack, 기본 1MB 내외)를 차지하고 컨텍스트 스위칭 비용도 있음 — **스레드 개수가 확장의 한계**가 됨.
- Reactive(WebFlux, Netty 기반)는 적은 수의 스레드(보통 CPU 코어 수만큼)로 **논블로킹 I/O + 이벤트 루프**를 사용 — I/O 대기 중엔 그 스레드가 다른 요청을 처리하러 감. 스레드를 "기다리는 데" 쓰지 않는 게 핵심.

## 2. 명령형(Imperative) vs 반응형(Reactive) 코드 비교

```java
// Spring MVC (명령형, 블로킹)
@GetMapping("/documents/{id}")
DocumentResponse get(@PathVariable Long id) {
    Document doc = documentRepository.findById(id).orElseThrow(); // 여기서 스레드가 DB 응답까지 블로킹
    return toResponse(doc);
}

// Spring WebFlux (반응형, 논블로킹)
@GetMapping("/documents/{id}")
Mono<DocumentResponse> get(@PathVariable Long id) {
    return documentRepository.findById(id) // Mono<Document> — 즉시 반환, 실제 값은 나중에 emit
        .map(this::toResponse); // 값이 도착하면 콜백처럼 실행
}
```
- **`Mono<T>`**: 0 또는 1개의 결과를 담는 비동기 컨테이너 (Java `Optional` + `CompletableFuture`를 합친 느낌).
- **`Flux<T>`**: 0..N개의 결과를 담는 비동기 스트림(Java Stream의 비동기/시간축 버전).
- 리턴 타입 자체가 "아직 결과가 없고, 나중에 채워질 것"이라는 뜻 — 호출 스레드는 결과를 기다리지 않고 즉시 다음 일을 함.

## 3. 함정 — 어디서 막히면 Reactive의 의미가 없어지는가

```java
// 이렇게 짜면 Reactive의 이점이 전부 사라짐 — 블로킹 JDBC 호출을 Reactive 안에서 그대로 씀
Mono<DocumentResponse> get(Long id) {
    return Mono.fromCallable(() -> jdbcTemplate.queryForObject(...)); // JDBC는 원래 블로킹 드라이버
}
```
- **가장 중요한 원칙: 파이프라인 전체(DB 드라이버, HTTP 클라이언트 등)가 논블로킹이어야 의미가 있다.** JPA/JDBC는 태생적으로 블로킹 — WebFlux와 같이 쓰려면 R2DBC(Reactive Relational Database Connectivity) 같은 논블로킹 드라이버가 필요.
- 이게 실무에서 "우리 서비스에 WebFlux를 도입해야 하나?"를 결정할 때 제일 먼저 걸리는 벽 — **DB 드라이버부터 전부 Reactive 생태계로 바꿔야 진짜 이득**을 봄. 부분적으로만 도입하면 오히려 복잡도만 늘고 이득은 없을 수 있음. 인터뷰에서 이 트레이드오프를 언급하면 "Reactive를 무조건 좋다고 밀어붙이지 않는" 균형 잡힌 시각으로 평가됨.

## 4. 언제 WebFlux가 실제로 이득인가

- **I/O 바운드 + 높은 동시 연결 수**(예: 실시간 동시 편집의 WebSocket/SSE 연결을 대량으로 유지, 외부 API를 여러 개 병렬 호출 후 합치는 게이트웨이성 서비스)에서 강점.
- 반대로 **CPU 바운드 작업**(복잡한 연산)이 많으면 Reactive가 딱히 이득이 없음 — 어차피 CPU가 병목이라 스레드 모델을 바꿔도 처리량이 안 늘어남.
- 이 공고 맥락: "실시간 저장 및 동시 편집"(다수의 지속 연결 유지), "글로벌 트래픽 관리"(다수의 동시 접속) — WebFlux가 우대사항으로 명시된 이유를 이 두 지점과 연결해서 설명 가능해야 함.

## 5. Backpressure — Reactive Streams의 핵심 개념

- Producer(데이터를 만드는 쪽)가 Consumer(처리하는 쪽)보다 빠르면, Consumer가 감당 못 할 만큼 데이터가 쌓임 — **Backpressure는 Consumer가 "내가 처리 가능한 만큼만 보내라"고 Producer에게 요청하는 메커니즘**.
- 08번 문서의 메시지 큐가 "버퍼로 흡수"하는 방식이라면, Backpressure는 **애초에 producer 속도를 조절**하는 방식이라는 차이를 알아두면 좋음.

## 6. 자주 나오는 질문

- "WebFlux를 왜 쓰나요?" → Thread-per-request 한계 + I/O 바운드 특성 + 논블로킹 I/O
- "우리 서비스에 WebFlux 도입하면 무조건 좋나요?" → 아니오, DB 드라이버 등 전체 스택이 논블로킹이어야 이득, 부분 도입의 함정
- "Mono/Flux 차이는?" → 0-1개 vs 0-N개 비동기 결과
- "CPU 바운드 작업에도 WebFlux가 유리한가요?" → 아니오, I/O 바운드에서만 강점

## 실습 방법

1. Spring WebFlux + R2DBC(또는 in-memory `Mono.fromSupplier`)로 간단한 `Mono<T>` 반환 엔드포인트 하나 짜보기
2. 같은 API를 MVC(블로킹)와 WebFlux(논블로킹)로 각각 짜고 부하 테스트(08번 문서 도구)로 동시 연결 수에 따른 차이 비교해보기 (여유 있으면)
3. `Mono`/`Flux`의 `map`/`flatMap`/`zip` 정도만 먼저 익히기 — 연산자 전체를 외울 필요는 없음

## 참고 키워드 (검색용)
- Spring WebFlux vs Spring MVC thread-per-request
- Reactive Streams backpressure
- Mono Flux Project Reactor
- R2DBC reactive database driver
- non-blocking I/O event loop Netty
