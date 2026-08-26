# 07. 동시성 제어 + 실시간 동시편집

공고 인용 주요업무: "실시간 저장 및 동시 편집 기능을 구현하며, 기술적으로 복잡한 시스템 아키텍처를 설계하고 최적화하는 도전을 경험합니다." 이 공고에서 **가장 기술적으로 특색 있는 주제** — 설계 면접에서 나올 가능성이 높다고 가정하고 준비.

## 1. 문제 정의 — 왜 동시 편집이 어려운가

- 여러 사용자가 같은 문서(디자인 협업 툴의 디자인 파일)를 동시에 편집 → "마지막에 저장한 사람이 이긴다"(Last-Write-Wins)식으로 처리하면 다른 사람의 변경사항이 그냥 사라짐. 사용자 경험상 용납 안 됨.
- 두 가지 접근이 실무에서 대표적:
  1. **락 기반(비관적/낙관적) + 충돌 시 병합/알림** — 구현 난이도 낮음, 실시간성은 다소 제한적
  2. **OT(Operational Transformation) / CRDT** — Google Docs, Figma류가 쓰는 방식, 실시간 문자 단위 동시 편집까지 가능하지만 구현 난이도 매우 높음

이 포지션 인터뷰에서 CRDT/OT를 처음부터 구현하라고 요구할 가능성은 낮음 — **"문제를 단계적으로 어떻게 좁혀가며 설계하는가"**를 보여주는 게 중요. 아래 순서로 사고를 전개하는 연습을 한다.

## 2. 1단계: 낙관적 락 + 버전 충돌 처리 (04번 문서 연장)

```java
@Entity
class Document {
    @Id Long id;
    String content;
    @Version Long version;
}

// Service
Document update(Long id, String newContent, Long clientVersion) {
    Document doc = repo.findById(id).orElseThrow();
    if (!doc.getVersion().equals(clientVersion)) {
        throw new StaleVersionException(); // Controller에서 409 Conflict로 응답
    }
    doc.setContent(newContent);
    return repo.save(doc); // 저장 시점에 @Version 자동 증가 + WHERE version=? 체크
}
```
- 클라이언트는 자기가 마지막으로 받은 `version`을 같이 보냄 → 서버가 현재 버전과 다르면 충돌로 판단, 409 반환.
- 클라이언트 쪽 처리: "다른 사람이 먼저 수정했습니다, 새로고침 후 다시 시도" — 단순하지만 **동시 편집이 드물게 일어나는 경우엔 충분한 해결책**. "모든 문제를 CRDT로 풀 필요는 없다"는 걸 아는 것 자체가 설계력.

## 3. 2단계: 필드/영역 단위로 충돌 범위 좁히기

- 문서 전체를 하나의 버전으로 관리하면 "다른 페이지를 편집했는데도 충돌"이 발생 — 사용자 경험이 나쁨.
- **요소(element) 단위 버전 관리**로 세분화: 디자인 협업 툴의 디자인 파일처럼 여러 요소(텍스트, 이미지)로 구성된 문서라면, 문서 전체가 아니라 **각 요소마다 독립적으로 버전/락을 관리**해서 서로 다른 요소를 편집하는 두 사용자는 충돌이 안 나게 만들 수 있음.
- 이게 "실시간 저장 및 동시 편집" 요구사항에 대한 현실적인 1차 설계안 — 완전한 문자 단위 동시 편집(OT/CRDT)보다 훨씬 구현 가능성이 높으면서 실사용성은 크게 개선됨.

## 4. 3단계: 실시간 반영 — WebSocket + Pub/Sub

```
클라이언트 A가 요소 수정 → WebSocket으로 서버에 전송
  → 서버가 저장 후, 같은 문서를 보고 있는 다른 클라이언트들에게 브로드캐스트
  → Redis Pub/Sub(또는 Kafka)로 여러 WebSocket 서버 인스턴스 간에도 메시지 전파(수평 확장 시 필수)
```
- WebSocket 서버가 여러 대로 스케일 아웃되면, A가 붙은 서버와 B가 붙은 서버가 다를 수 있음 — **Redis Pub/Sub 같은 중개자 없이는 서버 간 메시지 전파가 안 됨**. 이게 "왜 단순 WebSocket만으로는 부족하고 Pub/Sub이 필요한가"의 답.
- 누가 지금 이 문서를 보고 있는지(presence), 누가 어떤 요소를 편집 중인지(locking indicator)는 Redis Set/Hash로 관리하고 TTL로 자동 만료(연결 끊김 대응) — 05번 문서와 직접 연결.

