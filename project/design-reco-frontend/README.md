# design-reco-frontend

`project/design-reco-service`(WebFlux 백엔드)를 그대로 호출하는 데모 화면. 프레임워크(React 등) 없이
순수 HTML/CSS/바닐라 JS + `fetch`만 씀 — 빌드 도구 없이 정적 파일 그대로 서빙.

이 프론트 자체가 공부 대상은 아님. 목적은 "화면의 버튼 하나가 어느 API를 거쳐 백엔드 어느 코드까지
가는지"를 프레임워크 없이 투명하게 보면서, 실제 서비스가 클라이언트-서버로 어떻게 엮이는지 감을
잡는 것 — 자세한 학습 순서는 `project/design-reco-service/STUDY_GUIDE.md` 6단계 참고.

## 구조

- `index.html` — 화면 골격 (locale/userId 컨트롤, 템플릿 목록, 유저 추천, API 로그)
- `style.css` — 스타일
- `app.js` — 전부. `api.*` 함수가 백엔드 엔드포인트와 1:1로 매핑됨:

  | 화면 동작 | `app.js` 함수 | 백엔드 |
  |---|---|---|
  | 템플릿 목록 로드 | `api.listAssets` | `GET /assets` |
  | "비슷한 템플릿" | `api.getItemRecommendations` | `GET /assets/{id}/recommendations` |
  | "보기"/"사용" 버튼 | `api.recordEvent` | `POST /events` |
  | "회원님을 위한 추천" | `api.getUserRecommendations` | `GET /users/{userId}/recommendations` |

## 실행 방법

1. 백엔드 먼저 기동 (`project/design-reco-service/README.md` 참고 — Postgres/Redis 필요)
2. 이 디렉터리에서 정적 서버 아무거나 (빌드 불필요, 그냥 파일 서빙만 하면 됨):
   ```
   cd project/design-reco-frontend
   python3 -m http.server 5500
   ```
3. 브라우저로 `http://localhost:5500` 접속

포트를 5500이 아닌 다른 걸로 띄우면 CORS가 막힘 — 백엔드 `WebConfig`(`com.example.designreco.web.WebConfig`)에
`localhost:5500`/`127.0.0.1:5500`만 허용해뒀기 때문. 다른 포트를 쓰고 싶으면 그 파일의
`allowedOrigins`도 같이 바꿀 것.

## 왜 이렇게 만들었나

- **프레임워크 없음**: React/Vue를 썼다면 "상태 변경 → 자동 리렌더" 배관이 프레임워크 내부로
  숨어버림. 여기선 `refreshAll()`/`loadUserRecommendations()`를 직접 호출하는 걸 눈으로 볼 수 있게
  일부러 안 씀 — 학습용 데모라 "마법처럼 되는" 부분을 최소화.
- **이벤트 위임**: 템플릿 카드가 fetch 결과에 따라 매번 새로 그려지는데(`innerHTML` 재할당), 카드마다
  리스너를 새로 다는 대신 부모 `#assets-grid`에 리스너 하나만 걸고 `event.target`으로 어떤 버튼이
  눌렸는지 판단 (`app.js`의 `addEventListener("click", ...)` 부분).
- **API 로그 패널**: 화면 조작 → 실제로 나가는 HTTP 요청을 바로 옆에서 볼 수 있게. 브라우저 개발자
  도구 Network 탭을 열 필요 없이 "지금 이 클릭이 백엔드에 뭘 보냈는지"가 화면에 그대로 찍힘.
