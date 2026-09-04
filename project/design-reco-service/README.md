# design-reco-service

`project/backend-prep`(협업 문서 편집기 — member/document/payment/inventory/search/MSA)와 겹치지 않는
공고 영역만 골라서 만든 별도 토이 프로젝트: **AI 추천 시스템 + 글로벌 트래픽/다국어 인프라 + Spring WebFlux(Reactive)**.

이 프로젝트는 Claude가 공고 내용을 근거로 아키텍처를 추론해 설계·구현을 주도한 것 — `backend-prep`처럼
가이드만 받고 직접 짜는 학습용이 아니라, 완성된 구현을 보고 설계 이유를 파악/설명하는 용도.

소스코드 읽는 순서/공부 방법은 [`STUDY_GUIDE.md`](./STUDY_GUIDE.md) 참고. 이 백엔드를 실제로 호출하는
프론트엔드는 [`project/design-reco-frontend/`](../design-reco-frontend/).

## 재개 시 먼저 볼 것 (2026-09-04 기준)

**구현 상태: v1 완결.** 백엔드(TODO 1~6번, 아래 표) + 프론트(`design-reco-frontend`) + 전체 코드
설명 주석 + 브라우저 E2E 검증(claude-in-chrome으로 locale 전환/이벤트 기록/item·user 추천 전부 실클릭
확인, 콘솔 에러 없음)까지 이번 세션에서 끝냄. **당장 이어서 짤 미완성 코드는 없음** — 다음 세션은
"학습(STUDY_GUIDE.md 읽기)" 아니면 아래 "다음에 해볼 만한 것" 중에서 고르는 선택 지점.

**서비스 기동 상태**: 세션 종료하면서 백엔드(8080)·프론트(5500) 프로세스는 껐음. 로컬 PostgreSQL
(`design_reco` DB, `schema.sql` 적용된 상태 그대로 유지 — 재적용 불필요, 더미데이터도 남아있을 수
있음), Redis(brew로 설치됨, 이 세션에서 수동 daemonize로 띄운 상태였음 — 재부팅했으면 꺼졌을 것,
"Redis 설치·실행" 절 참고)는 인프라라 손 안 댐. **다시 켤 땐 "실행 방법" 절 그대로** —
`design_reco` DB/schema는 이미 있으니 1번(createdb)은 건너뛰어도 됨, 이미 있으면 에러 나는 정도.

**다음 세션 시작할 때**: `STUDY_GUIDE.md` 순서대로 코드 읽기 시작하면 됨 — 별도 "다음 구현 후보"를
고를 필요 없이 그냥 공부 진도만 이어가는 국면.

### 다음에 해볼 만한 것 (전부 선택 사항, 우선순위 없음)

- **STUDY_GUIDE.md 4단계에 적어둔 캐시 키 버그를 직접 찾아 고치기** — 지금 item-based 추천 캐시 키가
  `reco:asset:{id}:{limit}`라 locale이 안 들어감. 즉 ko로 먼저 호출해서 캐시되면, 그다음 5분 안에 ja로
  같은 에셋을 호출해도 캐시에 저장된 ko 시절 title/description이 그대로 나갈 여지가 있음(추천 대상
  id 목록 자체는 언어 무관이라 캐시해도 되지만, 응답 조립 단계 캐싱이 아니라서 실제로 title이 섞이진
  않음 — 직접 재현해서 진짜 버그인지 확인해보는 것부터가 연습).
- **콜드 스타트 처리** — `UserController`가 지금은 이력 없으면 그냥 빈 배열. "이력 없는 유저에게 뭘
  보여줄지"(인기순 폴백 등)를 직접 설계해서 붙여보기.
- **프론트 확장** — `design-reco-frontend`에 검색/카테고리 필터 추가해보기. 프론트 자체 학습이 목적은
  아니지만, 백엔드에 없는 기능을 요구하다 보면 "이 API가 왜 이렇게 생겼는지" 역으로 이해가 깊어짐.
- **pgvector로 교체** — `CosineSimilarity`/`schema.sql`의 `// ponytail:` 주석에 적어둔 업그레이드
  경로. pgvector 확장 설치 후 `embedding` 컬럼을 `vector` 타입으로, 쿼리를 `ORDER BY embedding <=> :target`으로.

## 시나리오

