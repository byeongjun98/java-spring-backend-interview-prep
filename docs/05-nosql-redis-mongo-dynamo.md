# 05. NoSQL — Redis / MongoDB / DynamoDB

공고 기술스택: PostgreSQL, MongoDB, DynamoDB, Redis, Vector DB. 각각 "왜 이 DB를 이 용도에 쓰는가"를 설명하는 게 핵심 — 다 똑같이 쓸 수 있는 게 아니라 데이터 모델과 접근 패턴이 다르다.

## 1. 왜 하나의 회사가 4~5종류 DB를 같이 쓰는가 — Polyglot Persistence

- "모든 데이터를 하나의 DB에 넣는다"가 아니라, **데이터의 접근 패턴/일관성 요구/확장성 요구에 맞춰 DB를 고른다**는 게 핵심 사고방식.
- 채용공고 기술스택 추정 매핑(인터뷰에서 이런 식으로 설명 가능해야 함):
  - PostgreSQL: 회원, 결제 등 **정합성이 중요하고 관계가 복잡한** 도메인 (트랜잭션, JOIN)
  - MongoDB: 디자인 문서처럼 **스키마가 유동적이고 중첩 구조가 깊은** 데이터 (문서 하나에 레이어/요소 정보가 트리 형태로 들어있는 디자인 협업 툴의 문서 데이터 같은 것)
  - DynamoDB: **초저지연 + 대규모 트래픽**이 필요한 단순 key-value/조회 패턴 (세션, 카운터, 조회 로그성 데이터)
  - Redis: 캐시, 세션, 분산 락, 실시간 순위/카운터 (In-memory)
  - Vector DB: AI 추천/유사도 검색(임베딩 기반) — "AI 기술을 활용한 추천 시스템"과 직결

## 2. Redis — 캐시/락/세션

### 캐시 패턴

```java
@Cacheable(value = "documents", key = "#id")
public DocumentResponse getById(Long id) {
    return documentRepository.findById(id)...; // 캐시 미스일 때만 실행
}
```
- **Cache-Aside(Look-Aside) 패턴**: 애플리케이션이 먼저 캐시 조회 → 없으면 DB 조회 후 캐시에 채움. Spring `@Cacheable`이 이 패턴을 감싸준 것.
- **캐시 무효화(Invalidation)가 진짜 어려운 부분** — 데이터가 바뀌면 캐시도 갱신/삭제해야 함(`@CacheEvict`). "캐시 무효화와 네이밍이 컴퓨터 과학에서 제일 어려운 두 문제 중 하나"라는 농담이 있을 정도 — 왜 어려운지: 여러 캐시 레이어, TTL과 실제 변경 시점의 불일치, 분산 환경에서 캐시 서버 여러 대 동기화 문제.
- TTL(Time-To-Live) 전략: 너무 짧으면 캐시 효과 없음, 너무 길면 오래된 데이터 노출 — 데이터 특성(자주 안 바뀜/바뀌어도 치명적이지 않음)에 맞춰 결정.

### 분산 락

```java
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent("lock:document:" + id, "locked", Duration.ofSeconds(5)); // SET NX EX
if (Boolean.TRUE.equals(acquired)) {
    try {
        // 임계 구역
    } finally {
        redisTemplate.delete("lock:document:" + id);
    }
}
```
- `SET NX EX`(Not eXists + EXpire)가 Redis 분산 락의 핵심 원자적 연산. **TTL을 반드시 걸어야** 락을 쥔 프로세스가 죽어도 영구 대기(deadlock, 02번 문서)가 안 됨.
- 여러 서버가 동시에 락을 시도할 때 Redis 단일 인스턴스라 원자성 보장 — 단, Redis 자체가 죽으면 락도 같이 날아가는 한계가 있음(Redlock 알고리즘은 이 한계를 보완하려는 시도, 개념만 알아도 충분).

### Redis 분산 락과 04번 문서(재고 차감)의 관계

04번 문서에서 재고 차감 레이스 컨디션을 **DB 락**(조건부 UPDATE, `SELECT FOR UPDATE`)으로 해결했다면, 이건 단일 DB 안에서만 유효한 방법 — MSA로 서비스가 여러 개로 쪼개져서 재고 관리 로직 자체가 여러 서버 인스턴스에서 동시에 실행될 수 있는 상황이면, DB 트랜잭션 락만으론 부족할 수 있고 **애플리케이션 레벨의 분산 락**(위 Redis `SET NX EX`)이 필요해질 수 있음. 다만 재고처럼 정합성이 중요한 데이터는 결국 DB 제약(UNIQUE, 조건부 UPDATE)이 최후의 안전망이 되고, Redis 락은 "같은 재고를 여러 요청이 동시에 만지지 못하게 사전에 걸러주는" 성능 최적화 레이어로 쓰는 경우가 많음 — **Redis 락만 믿고 DB 제약을 생략하면 안 됨** (Redis가 다운되거나 락 TTL이 실제 처리 시간보다 짧으면 그 순간 보호가 사라지기 때문).

### 실시간 카운터/순위

- `INCR`(원자적 증가), Sorted Set(`ZADD`/`ZRANGE`)으로 실시간 랭킹 — DB에서 매번 `COUNT`/`ORDER BY` 하는 것보다 훨씬 빠름. 07번 문서의 "실시간 동시 편집" 세션 관리(누가 지금 편집 중인지)에도 Redis Set/Hash가 자연스럽게 쓰임.

