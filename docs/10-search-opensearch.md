# 10. 검색 엔진 기초 / AWS OpenSearch

공고 인용: 팀 소개 "결제, 회원, 검색 등 서비스의 근간이 되는 핵심 도메인의 비즈니스 로직을 설계하고 운영합니다." 기술스택: AWS OpenSearch.

## 1. 왜 검색에 DB가 아니라 별도 검색 엔진을 쓰는가

```sql
SELECT * FROM templates WHERE title LIKE '%생일 초대장%'; -- RDB의 한계
```
- `LIKE '%...%'`(양쪽 와일드카드)는 인덱스를 못 타서 **풀스캔** — 04번 문서에서 배운 인덱스 원리상, B-Tree 인덱스는 접두사 검색(`'초대장%'`)만 빠르게 처리 가능하고 중간/전체 포함 검색은 못 함.
- 검색 엔진(OpenSearch/Elasticsearch)은 **역색인(Inverted Index)** 구조로 애초에 "단어 → 그 단어를 포함한 문서 목록"을 미리 만들어둬서, 포함 검색/오타 허용/관련도 순 정렬이 빠름. RDB와 근본적으로 다른 자료구조를 쓴다는 게 핵심 차이.

## 2. 역색인(Inverted Index) 개념

```
문서1: "생일 초대장 템플릿"
문서2: "생일 축하 카드"

역색인:
  "생일"   → [문서1, 문서2]
  "초대장" → [문서1]
  "템플릿" → [문서1]
  "축하"   → [문서2]
  "카드"   → [문서2]
```
- 검색어 "생일"이 들어오면 역색인에서 바로 [문서1, 문서2]를 찾음 — 문서 전체를 스캔하지 않음.
- **분석기(Analyzer)**: 원문을 색인 가능한 단위(토큰)로 쪼개는 과정(형태소 분석, 소문자 변환, 불용어 제거 등). 한국어는 조사/어미가 붙기 때문에("초대장을", "초대장이") 영어보다 형태소 분석기(예: 노리(Nori) 분석기)가 더 중요 — "생일 초대장"과 "생일 초대장을"이 같은 검색으로 매칭되게 하는 역할.

## 3. 관련도 스코어링 (왜 검색 결과에 순서가 있는가)

- 단순히 "포함 여부"만 보는 게 아니라, **얼마나 관련 있는지 점수를 매겨 정렬**함. 대표 알고리즘: **TF-IDF**, 최신 OpenSearch/Elasticsearch 기본값은 **BM25**.
  - **TF(Term Frequency)**: 문서 안에 그 단어가 많이 나올수록 관련도 높음.
  - **IDF(Inverse Document Frequency)**: 모든 문서에 흔한 단어("그리고", "템플릿")는 변별력이 낮으므로 가중치를 낮춤, 드문 단어는 가중치를 높임.
  - **BM25**: TF-IDF를 개선 — TF가 일정 수준 이상 커져도 점수가 무한정 오르지 않게 포화(saturation)시키고, 문서 길이를 정규화(긴 문서가 단어를 많이 포함하는 게 당연하므로 불이익 보정).
- 인터뷰 답변 포인트: "검색은 매칭 여부가 아니라 랭킹 문제"라는 걸 이해하고 있다는 것 자체가 차별점.

## 4. OpenSearch 실전 개념

```json
PUT /templates/_doc/1
{
  "title": "생일 초대장 템플릿",
  "category": "invitation",
  "created_at": "2026-01-01"
}

GET /templates/_search
{
  "query": {
    "match": { "title": "생일 초대장" }
  }
}
```
- **샤드(Shard)**: 인덱스를 여러 조각으로 나눠 분산 저장(08번 문서 샤딩과 같은 개념) — 검색/색인 부하를 여러 노드로 분산.
- **레플리카(Replica)**: 샤드의 복제본 — 가용성(노드 장애 대응) + 읽기 처리량 증가.
- **match** 쿼리(분석기를 거쳐 유연하게 매칭) vs **term** 쿼리(분석 없이 정확히 일치하는 값만 — 카테고리/ID처럼 정형 값에 사용). 이 차이를 모르면 "왜 term 쿼리로 검색하니 결과가 하나도 안 나오지?" 같은 실무 삽질을 하게 됨.

## 5. 검색과 DB의 데이터 동기화 문제

- OpenSearch는 **원본 데이터(source of truth)가 아니라 검색 전용 뷰**로 쓰는 게 일반적 — 실제 데이터는 PostgreSQL/MongoDB에 저장하고, 변경 시 OpenSearch 인덱스도 같이 갱신(동기화)해야 함.
- 동기화 방식:
  - 애플리케이션에서 DB 저장 후 명시적으로 OpenSearch에도 색인 (구현 간단, 두 저장소 간 실패 시 불일치 가능성)
  - **CDC(Change Data Capture)**: DB의 변경 로그(binlog 등)를 스트림으로 읽어 자동으로 검색 엔진에 반영 — 애플리케이션 코드와 분리되어 일관성 관리가 쉬움, 08/06번 문서의 최종 일관성 개념과 연결.

## 6. 자주 나오는 질문

- "왜 검색은 DB LIKE 쿼리로 안 하고 별도 엔진을 쓰나요?" → 역색인 vs B-Tree 인덱스 구조 차이
- "검색 결과 순서는 어떻게 정해지나요?" → TF-IDF/BM25 관련도 스코어링
- "DB와 검색 엔진 데이터가 안 맞으면?" → 동기화 전략(명시적 갱신 vs CDC), 최종 일관성 트레이드오프
- "한국어 검색에서 특별히 신경 쓸 점은?" → 형태소 분석기(조사/어미 처리)

## 실습 방법

1. Docker로 OpenSearch 하나 띄우고 문서 몇 개 색인 → `match`/`term` 쿼리 차이 직접 확인
2. 같은 검색을 PostgreSQL `LIKE '%...%'`로도 해보고 `EXPLAIN`으로 Seq Scan 확인 (04번 문서 실습과 연계)
3. `project/`의 search 도메인 착수 시 초기엔 DB full-text search(PostgreSQL `tsvector`)로 단순하게 시작 → 이후 OpenSearch로 교체하는 단계적 구성 고려

## 참고 키워드 (검색용)
- inverted index search engine
- TF-IDF vs BM25 relevance scoring
- OpenSearch shard replica architecture
- match query vs term query Elasticsearch
- Change Data Capture search index synchronization