디자인 SaaS에서 사용자가 템플릿 작업 중 "이거랑 비슷한 템플릿" 추천을 받는 백엔드.
item-based(에셋↔에셋 유사도)와 user-based(유저의 최근 관심사 기반) 두 방식을 모두 구현.

## 아키텍처

- **스택**: Java 17, Spring WebFlux, R2DBC + PostgreSQL, Redis(Reactive), Lombok.
  (backend-prep은 MVC + JPA — 스택도 의도적으로 다르게)
- **도메인**
  - `Asset` — 템플릿 메타데이터 + 임베딩 벡터(toy, 8차원)
  - `AssetTranslation` — `(asset_id, locale)` 단위 다국어 title/description. R2DBC는 JPA와 달리 연관관계 매핑이
    없어서, 조회 시 `AssetResponseAssembler`가 reactive `flatMap`으로 두 리포지토리를 직접 조립함.
  - `UserEvent` — 조회(VIEW)/사용(USE) 이력. user-based 추천의 입력.
- **다국어**: `Accept-Language` 헤더를 `java.util.Locale.LanguageRange`로 파싱해 지원 locale(ko/en/ja) 중
  최우선 순위를 고름 (`AcceptLanguageResolver`). 매칭되는 번역이 없으면 `en`으로 폴백.
- **추천 알고리즘**: pgvector 확장 없이 애플리케이션 레벨 코사인 유사도(`CosineSimilarity`)로 시작.
  `// ponytail:` 주석으로 남겨둔 이유 — 로컬에 pgvector 설치를 강제하지 않으려는 목적. 카탈로그 규모가
  커져서 전수 스캔이 느려지면 pgvector `<=>` 연산자로 교체.
  - item-based (`GET /assets/{id}/recommendations`): 대상 에셋의 임베딩과 나머지 전체의 코사인 유사도 Top-N.
  - user-based (`GET /users/{userId}/recommendations`): 최근 조회/사용한 에셋 최대 10건의 임베딩 centroid(`EmbeddingMath`)를 구해, 그 centroid와 가장 비슷하면서 아직 안 본 에셋 Top-N. 이력이 없으면 빈 배열(콜드 스타트는 범위 밖).
- **캐싱**: item-based 추천 결과(에셋 id 목록)를 Redis에 5분 TTL로 캐싱 — "글로벌 트래픽" 요구사항을
  캐시 계층으로 흉내. `ReactiveStringRedisTemplate`은 Spring Boot 자동 설정 빈 그대로 사용.
- **검증**: `RestControllerAdvice`가 `IllegalArgumentException`을 400으로 매핑 (잘못된 `eventType` 등).

### 실제로 발견하고 고친 버그 (WebFlux 학습 포인트)

로컬에서 직접 띄워서 `curl`로 확인하다가 발견: 추천 결과를
`Flux.fromIterable(정렬된_id_목록).flatMap(assetRepository::findById)`로 조립했더니, 코사인 유사도로
정렬한 순서가 응답에서 뒤섞임. `flatMap`은 내부 Publisher가 **완료되는 순서**대로 흘려보내지, 입력 순서를
보존하지 않음 (DB 조회가 몇 ms씩 어긋나면 순서가 매번 바뀜) — Redis에 저장된 원본 순서("2,5")와 실제 API
응답 순서("5,2")가 다른 걸로 확인. `flatMapSequential`로 바꿔서 해결 (`AssetController`, `UserController`).
**"순위/순서가 의미 있는 리스트를 reactive 파이프라인으로 조립할 때는 flatMap 대신 flatMapSequential/concatMap"**
— WebFlux 우대사항 관련해서 설계 면접에서 나올 법한 디테일.

## TODO 진행 상황

| # | 내용 | 상태 |
|---|---|---|
| 1 | Gradle 스캐폴딩 (WebFlux/R2DBC, wrapper, schema.sql) | 완료 |
| 2 | Asset+AssetTranslation 엔티티/리포지토리, 더미데이터, 로케일 인식 GET API | 완료 |
| 3 | 임베딩 + 코사인 유사도 유틸 + 단위테스트 | 완료 |
| 4 | 추천 API (`GET /assets/{id}/recommendations`) | 완료 |
| 5 | UserEvent 기록 API(`POST /events`) + 최근 이력 반영 user-based 추천 | 완료 |
| 6 | Redis 캐싱 레이어 (item-based 추천 결과, 5분 TTL) | 완료 |

