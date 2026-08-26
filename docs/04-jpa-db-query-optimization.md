# 04. JPA + PostgreSQL 쿼리 최적화

공고 인용: "DB 쿼리 분석을 통한 최적화 경험이 있으신 분". 기술스택: PostgreSQL.

## 1. JPA/Hibernate 기본 동작 원리

```java
@Entity
class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String title;

    @Version // 낙관적 락 — 07번 문서에서 실제 사용
    Long version;

    @ManyToOne(fetch = FetchType.LAZY) // 기본을 LAZY로 — 아래 N+1 참고
    Member owner;
}
```
- **영속성 컨텍스트(Persistence Context)**: JPA가 엔티티를 메모리에서 관리하는 1차 캐시. 같은 트랜잭션 내에서 같은 ID로 조회하면 SQL 없이 캐시에서 반환(동일성 보장, `==` 비교 가능).
- **더티 체킹(Dirty Checking)**: 트랜잭션 커밋 시점에 엔티티 필드가 처음 로드했을 때와 달라졌으면 자동으로 UPDATE 쿼리 생성 — `save()` 명시 호출 없이도 반영된다는 걸 모르면 "왜 이 코드가 DB를 업데이트하지?" 하고 헷갈리게 됨.

### N+1 문제 — JPA 인터뷰 단골 질문 1순위

```java
List<Document> docs = documentRepository.findAll(); // 쿼리 1번
for (Document d : docs) {
    d.getOwner().getName(); // LAZY 로딩이면 각 document마다 owner 조회 쿼리 추가 발생 → N번
}
// 총 1 + N번 쿼리 실행
```
- 원인: 연관관계를 LAZY로 걸어두고 반복문에서 접근하면 매번 별도 쿼리 발생.
- 해결책:
  - **Fetch Join**: `SELECT d FROM Document d JOIN FETCH d.owner` — 한 번의 쿼리로 연관 엔티티까지 조회.
  - **`@EntityGraph`**: 어노테이션으로 fetch join을 선언적으로 지정.
  - **Batch Size**(`@BatchSize` 또는 `hibernate.default_batch_fetch_size`): LAZY를 유지하되 `IN` 절로 묶어서 N번을 몇 번으로 줄임.
- "언제 Fetch Join, 언제 Batch Size?" → 1:1 상황에서 항상 같이 쓰는 연관관계면 Fetch Join, 컬렉션이 여러 개라 Fetch Join을 다 걸면 카테시안 곱이 터지는 경우엔 Batch Size.

### 즉시 로딩(EAGER) vs 지연 로딩(LAZY)

- **기본을 항상 LAZY로** 설정하는 게 실무 원칙. EAGER는 예상 못 한 시점에 불필요한 조인이 걸려 성능 저하의 원인이 되기 쉬움 — "필요할 때 명시적으로 Fetch Join으로 가져온다"는 방향이 제어 가능성이 높음.

## 2. 쿼리 분석 — `EXPLAIN ANALYZE`

```sql
EXPLAIN ANALYZE
SELECT * FROM documents WHERE owner_id = 123 ORDER BY created_at DESC LIMIT 20;
```
- **Seq Scan**(전체 테이블 스캔) vs **Index Scan**(인덱스 사용) — 실행계획에서 이 차이를 읽을 수 있어야 함.
- `owner_id`에 인덱스가 없으면 Seq Scan → 인덱스 생성 후 Index Scan으로 바뀌는지 확인하는 게 기본 최적화 루틴.
- **cost**(예상 비용), **actual time**(실제 소요 시간), **rows**(예상/실제 반환 행 수)를 비교 — 예상과 실제가 크게 다르면 통계 정보(`ANALYZE` 명령)가 오래됐다는 신호.

### 인덱스 설계 기본

```sql
CREATE INDEX idx_documents_owner_created ON documents(owner_id, created_at DESC);
```
- **복합 인덱스는 컬럼 순서가 중요** — WHERE 절 조건이 `owner_id =` (동등 비교)이고 `ORDER BY created_at DESC`면, `owner_id`를 앞에 둬야 그 안에서 `created_at` 정렬까지 인덱스로 처리 가능(정렬 비용 제거).
- 인덱스는 조회를 빠르게 하지만 **쓰기(INSERT/UPDATE) 시 인덱스도 같이 갱신**되므로 비용이 든다 — "인덱스는 공짜가 아니다"를 설명할 수 있어야 함. 카디널리티(고유값 비율)가 낮은 컬럼(예: boolean)은 인덱스 효과가 적음.
- **커버링 인덱스(Covering Index)**: SELECT하는 컬럼까지 인덱스에 포함시켜 테이블 접근 자체를 생략하는 기법 — 있으면 좋다는 정도만 알아도 충분.

## 3. 트랜잭션 격리 수준

| 격리 수준 | Dirty Read | Non-repeatable Read | Phantom Read |
|---|---|---|---|
| Read Uncommitted | 발생 | 발생 | 발생 |
| Read Committed (PostgreSQL 기본값) | 방지 | 발생 | 발생 |
| Repeatable Read | 방지 | 방지 | 발생(PostgreSQL은 실제로 방지) |
| Serializable | 방지 | 방지 | 방지 |

- PostgreSQL은 **MVCC(Multi-Version Concurrency Control)** 방식이라 읽기 작업이 쓰기 작업을 블로킹하지 않음 — "락 기반이 아니라 버전 기반으로 동시성을 처리한다"는 점이 MySQL(InnoDB)과 비교 질문에서 나올 수 있는 포인트.
- Spring에서는 `@Transactional(isolation = Isolation.READ_COMMITTED)`처럼 지정. 기본값을 함부로 올리면(예: Serializable) 동시성 처리량이 급격히 떨어지므로 **필요한 트랜잭션에만 격리 수준을 좁혀서 올리는 게 원칙**.

