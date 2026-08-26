# 03. Spring MVC / RESTful API 설계

공고 인용: "Java, Spring, AWS 기반 웹 백엔드 서비스 개발 경험", "RESTful API 설계 및 개발 경험을 보유하신 분".

## 1. Spring MVC 요청 처리 흐름

```
Client → DispatcherServlet → HandlerMapping(어떤 Controller가 처리할지 결정)
       → HandlerAdapter → Controller → Service → Repository → DB
       ← ViewResolver(또는 @ResponseBody면 바로 직렬화) ← 응답
```
- **DispatcherServlet**이 프론트 컨트롤러(Front Controller 패턴) — 모든 요청이 여기로 먼저 들어와서 적절한 컨트롤러로 위임된다. "Spring MVC가 왜 프론트 컨트롤러 패턴인가"는 자주 나오는 개념 질문.
- 계층 분리: **Controller(요청/응답, 검증) → Service(비즈니스 로직, 트랜잭션 경계) → Repository(영속성)**. 각 계층이 서로의 역할을 침범하면 안 됨 (예: Controller에서 직접 DB 접근 — 흔한 안티패턴).

```java
@RestController
@RequestMapping("/api/documents")
class DocumentController {
    private final DocumentService documentService; // 생성자 주입 (01번 문서 DIP)

    DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{id}")
    ResponseEntity<DocumentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getById(id));
    }

    @PostMapping
    ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentCreateRequest req) {
        DocumentResponse created = documentService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```
- 생성자 주입을 쓰는 이유: 필드 주입(`@Autowired` on field)은 테스트 시 Mock 주입이 번거롭고, 순환 의존을 컴파일 타임에 못 잡음. 생성자 주입은 `final` 필드 강제 + 불변성 확보 + 순환 의존 즉시 발견(앱 기동 실패).

## 2. RESTful API 설계 원칙

### 리소스 중심 URL

| 나쁜 예 | 좋은 예 | 이유 |
|---|---|---|
| `GET /getDocument?id=1` | `GET /documents/1` | URL은 리소스(명사), 동작은 HTTP 메서드(동사)가 표현 |
| `POST /documents/1/delete` | `DELETE /documents/1` | 동작을 URL에 넣지 않음 |
| `GET /documents/1/update` | `PATCH /documents/1` | 조회와 변경을 메서드로 구분 |

### HTTP 메서드와 멱등성(Idempotency)

| 메서드 | 용도 | 멱등성 | 캐시 가능 |
|---|---|---|---|
| GET | 조회 | O | O |
| POST | 생성(리소스 URI를 서버가 결정) | X | X |
| PUT | 전체 교체(리소스 URI를 클라이언트가 결정) | O | X |
| PATCH | 부분 수정 | 구현에 따라 다름(보통 X) | X |
| DELETE | 삭제 | O(같은 요청 반복해도 결과는 "없음"으로 동일) | X |

### 멱등키(Idempotency Key) 설계 — POST를 안전하게 재시도하는 법

표에서 보듯 POST는 멱등하지 않음 — 근데 결제/주문처럼 **한 번만 실행돼야 하는 POST**는 실무에서 계속 등장함. 문제 상황:

```
클라이언트가 결제 요청(POST /payments)을 보냄
  → 서버는 결제를 처리하고 성공 응답을 만드는 중인데
  → 응답이 클라이언트에 도달하기 전에 네트워크가 끊김
  → 클라이언트는 "실패했나?" 하고 같은 요청을 재시도
  → 서버 입장에선 완전히 새로운 POST 요청 — 결제가 두 번 실행됨 (이중 결제)
```

**해결책**: 클라이언트가 요청마다 고유한 멱등키(보통 UUID)를 만들어서 헤더에 실어 보낸다.
```
POST /payments
Idempotency-Key: 3f9a2b10-...
```
서버는 그 키를 이미 처리한 적 있는지 확인하고, 처리했다면 **결제를 다시 실행하지 않고 이전 결과를 그대로 반환**한다.

```java
// idempotency_key 테이블: key(PK, UNIQUE) + 저장된 응답 + 생성시각
@Transactional
public PaymentResponse pay(String idempotencyKey, PaymentRequest req) {
    Optional<IdempotencyRecord> existing = idempotencyRepository.findById(idempotencyKey);
    if (existing.isPresent()) {
        return existing.get().getCachedResponse(); // 재실행 안 하고 이전 결과 그대로 반환
    }
    PaymentResponse response = doActualPayment(req);
    idempotencyRepository.save(new IdempotencyRecord(idempotencyKey, response)); // UNIQUE 제약 있는 PK
    return response;
}
```

**여기서 흔히 놓치는 함정**: "먼저 조회해서 없으면 처리하고 저장한다"는 순서 자체가 **check-then-act 패턴이라 동시성 race condition에 취약**함 (07번 문서에서 다시 다룰 개념). 같은 키로 된 요청 두 개가 거의 동시에 들어오면, 둘 다 `findById`에서 "없음"을 보고 둘 다 결제를 실행해버릴 수 있음. 멱등키 테이블의 `key` 컬럼에 **DB UNIQUE 제약**을 걸어두면, 두 번째 INSERT가 제약 위반으로 실패하므로 그 예외를 잡아서 "이미 처리 중/처리됨"으로 응답하면 된다 — **애플리케이션 로직이 아니라 DB 제약으로 동시성을 막는다**는 게 핵심 아이디어. 04번 문서에서 배울 락(lock)과 같은 문제의식.

