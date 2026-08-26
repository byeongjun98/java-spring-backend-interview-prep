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
| document 동시편집 | 04, 07 | 낙관적 락(`@Version`) 기반 버전 관리 | 진행 중 |
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

1. `docs/00`~`11` 전체 작성 완료 — 순서대로 읽고 각 문서 끝 "실습 방법"/체크리스트 직접 수행하며 소화.
2. `project/backend-prep`에서 04번 문서 실습 이어서 진행 — 낙관적 락 충돌 재현(Part E).
3. 멱등성(03번)·재고 락/분산 락(04·05번)·Saga(06번)는 예전에 다른 프로젝트에서 다뤘지만 제대로 이해한 게 아니었어서, 이 저장소에서 새로 배우는 셈 치고 각 문서 실습을 직접 손으로 구현해보며 확인.
4. 학습하며 막히거나 더 깊게 파야 할 주제 생기면 해당 번호 문서에 이어서 보강.
