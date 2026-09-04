# 백엔드 개발자 채용 준비

**백엔드 개발자** 채용 준비용 학습 + 토이프로젝트 저장소. 특정 회사명은 의도적으로 밝히지 않음 — 학습 내용과 진행 과정 자체가 핵심.

## 공고 핵심 요약

- **팀**: 웹 기반 디자인 SaaS 백엔드. 대규모 트래픽 처리, 결제/회원/검색 핵심 도메인 설계·운영.
- **주요업무**: 모놀리식→MSA 전환, 실시간 저장/동시 편집, AI 추천 시스템, 글로벌 트래픽/다국어 인프라.
- **자격요건**: Java/Spring/AWS 백엔드 경험, RESTful API 설계, DB 쿼리 최적화, 대용량 트래픽/분산 처리, CS 기초(자료구조/OS/컴퓨터구조/OOP), AI 도구 활용·검증 경험.
- **우대사항**: Spring WebFlux(Reactive), 디자인패턴/아키텍처, 클린코드/리팩토링, 커뮤니케이션, 팀 AI 활용 문화 전파.
- **기술스택**: Java, Spring MVC / PostgreSQL, MongoDB, DynamoDB, Redis, Vector DB / AWS OpenSearch / Datadog, CloudWatch / Databricks / AWS.
- **전형**: 서류 → 기술 스크리닝(CS 기초, 온라인 30분) → 1차 인터뷰(설계 면접, 50분) → 2차 인터뷰 → 처우협의.

## 구조

```
docs/     이론 학습 자료 (md, 번호 순서로 학습)
project/  토이프로젝트 (Java/Spring, 공고 핵심 업무 미러링)
```

`project/design-reco-service/`: `backend-prep`과 겹치지 않는 공고 영역(AI 추천/다국어/WebFlux)을
Claude가 설계·구현 주도로 만드는 별도 토이 프로젝트. 진행 상황·소스코드 학습 순서는 해당 디렉터리
`README.md`/`STUDY_GUIDE.md` 참고. `project/design-reco-frontend/`는 이 백엔드를 호출하는 데모 프론트엔드
(프레임워크 없는 바닐라 JS) — 클라이언트-서버가 실제로 어떻게 엮이는지 보는 용도.

## 학습 로드맵 (docs/)

| 파일 | 주제 | 공고 대응 | 상태 |
|---|---|---|---|
| 00-overview.md | 갭 분석 + 학습 로드맵 | 전체 | 작성 완료 |
| 01-oop-design-patterns.md | OOP 원칙, 디자인패턴 실전 적용 | 자격요건(OOP), 우대(디자인패턴) | 작성 완료 |
| 02-cs-fundamentals.md | 자료구조, OS, 컴퓨터구조 (기술 스크리닝 대비) | 자격요건(CS 기초) | 작성 완료 |
| 03-spring-rest-api-design.md | Spring MVC, RESTful API 설계 원칙, 멱등키 설계 | 자격요건(RESTful API) | 작성 완료 |
| 04-jpa-db-query-optimization.md | JPA/Hibernate, PostgreSQL 쿼리 분석/최적화, 재고 락/레이스 컨디션 | 자격요건(DB 쿼리 최적화) | 작성 완료 |
| 05-nosql-redis-mongo-dynamo.md | Redis 캐싱, MongoDB, DynamoDB 활용 패턴, 분산 락 | 기술스택 | 작성 완료 |
| 06-msa-transition.md | 모놀리식 → MSA 분리 설계, 서비스 간 통신 | 주요업무(MSA 전환) | 작성 완료 |
| 07-concurrency-realtime-collab.md | 동시성 제어, 낙관적/비관적 락, 실시간 동시편집(OT/CRDT 개념) | 주요업무(실시간 동시편집) | 작성 완료 |
| 08-high-traffic-distributed.md | 대용량 트래픽 처리, 분산 처리, 캐싱/큐잉 전략 | 자격요건(대용량 트래픽) | 작성 완료 |
| 09-webflux-reactive.md | Spring WebFlux, Reactive Programming | 우대사항 | 작성 완료 |
| 10-search-opensearch.md | 검색 엔진 기초, OpenSearch | 주요업무(검색 도메인) | 작성 완료 |
| 11-ai-tooling-workflow.md | AI 도구 워크플로우 도입 + 결과 비판적 검증 | 자격요건(AI 도구 활용) | 작성 완료 |

