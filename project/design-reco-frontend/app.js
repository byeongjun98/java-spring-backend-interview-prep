// 프레임워크(React 등) 없이 fetch + DOM API만으로 짬 — 이 화면 자체를 공부하는 게 목적이 아니라
// "화면 동작 하나가 백엔드 API 하나로 이어진다"는 배관을 최대한 투명하게 보여주는 게 목적이라,
// 프레임워크가 감춰버리는 부분(요청 언제 나가는지, 상태 갱신을 언제 트리거하는지) 없이 그대로 씀.

const API_BASE = "http://localhost:8080";

// 화면 전역 상태. React 없이 순수 JS로 만들다 보니 "상태가 바뀌면 다시 그린다"를 직접 호출해야 함
// (프레임워크였다면 상태 변경 시 자동 리렌더가 되지만, 여기선 setLocale/setUserId 안에서 수동으로 트리거).
const state = {
	locale: "ko",
	userId: 1,
};

// ---- API 클라이언트: 백엔드 엔드포인트 하나당 함수 하나 ----
// design-reco-service 쪽 컨트롤러 메서드와 1:1로 짝지어져 있음(AssetController, UserController 참고).
const api = {
	listAssets: () => request("GET", "/assets"),
	getItemRecommendations: (assetId, limit = 3) => request("GET", `/assets/${assetId}/recommendations?limit=${limit}`),
	getUserRecommendations: (userId, limit = 3) => request("GET", `/users/${userId}/recommendations?limit=${limit}`),
	recordEvent: (userId, assetId, eventType) =>
		request("POST", "/events", { userId, assetId, eventType }),
};

async function request(method, path, body) {
	log(`${method} ${path}${body ? " " + JSON.stringify(body) : ""}`);
	const res = await fetch(API_BASE + path, {
		method,
		// Accept-Language는 CORS-safelisted 헤더라 이 값만으로는 프리플라이트가 안 붙지만,
		// POST + Content-Type: application/json 조합은 브라우저가 자동으로 OPTIONS 프리플라이트를
		// 먼저 보냄 — 백엔드 WebConfig(CORS 설정)가 이걸 허용해줘야 실제 요청이 통과함.
		headers: {
			"Accept-Language": state.locale,
			...(body ? { "Content-Type": "application/json" } : {}),
		},
		body: body ? JSON.stringify(body) : undefined,
	});
	if (!res.ok) {
		const message = await res.text();
		log(`  → ${res.status} ${message}`);
		throw new Error(`${res.status}: ${message}`);
	}
	// POST /events는 본문 없는 201 Created(ResponseEntity<Void>) — 파싱할 JSON이 없음.
	const hasBody = res.headers.get("content-length") !== "0";
	return hasBody ? res.json() : null;
}

function log(line) {
	const el = document.getElementById("log");
	el.textContent += line + "\n";
	el.scrollTop = el.scrollHeight;
}

// ---- 렌더링 ----

function assetCardHtml(asset) {
	return `
    <div class="card" data-asset-id="${asset.id}">
      <div class="category">${asset.category}</div>
      <div class="title">${escapeHtml(asset.title)}</div>
      <div class="description">${escapeHtml(asset.description ?? "")}</div>
      <div class="actions">
        <button data-action="view">보기</button>
        <button data-action="use">사용</button>
        <button data-action="toggle-reco">비슷한 템플릿</button>
      </div>
      <div class="reco-panel"><em>불러오는 중...</em></div>
    </div>
  `;
}

function recoListHtml(assets) {
	if (assets.length === 0) return "<span>추천 결과 없음</span>";
	return `<ul>${assets.map((a) => `<li>${escapeHtml(a.title)} <span class="hint">(${a.category})</span></li>`).join("")}</ul>`;
}

function escapeHtml(str) {
	const div = document.createElement("div");
	div.textContent = str;
	return div.innerHTML;
}

async function loadAssets() {
	const grid = document.getElementById("assets-grid");
	grid.innerHTML = `<div class="empty">불러오는 중...</div>`;
	try {
		const assets = await api.listAssets();
		grid.innerHTML = assets.length ? assets.map(assetCardHtml).join("") : `<div class="empty">에셋 없음</div>`;
	} catch (err) {
		grid.innerHTML = `<div class="error">불러오기 실패: ${err.message}</div>`;
	}
}

async function loadUserRecommendations() {
	const grid = document.getElementById("user-reco-grid");
	grid.innerHTML = `<div class="empty">불러오는 중...</div>`;
	try {
		const assets = await api.getUserRecommendations(state.userId);
		grid.innerHTML = assets.length
			? assets.map((a) => `<div class="card"><div class="category">${a.category}</div><div class="title">${escapeHtml(a.title)}</div></div>`).join("")
			: `<div class="empty">추천 이력 없음 — 왼쪽에서 "보기"/"사용"을 눌러본 뒤 새로고침</div>`;
	} catch (err) {
		grid.innerHTML = `<div class="error">불러오기 실패: ${err.message}</div>`;
	}
}

// ---- 이벤트 위임: 카드가 동적으로 매번 새로 그려지므로, 카드 하나하나에 리스너를 다는 대신
// 부모(grid)에 한 번만 리스너를 걸고 클릭된 게 어떤 버튼인지 event.target으로 판단 ----
document.getElementById("assets-grid").addEventListener("click", async (event) => {
	const button = event.target.closest("button");
	if (!button) return;
	const card = button.closest(".card");
	const assetId = Number(card.dataset.assetId);

	if (button.dataset.action === "view" || button.dataset.action === "use") {
		const eventType = button.dataset.action === "view" ? "VIEW" : "USE";
		await api.recordEvent(state.userId, assetId, eventType);
		await loadUserRecommendations(); // 이력이 바뀌었으니 추천도 다시 불러옴
		return;
	}

	if (button.dataset.action === "toggle-reco") {
		const panel = card.querySelector(".reco-panel");
		panel.classList.toggle("open");
		if (panel.classList.contains("open")) {
			panel.innerHTML = "<em>불러오는 중...</em>";
			try {
				const recommended = await api.getItemRecommendations(assetId);
				panel.innerHTML = recoListHtml(recommended);
			} catch (err) {
				panel.innerHTML = `<span class="error">${err.message}</span>`;
			}
		}
	}
});

function refreshAll() {
	loadAssets();
	loadUserRecommendations();
}

document.getElementById("locale-select").addEventListener("change", (event) => {
	state.locale = event.target.value;
	refreshAll(); // locale이 바뀌면 title/description이 그 언어로 다시 와야 하니 전체 재조회
});

document.getElementById("user-id-input").addEventListener("change", (event) => {
	state.userId = Number(event.target.value) || 1;
	loadUserRecommendations();
});

document.getElementById("refresh-btn").addEventListener("click", refreshAll);

refreshAll();