인터뷰에서 결제 관련 질문이 나오면 이 설계(멱등키 + DB 유니크 제약)로 바로 답변 구성 가능.

### 상태 코드를 정확히 쓰기

| 코드 | 의미 | 흔한 실수 |
|---|---|---|
| 200 OK | 성공(응답 바디 있음) | - |
| 201 Created | 생성 성공 | POST 성공을 전부 200으로 퉁치는 실수 |
| 204 No Content | 성공했지만 바디 없음(DELETE 등) | - |
| 400 Bad Request | 클라이언트 요청 자체가 잘못됨(검증 실패) | 서버 에러(500)와 혼동 |
| 401 Unauthorized | 인증 안 됨(누구인지 모름) | 403과 혼동 |
| 403 Forbidden | 인증은 됐지만 권한 없음 | 401과 혼동 |
| 404 Not Found | 리소스 없음 | - |
| 409 Conflict | 상태 충돌(중복 생성, 낙관적 락 충돌) | 07번 문서 동시편집 충돌 처리에서 실제 사용 |
| 500 Internal Server Error | 서버 예외 | 클라이언트 잘못까지 500으로 던지는 실수 |

### 버저닝 / 페이지네이션

- 버저닝: URL(`/api/v1/...`)이 가장 흔하고 명시적. 헤더 기반 버저닝도 있지만 실무에선 URL 방식이 압도적으로 많이 쓰임.
- 페이지네이션: `offset/limit`(단순하지만 대량 데이터에서 뒤 페이지로 갈수록 느려짐 — OFFSET이 큰 값을 skip해야 하므로) vs **커서 기반(cursor-based)**(마지막으로 본 항목의 ID/timestamp를 기준으로 다음 페이지 조회, 대용량 트래픽에 유리) — 08번 문서(대용량 트래픽)와 연결.

## 3. 예외 처리 — `@ControllerAdvice`로 일관성 확보

```java
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }
}
```
- 컨트롤러마다 try-catch를 반복하지 않고 **전역에서 예외 → HTTP 상태코드 매핑을 일원화**. 커스텀 예외 클래스(예: `BusinessException(ErrorCode)`)를 만들어서 도메인별 에러 코드를 실어 던지고, `@RestControllerAdvice`에서 그 예외 타입만 잡아 상태코드로 변환하는 패턴이 정확히 이 목적.

## 4. 검증(Validation)

```java
record DocumentCreateRequest(
    @NotBlank String title,
    @Size(max = 10000) String content
) {}
```
- `@Valid` + Bean Validation(`@NotBlank`, `@Size`, `@Email` 등)으로 **컨트롤러 진입 시점에 형식 검증**을 끝냄 — 비즈니스 로직(Service)에는 "형식은 이미 유효하다"는 전제로 들어가게 만드는 게 계층 분리의 핵심.
- 형식 검증(길이, null 여부)과 비즈니스 검증(예: "이미 존재하는 이메일인가")을 구분할 것 — 후자는 DB 조회가 필요하므로 Service 계층 책임.

## 5. 자주 나오는 설계 면접 질문

- "이 API를 어떻게 설계하시겠어요?" (예: 문서 목록 조회 + 검색 + 페이지네이션) → 리소스 명사, 쿼리 파라미터로 필터/정렬, 커서 기반 페이지네이션까지 한 번에 설계 가능해야 함
- "PUT과 PATCH 차이는?" → 전체 교체 vs 부분 수정, 멱등성 여부
- "동시에 같은 리소스를 두 클라이언트가 수정하면?" → 낙관적 락 + 409 Conflict (07번 문서로 연결)
- "API 응답 포맷을 어떻게 통일하나요?" → 공통 `ApiResponse<T>` 래퍼 + `GlobalExceptionHandler`
- "같은 결제 요청이 중복으로 들어오면?" → 멱등키 + DB UNIQUE 제약으로 두 번째 요청을 막고 첫 응답 재반환

## 실습 방법

1. Spring Initializr로 최소 프로젝트 생성 → Controller/Service/Repository 3계층으로 CRUD API 하나 손으로 짜보기
2. 위 상태코드 표 안 보고 즉답 연습 (200/201/204/400/401/403/404/409/500)
3. Postman/curl로 실제 요청 보내서 상태코드/응답 확인해보기 — 눈으로만 읽지 말 것
4. `project/`에 멱등키 기반 엔드포인트 하나 구현 — 같은 `Idempotency-Key`로 두 번 연속 호출해서 실제로 한 번만 처리되는지 확인 (동시에 두 요청을 보내는 것까지 재현하려면 04번 문서 학습 후 다시 시도)

## 참고 키워드 (검색용)
- REST API design best practices resource naming
- HTTP idempotency PUT vs POST
- Idempotency key design payment API
- Spring @ControllerAdvice exception handling
- cursor-based pagination vs offset pagination
- Bean Validation Java @Valid