## 4. 낙관적 락 vs 비관적 락 (07번 문서와 겹치지만 DB 관점 정리)

```java
@Version
Long version; // UPDATE ... WHERE id=? AND version=? — 버전 안 맞으면 0 row 업데이트 → OptimisticLockException
```
- **낙관적 락**: 충돌이 드물 것이라 가정, 커밋 시점에만 버전 체크. 실패하면 재시도/사용자에게 409 반환 — 07번 문서(실시간 동시편집)에서 실제로 쓸 메커니즘.
- **비관적 락**(`SELECT ... FOR UPDATE`): 조회 시점부터 락을 걸어 다른 트랜잭션의 접근을 차단. 충돌이 잦고 반드시 순서를 보장해야 할 때 사용.

### 재고 차감 레이스 컨디션 — 비관적 락이 필요한 대표 사례

재고 1개 남은 상품에 주문 두 개가 거의 동시에 들어오는 상황:

```
Tx A: SELECT stock FROM product WHERE id=1;   -- 1 읽음
Tx B: SELECT stock FROM product WHERE id=1;   -- 1 읽음 (A가 아직 커밋 안 함)
Tx A: UPDATE product SET stock = 0 WHERE id=1; COMMIT;  -- stock=0
Tx B: UPDATE product SET stock = 0 WHERE id=1; COMMIT;  -- stock=0 (실제로는 -1이어야 정상인데 0으로 덮어씀)
```
둘 다 "1개 있었다"고 믿고 각자 주문을 확정해버림 — **재고는 1개인데 주문은 2건 나가는 오버셀(oversell)**. 이게 바로 위 3번(트랜잭션 격리 수준)에서 다룬 **Non-repeatable Read** 문제의 실전 버전 — Read Committed 정도로는 안 막아줌.

**1차 해결책 (제일 간단, 락 없이도 됨)**: 조건부 원자적 UPDATE.
```sql
UPDATE product SET stock = stock - 1 WHERE id = 1 AND stock > 0;
```
이 한 줄이 원자적이라 두 트랜잭션이 동시에 실행돼도 DB가 순차적으로 처리 — 재고가 0이 되는 순간 `WHERE stock > 0` 조건에 걸려 두 번째 UPDATE는 0 rows affected. **재고 차감처럼 단순 증감이면 이 방법만으로 충분**하고, 별도 락 개념이 필요 없다는 걸 아는 게 중요 (없는 문제에 무거운 해법을 안 쓰는 감각).

**2차 해결책 (조건이 복잡해서 한 줄 UPDATE로 못 끝낼 때)**: 비관적 락으로 명시적으로 잠그고, 여러 단계 로직(재고 확인 + 포인트 차감 + 주문 생성 등)을 그 안에서 처리.
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(Long id); // SELECT ... FOR UPDATE 로 변환됨
}
```
`findByIdForUpdate`로 조회한 순간부터 트랜잭션이 끝날 때까지 다른 트랜잭션은 같은 row를 잠그지 못하고 대기(blocking) — 그래서 "비관적"(먼저 잠그고 시작). 대기가 길어지면 처리량이 떨어지므로, **트랜잭션 안에서 이 락을 오래 들고 있으면 안 됨**(외부 API 호출 등 느린 작업을 락 잡은 채로 하지 말 것).

## 5. 자주 나오는 질문

- "N+1 문제가 뭐고 어떻게 해결하나요?" → 원인 + Fetch Join/Batch Size 두 가지 해결책 + 선택 기준
- "느린 쿼리를 어떻게 분석하나요?" → `EXPLAIN ANALYZE` → Seq Scan 확인 → 인덱스 추가 → 재확인
- "낙관적 락과 비관적 락 중 언제 뭘 쓰나요?" → 충돌 빈도 기준
- "PostgreSQL은 왜 읽기가 쓰기를 안 막나요?" → MVCC
- "재고 차감 시 동시성 문제는 어떻게 막나요?" → 조건부 원자적 UPDATE 먼저 고려, 로직이 복잡하면 비관적 락(`SELECT FOR UPDATE`)

## 실습 방법

1. 로컬 PostgreSQL(Docker)에 더미 데이터 1만 건 넣고 인덱스 없이/있이 `EXPLAIN ANALYZE` 비교해보기
2. JPA 엔티티 연관관계 걸고 일부러 N+1 재현 → Fetch Join으로 고쳐서 쿼리 로그(`show-sql`)로 횟수 비교
3. `@Version` 필드로 낙관적 락 걸고 동시에 두 트랜잭션이 같은 row 수정 시도 → `OptimisticLockException` 재현
4. 재고(stock) 컬럼 하나 만들어서 값 1로 세팅 → 두 스레드/두 트랜잭션에서 동시에 `stock - 1` 시도 → 락 없이 하면 오버셀 재현되는지, 조건부 UPDATE(`WHERE stock > 0`)로 고치면 막히는지 직접 확인

## 참고 키워드 (검색용)
- JPA N+1 problem fetch join vs batch size
- PostgreSQL EXPLAIN ANALYZE seq scan vs index scan
- MVCC PostgreSQL transaction isolation
- optimistic lock vs pessimistic lock JPA @Version
- composite index column order
- race condition inventory oversell atomic update
