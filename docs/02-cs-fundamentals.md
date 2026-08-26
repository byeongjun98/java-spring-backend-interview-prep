# 02. CS 기초 — 자료구조 / 운영체제 / 컴퓨터구조

공고 인용: "자료구조, 운영체제, 컴퓨터 아키텍쳐, OOP 등 기본 CS 지식을 갖추신 분". 기술 스크리닝(온라인 30분)이 이걸 별도 검증하므로 **우선순위 최상위**로 학습.

30분 스크리닝은 코딩보다 개념 질문/짧은 설명 위주일 가능성이 높음. 따라서 이 문서는 "구현"보다 **"막힘없이 설명 가능한가"**에 초점을 둔다.

## 1. 자료구조 — Big-O + Java 컬렉션과 연결

### 시간복잡도 감각 (자주 틀리는 것 위주)

| 자료구조 | 조회 | 삽입/삭제 | 비고 |
|---|---|---|---|
| Array (`ArrayList`) | O(1) index 접근 | O(n) (중간 삽입 시 shift) | 끝에 추가는 amortized O(1) |
| LinkedList | O(n) | O(1) (노드 참조 있을 때) | Java `LinkedList`는 실무에서 거의 안 씀 — 캐시 지역성 나쁨 |
| HashMap | O(1) 평균, O(n) 최악(해시 충돌) | O(1) 평균 | Java 8+부터 버킷 내 충돌이 많으면 리스트→트리(Red-Black Tree)로 전환, O(n)→O(log n) |
| TreeMap | O(log n) | O(log n) | Red-Black Tree 기반, **정렬 순서 유지**가 필요할 때만 |
| Stack/Queue | O(1) push/pop | O(1) | 재귀↔반복 변환, BFS/DFS에서 필수 |

**흔한 실수**: "HashMap은 항상 O(1)"이라고만 답하면 감점 요인. 해시 충돌 시 최악의 경우와 Java 8의 트리화 개선까지 언급하면 깊이가 드러남.

### HashMap 내부 동작 (거의 항상 나오는 단골 질문)

```
1. key.hashCode() 호출 → 해시값 산출
2. 해시값을 버킷 배열 크기로 나눠(& 연산) 버킷 인덱스 결정
3. 같은 버킷에 이미 값이 있으면 (충돌) → 연결 리스트로 체이닝, 리스트 길이 8 이상이면 트리로 변환
4. key.equals()로 실제 동일 키인지 최종 비교
```
- **`hashCode()`와 `equals()`를 같이 오버라이드해야 하는 이유**: `equals()`가 true인 두 객체는 `hashCode()`도 같아야 한다는 계약(contract) 때문. 이거 어기면 HashMap/HashSet에서 "분명 넣었는데 못 찾는" 버그가 남.
- 초기 용량/load factor(기본 0.75)를 넘으면 리해싱(resize) 발생 — 이 순간 O(n) 비용이 든다는 것도 언급 가능하면 좋음.

### Tree / Graph 순회

- DFS(전위/중위/후위), BFS는 최소한 화이트보드에 손코드로 짤 수 있어야 함.
- 그래프 탐색: 방문 배열(visited) 없이 짜면 무한루프 — 실무 버그로도 자주 등장(순환 참조 데이터).

### 정렬 알고리즘

- `Collections.sort()` / `Arrays.sort(Object[])`는 내부적으로 **TimSort**(안정 정렬, O(n log n)) 사용. 원시타입 배열(`int[]`)은 **Dual-Pivot QuickSort**(불안정, 평균 O(n log n))라는 차이를 알면 좋음.
- "안정 정렬(stable sort)이 왜 중요한가" — 동일 키를 가진 원소들의 상대 순서를 보존해야 하는 다단계 정렬(예: 이름순 정렬된 걸 부서순으로 다시 정렬)에서 필요.

## 2. 운영체제 — 백엔드 개발자가 실제로 쓰는 부분 위주

### 프로세스 vs 스레드

| | 프로세스 | 스레드 |
|---|---|---|
| 메모리 | 독립된 주소공간 | 같은 프로세스 내 Heap/Stack 중 **Heap 공유**, Stack은 스레드별 독립 |
| 생성 비용 | 무거움 (Context Switch 비용 큼) | 가벼움 |
| 통신 | IPC 필요 (파이프, 소켓, 공유메모리) | 메모리 직접 공유 가능 (그만큼 동기화 문제도 생김) |

- Spring Boot(Tomcat 내장)는 **요청마다 스레드를 하나씩 할당**(Thread-per-request 모델). 이게 07번 문서(동시성)와 08번 문서(대용량 트래픽)로 바로 연결됨 — "왜 스레드 풀 크기가 성능에 영향을 주는가"를 설명할 수 있어야 함.