## 5. 4단계 (심화, 알아만 두기): OT / CRDT 개념

- **OT(Operational Transformation)**: 각 클라이언트의 "연산"(예: "3번 위치에 'a' 삽입")을 서버가 다른 클라이언트의 동시 연산과 순서를 맞춰 변환(transform)해서 모두 같은 최종 상태에 도달하게 함. Google Docs 방식. 구현이 매우 까다로움(변환 함수의 정확성 증명 필요).
- **CRDT(Conflict-free Replicated Data Type)**: 데이터 구조 자체를 "어떤 순서로 연산이 도착해도 항상 같은 결과로 수렴"하도록 설계(예: 각 문자에 고유 ID를 부여해 삽입 위치를 좌표가 아닌 관계로 표현). 서버 중재 없이도 클라이언트끼리 병합 가능. Figma가 유사한 방식을 씀.
- 실전 답변 전략: "완전한 이해를 주장하기보다, 왜 이 방식이 필요한지(락 기반의 한계)와 트레이드오프(구현 복잡도 vs UX)를 설명"하는 것으로 충분. 처음부터 CRDT를 밀어붙이면 오히려 "실용적 판단력 부족"으로 보일 수 있음 — **단계적 설계 전개가 핵심 평가 포인트**라는 걸 잊지 말 것.

## 6. Java 동시성 도구 — 03/04번 문서와 겹치지 않는 실전 도구

```java
// synchronized: 가장 단순, 임계 구역 전체 블로킹
synchronized void increment() { count++; }

// ReentrantLock: synchronized보다 유연 (tryLock, 타임아웃, 공정성 옵션)
private final ReentrantLock lock = new ReentrantLock();
void update() {
    lock.lock();
    try { /* ... */ } finally { lock.unlock(); }
}

// AtomicInteger/AtomicLong: CAS(Compare-And-Swap) 기반, 락 없이 원자적 연산
private final AtomicInteger count = new AtomicInteger();
count.incrementAndGet();

// ConcurrentHashMap: 세그먼트 단위로 락을 분산해 HashMap보다 동시성 처리량 높음
Map<String, Session> sessions = new ConcurrentHashMap<>();
```
- 단일 서버 프로세스 내 동시성(위 도구들)과 **여러 서버 간 분산 동시성(Redis 분산 락, 05번 문서)**을 구분해서 설명할 수 있어야 함 — MSA/스케일 아웃 환경에서는 `synchronized`가 다른 서버의 스레드는 전혀 막지 못한다는 게 자주 나오는 함정 질문.

## 7. 자주 나오는 질문

- "두 사용자가 같은 문서를 동시에 편집하면 어떻게 처리하나요?" → 1~4단계 순서로 설계 전개
- "왜 처음부터 CRDT를 안 쓰나요?" → 구현/운영 복잡도 vs 실제 충돌 빈도, 단계적 접근의 합리성
- "WebSocket 서버를 여러 대로 스케일 아웃하면 생기는 문제는?" → Pub/Sub 필요성
- "낙관적 락 충돌이 자주 나면 사용자 경험이 나빠지는데?" → 충돌 범위를 요소 단위로 좁히는 설계로 완화

## 실습 방법

1. `@Version` 낙관적 락을 실제로 걸고, 두 개의 스레드/요청으로 동시 업데이트 시도해 `OptimisticLockException` 재현 (04번 문서 실습과 연계)
2. `project/`의 document 도메인 설계 시 요소 단위 버전 관리를 실제로 스키마에 반영해보기
3. Google Docs/Figma가 각각 어떤 방식(OT vs CRDT)을 쓰는지 아티클 하나씩 찾아 읽고 한 문단으로 요약해보기

## 참고 키워드 (검색용)
- optimistic locking conflict resolution real-time collaboration
- WebSocket horizontal scaling Redis Pub/Sub
- Operational Transformation vs CRDT
- Java ReentrantLock vs synchronized vs AtomicInteger
- ConcurrentHashMap internal segment locking