## 3. MongoDB — 문서 지향, 유연한 스키마

```javascript
// 디자인 문서 예시 — 중첩 구조가 깊고 요소마다 필드가 다를 수 있음
{
  "_id": "doc123",
  "title": "포스터 디자인",
  "pages": [
    {
      "elements": [
        { "type": "text", "content": "제목", "x": 10, "y": 20 },
        { "type": "image", "url": "...", "x": 50, "y": 60, "filter": "sepia" }
      ]
    }
  ]
}
```
- RDB였다면 `elements` 테이블에 타입별로 nullable 컬럼이 잔뜩 생기거나(text 전용 필드, image 전용 필드 혼재), 타입별 테이블을 분리해 JOIN이 많아짐 — MongoDB는 **문서 하나로 자연스럽게 표현**되고, 요소 타입이 늘어나도 스키마 마이그레이션 없이 필드 추가 가능.
- 대신 트레이드오프: **여러 문서에 걸친 트랜잭션/JOIN이 약함**(MongoDB 4.0+부터 멀티 도큐먼트 트랜잭션 지원하지만 RDB만큼 자연스럽진 않음). "언제 MongoDB, 언제 PostgreSQL?" → 데이터가 계층적/유동적이고 단일 문서 단위로 완결되면 MongoDB, 여러 엔티티 간 강한 정합성/관계가 필요하면 RDB.
- 인덱스 개념은 RDB와 비슷하게 존재(`db.collection.createIndex(...)`), embedded document(내장) vs reference(참조) 설계 트레이드오프도 알아두면 좋음 — 자주 같이 조회되는 데이터는 embed, 독립적으로 크거나 자주 갱신되는 데이터는 reference.

## 4. DynamoDB — key-value, 예측 가능한 초저지연

```
Table: Sessions
Partition Key: userId
Sort Key: sessionId
Attributes: { lastActive, deviceInfo }
```
- **파티션 키(Partition Key) 설계가 성능을 좌우** — DynamoDB는 파티션 키의 해시값으로 데이터를 여러 파티션에 분산 저장. 특정 키에 트래픽이 몰리면(hot partition) 그 파티션만 병목 — "고르게 분산되는 파티션 키를 고르는 게 설계의 핵심"이라는 걸 설명할 수 있어야 함.
- **RDB와 근본적으로 다른 점**: 유연한 ad-hoc 쿼리(`WHERE` 아무 컬럼이나)가 안 되고, **접근 패턴을 먼저 설계하고 그에 맞춰 키/인덱스(GSI)를 미리 정의**해야 함 ("Query-first design"). JOIN 없음 — 필요하면 애플리케이션에서 여러 번 조회하거나 데이터를 중복 저장(비정규화).
- 완전관리형이라 확장성/가용성은 AWS가 보장 — 대신 설계 유연성을 희생. "왜 세션/카운터처럼 단순 조회 패턴에 DynamoDB를 쓰는가"의 답이 바로 이 트레이드오프.

## 5. Vector DB — AI 추천/유사도 검색용

- 텍스트/이미지를 임베딩(고차원 벡터)으로 변환 후, **벡터 간 거리(코사인 유사도 등)로 "비슷한 것"을 찾는** 용도.
- "AI 기술을 활용한 추천 시스템 개발"(공고 주요업무) — 예: 사용자가 만든 디자인과 비슷한 템플릿 추천, 검색어의 의미적 유사 콘텐츠 검색(키워드 매칭이 아닌 의미 기반 검색).
- 깊게 알 필요는 없지만 "임베딩이 뭐고, 왜 전통적인 인덱스(B-Tree)로는 유사도 검색이 안 되는가(고차원 공간에서 거리 계산이 필요하므로 ANN(Approximate Nearest Neighbor) 알고리즘 필요)" 정도는 설명 가능하면 좋음.

## 6. 자주 나오는 질문

- "이 데이터는 어떤 DB에 저장하시겠어요?" (예: 사용자가 실시간 편집 중인 디자인 문서) → 구조 유동성(MongoDB) + 실시간 세션 상태(Redis) 조합으로 답변 구성
- "캐시 무효화 전략은?" → TTL vs 명시적 evict, 데이터 특성별 트레이드오프
- "Redis가 다운되면 서비스는?" → fallback 전략(캐시 미스 시 DB 직접 조회, 분산 락 실패 시 재시도/graceful degradation), 재고처럼 정합성이 중요한 데이터는 DB 락이 최후의 안전망이라는 것까지 언급

## 실습 방법

1. Docker로 Redis 띄우고 `SET NX EX` 직접 CLI로 실행해보며 분산 락 원리 확인
2. Spring Data Redis로 `@Cacheable`/`@CacheEvict` 걸어보고 캐시 히트/미스를 로그로 확인
3. MongoDB에 중첩 구조 문서 하나 넣고 쿼리해보며 RDB와의 차이 체감

## 참고 키워드 (검색용)
- cache-aside pattern vs write-through
- Redis distributed lock SET NX EX Redlock
- DynamoDB partition key hot partition design
- MongoDB embedded vs referenced document design
- vector database approximate nearest neighbor