**학습 순서 제안**: 01(OOP/패턴) → 02(CS 기초, 기술스크리닝 대비 우선순위 높음) → 03(REST/Spring) → 04(DB) → 05(NoSQL) → 06(MSA) → 07(동시성/동시편집) → 08(트래픽) → 09(WebFlux, 여유 있으면) → 10(검색) → 11(AI 워크플로우, 이력서/인터뷰 스토리텔링용).

## 토이 프로젝트 (project/backend-prep)

**가제**: "미니 협업 문서 편집기 백엔드" — 공고 핵심 업무("결제/회원/검색", "실시간 저장 및 동시 편집", "MSA 전환")를 축소 미러링. Java 17, Spring Boot, PostgreSQL.

| 도메인 | 대응 문서 | 내용 | 상태 |
|---|---|---|---|
| member | 03 | 회원 엔티티, REST API | 완료 (JPA 엔티티 + 더미데이터) |
| document | 04 | 문서 CRUD, owner 연관관계(LAZY) | 완료 |
| document 조회 성능 | 04 | 인덱스 유무 비교(`EXPLAIN ANALYZE`), N+1 재현 → Fetch Join 해결 | 완료 |
| document 동시편집 | 04, 07 | 낙관적 락(`@Version`) 기반 버전 관리 — 충돌 재현 테스트(순차 시뮬레이션 + `ExecutorService` 동시성 버전) 완료 | 완료 |
| realtime-edit | 07 | 동시 편집 충돌 처리, Redis 세션/락 | 예정 |
| payment (멱등성) | 03 | 멱등키 헤더 기반 중복 결제 방지 — DB 유니크 제약으로 동시 요청 race까지 막기 | 예정 |
| inventory (재고 락) | 04, 05 | 동시 차감 시 오버셀 재현 → 원자적 UPDATE/비관적 락으로 해결, Redis 분산 락 fallback | 예정 |
| search | 10 | 문서 제목/내용 검색 (OpenSearch 또는 DB full-text로 시작) | 예정 |
| gateway/msa | 06 | 단일 서비스로 시작 → 도메인별 서비스 분리 실습 | 예정 |

`project/playground/`: 문서 01(OOP/디자인패턴) 실습용 독립 예제 (빌드 도구 없이 `javac`/`java`로 바로 실행).

### 실행 방법

1. 로컬 PostgreSQL에 DB 생성 후 `project/backend-prep/schema.sql` 적용
   ```
   createdb backend_prep
   psql -d backend_prep -f project/backend-prep/schema.sql
   ```
2. DB 접속 정보는 `.env`로 관리 (커밋 안 됨 — `.gitignore`에 등록돼 있음)
   ```
   cd project/backend-prep
   cp .env.example .env
   # .env 열어서 DB_USERNAME/DB_PASSWORD를 자기 값으로 채우기
   ```
3. Java 17 필요 — `project/backend-prep`엔 `.java-version`(jenv) 있음, 없으면 직접 JDK 17 설치
   ```
   export $(grep -v '^#' .env | xargs)   # .env 내용을 현재 쉘 환경변수로 로드
   ./gradlew bootRun
   ```
   `./gradlew`가 "Gradle requires JVM 17 or later"로 실패하면 `gradle.properties.example`을 `gradle.properties`로 복사해서 자기 JDK 17 경로로 채울 것 (개인 경로라 gitignore됨).

## 다음 단계

### 병행 트랙: project/design-reco-service (2026-09-04 기준)

이 섹션(아래 전부)은 `backend-prep` 가이드 학습 트랙 얘기 — 그거랑 별도로 `project/design-reco-service`
(+ `project/design-reco-frontend`) 트랙이 하나 더 생겼음. 공고 영역 중 backend-prep이 안 건드리는
AI 추천/다국어/WebFlux를 Claude가 설계·구현 주도로 완성한 것 (v1 완결, 미완성 코드 없음). 재개 시
먼저 볼 것/다음에 해볼 것은 `project/design-reco-service/README.md`의 "재개 시 먼저 볼 것" 절에 따로
정리해둠 — 아래 backend-prep 관련 메모와는 독립적으로 진행하면 됨.