로컬 PostgreSQL(`design_reco` DB, schema 적용됨) + Redis(`brew install redis`)로 실제 기동해서
위 시나리오 전부(다국어 조회, item/user-based 추천, 캐시 hit/miss, 잘못된 입력 400, 이력 없는 유저 빈 배열)
`curl`로 직접 확인 완료. 단위테스트 14개 전부 통과(`DesignRecoServiceApplicationTests` 포함, DB 필요).

## 실행 방법

1. 로컬 PostgreSQL에 DB 생성 후 `schema.sql` 적용
   ```
   createdb design_reco
   psql -d design_reco -f schema.sql
   ```
2. Redis 필요 (item-based 추천 캐싱, `GET /assets/{id}/recommendations`). 아래 "Redis 설치·실행" 참고.
3. `.env.example` → `.env` 복사 후 `DB_USERNAME`/`DB_PASSWORD` 채우기 (커밋 안 됨)
4. Java 17 필요 (repo 루트 `.java-version` 참고)
   ```
   export $(grep -v '^#' .env | xargs)
   ./gradlew bootRun
   ```
   `./gradlew`가 "Gradle requires JVM 17 or later"로 실패하면 `gradle.properties.example`을
   `gradle.properties`로 복사해서 자기 JDK 17 경로로 채울 것.

### Redis 설치·실행

이 저장소를 검증하는 과정에서 로컬(macOS, Homebrew)에 새로 설치·기동했음 — 아래는 그 기록 + 앞으로 쓸 때 참고용 가이드.

**설치** (한 번만)
```
brew install redis
```

**실행 — 두 가지 방법 중 하나만 하면 됨**

- 그때그때 켜고 끄기 (백그라운드 데몬으로 띄우고, 필요할 때 직접 끔):
  ```
  redis-server --daemonize yes                 # 기본 포트 6379로 백그라운드 기동
  redis-cli ping                                # PONG 나오면 정상
  redis-cli shutdown                            # 끌 때
  ```
- 로그인할 때마다 자동 기동 (백그라운드 서비스로 등록):
  ```
  brew services start redis                     # 등록 + 즉시 기동, 로그아웃/재부팅 후에도 자동 실행
  brew services list | grep redis                # 상태 확인
  brew services stop redis                       # 자동 기동 해제
  ```

**지금 이 머신 상태**: `brew install redis`로 설치됨, `redis-server --daemonize yes`로 기본 포트(6379)에
떠 있음 — `brew services`로 등록한 게 **아니라서** 재부팅하면 꺼짐. 계속 자동으로 켜져 있길 원하면 위
`brew services start redis`로 다시 등록할 것. `redis-cli ping` → `PONG`이면 현재도 살아있는 상태.

**앱 쪽 연결 설정**: `application.properties`의 `spring.data.redis.host`/`port`가 기본 `localhost:6379`를
바라봄. `REDIS_HOST`/`REDIS_PORT` 환경변수로 재정의 가능 (`.env`에 추가하면 `export $(grep -v '^#' .env | xargs)`로 같이 로드됨).
Redis가 안 떠 있어도 앱 자체는 뜸(Lettuce가 첫 명령 호출 전엔 접속 안 함) — 다만 `/assets/{id}/recommendations`를
호출하는 순간 연결 실패로 500이 남.

**캐시 들여다보기 / 비우기** (디버깅용)
```
redis-cli get "reco:asset:1:3"    # AssetController가 저장한 캐시 값 직접 확인
redis-cli flushdb                  # 캐시 전부 비우기 (추천 결과가 바뀌었는데 옛날 값이 나올 때)
```

기동 후 앱이 더미 에셋 5개(포스터 2 / 카드 2 / 프레젠테이션 1)를 자동 시딩함.

```
curl http://localhost:8080/assets
curl -H "Accept-Language: ja" http://localhost:8080/assets/1

curl "http://localhost:8080/assets/1/recommendations?limit=2"

curl -X POST http://localhost:8080/events -H "Content-Type: application/json" \
  -d '{"userId":1,"assetId":1,"eventType":"VIEW"}'
curl "http://localhost:8080/users/1/recommendations"
```