### 동시성 문제 — Race Condition / Deadlock

```java
class Counter {
    private int count = 0;
    void increment() { count++; } // 원자적이지 않음! read-modify-write 3단계
}
```
- 여러 스레드가 `increment()`를 동시 호출하면 값이 누락될 수 있음(race condition). 해결책: `synchronized`, `AtomicInteger`, 락 기반 동기화.
- **Deadlock 발생 4조건**: 상호 배제, 점유와 대기, 비선점, 순환 대기. 이 중 하나만 깨도 데드락 방지 가능 (예: 락 획득 순서를 항상 동일하게 강제 → 순환 대기 제거).
- Spring 맥락: DB 커넥션 풀 고갈, 분산 락(Redis) 타임아웃 미설정으로 인한 영구 대기 등이 실무 데드락/기아(starvation) 사례.

### 메모리 관리 — Java 관점 (JVM)

- **Heap**: 객체가 생성되는 곳. Young(Eden+Survivor) / Old 영역으로 나뉨.
- **GC(Garbage Collection)**: "대부분의 객체는 금방 죽는다(weak generational hypothesis)"는 가정 하에 Young 영역을 자주 청소(Minor GC), Old는 드물게(Major/Full GC).
- Full GC가 자주 일어나면 서비스 응답 지연(STW, Stop-The-World) — "대용량 트래픽 최적화" 문맥에서 GC 튜닝/메모리 누수 탐지 경험이 있으면 강조할 포인트.
- **Stack**: 메서드 호출 프레임, 지역변수. 스레드마다 독립 — 재귀 깊이가 너무 크면 `StackOverflowError`.

### 가상 메모리 / 캐시 (컴퓨터구조와 겹치는 부분)

- 페이지 테이블, TLB는 이름 정도만 알아도 충분한 수준일 가능성 높음 (백엔드 실무 질문보다 CS 원론 질문으로 나올 때 대비).

## 3. 컴퓨터 아키텍처 — 백엔드에서 체감하는 부분

### 메모리 계층 (Cache Hierarchy)

```
CPU Register (가장 빠름, 가장 작음)
  ↓
L1/L2/L3 Cache
  ↓
Main Memory (RAM)
  ↓
Disk/SSD (가장 느림, 가장 큼)
```
- **캐시 지역성(Locality)**: 배열이 LinkedList보다 순회가 빠른 진짜 이유 — 배열은 메모리에 연속 배치되어 캐시 라인에 한 번에 여러 원소가 올라옴(공간 지역성). LinkedList는 노드가 흩어져 있어 매번 캐시 미스 발생 가능성 높음.
- 이 개념이 "왜 `ArrayList`가 실무 기본 선택인가"의 근거가 됨.

### CPU와 I/O 바운드

- 백엔드 서비스는 대부분 **I/O 바운드**(DB 쿼리, 외부 API 호출 대기)다. 스레드가 I/O 대기 중엔 CPU를 안 쓰므로, 스레드를 무한정 늘리면 컨텍스트 스위칭 비용만 늘고 처리량은 안 늘어남 — 이게 09번 문서(WebFlux/Reactive, non-blocking I/O)로 이어지는 핵심 동기.

## 4. 30분 스크리닝 대비 체크리스트

- [ ] HashMap 내부 동작을 그림 없이 말로 설명 가능
- [ ] `equals()`/`hashCode()` 계약을 설명 가능
- [ ] 프로세스/스레드 차이 + Thread-per-request 모델 설명 가능
- [ ] Race condition 예시 + 해결책(synchronized/Atomic/락) 즉답 가능
- [ ] Deadlock 4조건 즉답 가능
- [ ] Java Heap 구조(Young/Old) + GC 개념 설명 가능
- [ ] 배열이 LinkedList보다 빠른 이유(캐시 지역성)를 하드웨어 관점에서 설명 가능
- [ ] Big-O로 자료구조별 조회/삽입 비교 즉답 가능

## 실습 방법

1. 체크리스트를 소리 내어 설명해보기(글로 아는 것과 말로 설명하는 것은 다른 능력) — 막히는 항목만 다시 찾아보기
2. `HashMap`/`ArrayList` 동작을 확인하는 짧은 Java 코드를 직접 짜서 `hashCode()`/`equals()` 오버라이드 안 했을 때 버그 재현해보기
3. LeetCode Easy~Medium 몇 개로 자료구조 감각 확인 (목표는 "풀이"보다 "복잡도 즉답")

## 참고 키워드 (검색용)
- HashMap internal implementation Java 8 treeify
- hashCode equals contract Java
- Thread-per-request model Tomcat
- JVM heap generational garbage collection
- cache locality array vs linked list performance
- deadlock four necessary conditions