### 재개 시 먼저 볼 것 (2026-09-01 기준, backend-prep 트랙)

자바 기본 문법 복습하러 잠시 이탈 — 복습 끝나고 돌아오면 아래 "다음 실습 후보"에서 하나 골라 이어서 진행. 지금까지 학습 스타일(코드 바로 안 써주고 가이드/틀만 제공, 재요청 시에만 작성 — `map`/`stream`/람다 등 기초 문법도 당연시 말고 주석으로 설명)은 계속 유지.

### 지금까지 완료된 것

- `docs/00`~`11` 전체 작성 완료, 1회독 마침.
- `project/backend-prep` 토이프로젝트: Spring Boot(Java 17) + PostgreSQL, member/document 도메인, N+1 → Fetch Join 해결, 인덱스 성능 비교까지 완료.
- 04번 문서 Part E(낙관적 락) 완료 — `Document.version`(`@Version`) 추가, `changeContent()` 도메인 메서드, `DocumentService.updateContent()`(`@Transactional` 필수 이유까지 주석 설명). 충돌 재현 테스트 2종(`DocumentServiceTest`):
  - `낙관적_락_충돌_재현`: `entityManager.clear()`로 두 "사용자"의 서로 다른 읽기를 순차 시뮬레이션.
  - `낙관적_락_동시성_재현`: `ExecutorService` + `CountDownLatch`로 진짜 스레드 2개 동시 실행.
- 이 과정에서 `build.gradle`에 `spring-boot-starter-test`가 누락돼 테스트 자체가 컴파일 안 되던 문제 발견/수정함 (있어야 정상인 걸 놓쳤던 것 — 다음에 새 테스트 모듈 추가할 때도 의존성부터 확인할 것).
- `DocumentController` 엔드포인트 네이밍 정리: `/document`, `/documents` → 실제 반환값(owner 이름)에 맞게 `/documents/{id}/owner`, `/documents/owners`로 변경(경로 변수 방식).

### 다음 실습 후보 (07번 문서 — 아직 뭐부터 할지 선택 안 함)

07번 문서(`docs/07-concurrency-realtime-collab.md`) 실습 방법 3개 중 1번(낙관적 락 충돌 재현)은 위에서 완료. 남은 것:

1. **요소 단위 버전 관리로 스키마 확장** — 지금 `Document`는 `content` 통짜 하나에 버전 하나. 07번 문서 3장의 "요소(element) 단위로 독립 버전 관리" 아이디어를 실제 엔티티/테이블로 설계해보기 (예: `DocumentElement`/`Block` 테이블 분리, 연관관계, 요소별 `@Version` 여부).
2. **05번 문서(`05-nosql-redis-mongo-dynamo.md`) 재확인** — 07번 4단계(Redis Pub/Sub, presence/락 관리)로 넘어가기 전에 Redis 기초를 다시 볼지 판단 필요. 아직 프로젝트에 Redis 자체를 안 붙임.
3. **OT vs CRDT 자료조사 + 한 문단 요약** — Google Docs(OT)/Figma(CRDT) 각각 어떤 방식 쓰는지 아티클 찾아 읽고 정리. 코드 아니라 조사 작업.

이 중 뭐부터 할지 다음 세션 시작할 때 정하면 됨 (기본 추천: 1번부터 — 지금까지 해온 JPA 엔티티/스키마 설계 흐름과 가장 자연스럽게 이어짐).

### 계속 남아있는 배경 목표

멱등성(03번)·재고 락/분산 락(04·05번)·Saga(06번)는 예전에 다른 프로젝트에서 다뤘지만 제대로 이해한 게 아니었어서, 이 저장소에서 새로 배우는 셈 치고 각 문서 실습을 직접 손으로 구현해보며 확인 — 위 도메인 표의 `payment`(멱등성), `inventory`(재고 락)가 여기 해당, 아직 "예정" 상태.
