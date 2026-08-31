package com.example.backend_prep.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.example.backend_prep.domain.Document;
import com.example.backend_prep.repository.DocumentRepository;

import jakarta.transaction.Transactional;

// `document -> document.getOwner().getName()` 같은 화살표(->) 표현이 람다(lambda).
// "document라는 이름의 값을 하나 받아서, document.getOwner().getName()을 계산해 돌려주는
// 함수"를 그 자리에서 즉석으로 만든 것 — 익명 메서드 하나 만든 거라고 생각하면 됨. 아래처럼
// 미리 이름 붙여서 빼놓은 메서드를 그 자리에 즉석으로 써넣은 것과 동일:
//   private String ownerName(Document document) { return document.getOwner().getName(); }
// map()/filter() 같은 메서드들은 "값을 받아서 뭔가 계산해 돌려주는 함수"를 파라미터로
// 받는데, 매번 별도 메서드를 만들기 번거로우니 람다로 그 자리에서 정의해 넘기는 것.
@Service
public class DocumentService {
    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    // findById(id)는 Optional<Document>를 반환함 — "있을 수도, 없을 수도 있는 값을 담는 상자".
    // 문서가 없을 때 null을 리턴하는 대신 이 상자로 감싸서, "없는 경우"를 강제로 처리하게 만듦.
    //
    // .map(document -> document.getOwner().getName())
    // → 이건 Optional의 map. "상자 안에 값이 있으면, 그 값을 이 함수에 넣어서 나온 결과로
    // 상자 내용물을 바꿔치기해라. 상자가 비어있으면(문서 없음) 아무것도 안 하고 그냥 빈 상자
    // 그대로 둔다." Optional<Document> → (owner.getName() 결과인) Optional<String>이 됨.
    // 즉 "Document가 있으면 그 안의 owner 이름으로 변신"이라는 뜻.
    // .orElseThrow(...)
    // → 최종적으로 상자가 비어있으면(Optional.empty) 예외를 던지고, 값이 있으면 상자를 벗겨서
    // 그 안의 String을 그대로 반환.
    public String getOwner(Integer id) {
        return documentRepository.findById(id)
                .map(document -> document.getOwner().getName())
                .orElseThrow(() -> new IllegalArgumentException("해당 문서를 찾을 수 없습니다. id=" + id));
    }

    // 여기 나오는 .map()은 위 getOwner()의 .map()과 이름만 같고 완전히 다른 것 — 헷갈리기 쉬운 지점.
    //
    // findTop20WithOwnerOrderByIdAsc(...)는 List<Document>(문서 여러 개가 든 목록)를 반환함.
    // .stream()
    // → List를 "한 번에 하나씩 가공 작업을 흘려보낼 수 있는 파이프라인"으로 바꿔줌.
    // for문 대신 쓰는 것뿐, 리스트 안 20개 문서를 하나씩 순서대로 처리하게 준비하는 단계.
    // .map(document -> document.getOwner().getName())
    // → 이건 Stream의 map. "파이프라인으로 흘러오는 것 하나하나(Document)를 전부 이 함수에
    // 넣어서, 나온 결과(String, owner 이름)로 바꿔치기해라." Document 20개짜리 흐름이
    // String(이름) 20개짜리 흐름으로 바뀜. for문으로 쓰면 이거랑 완전히 같은 코드:
    // List<String> names = new ArrayList<>();
    // for (Document document : documents) {
    // names.add(document.getOwner().getName());
    // }
    // .toList()
    // → 파이프라인 끝에서 다시 진짜 List로 모아 담기 (위 for문의 names 변수에 해당).
    //
    // 정리: Optional.map은 "있을 수도 없을 수도 있는 값 1개"를 다루고,
    // Stream.map은 "여러 개의 값 묶음"을 하나씩 다룬다. 메서드 이름만 같음.
    //
    // JOIN FETCH 버전 호출 — 04번 문서 N+1 실습에서 findTop20ByOrderByIdAsc()(N+1 재현용)와
    // 쿼리 로그(spring.jpa.show-sql=true)로 직접 비교해볼 것. 21번 -> 1번으로 줄어듦.
    public List<String> getOwners() {
        return documentRepository.findTop20WithOwnerOrderByIdAsc(PageRequest.of(0, 20)).stream()
                .map(document -> document.getOwner().getName())
                .toList();
    }

    // @Transactional이 왜 필요한가: 이 메서드 안에서 document.changeContent(...)로 필드만
    // 바꾸고 documentRepository.save(...) 같은 걸 명시적으로 호출하지 않았는데도 실제로
    // DB에 반영되는 이유 — findById로 가져온 document는 "영속 상태"(Hibernate가 감시 중인
    // 객체)라서, 트랜잭션이 끝나는(커밋되는) 시점에 Hibernate가 "이 객체 필드가 마지막
    // 조회 때랑 달라졌나?"를 자동 비교(dirty checking)해서 다르면 알아서 UPDATE를 날림.
    // 이 UPDATE가 나가는 순간이 곧 @Version 필드의 "WHERE version=?" 조건 체크가 실제로
    // 일어나는 시점이라, @Transactional 없이는(트랜잭션/영속성 컨텍스트가 없어서) 이 흐름
    // 자체가 성립 안 함 — 낙관적 락 재현에 필수.
    @Transactional
    public Document updateContent(Integer id, String newContent) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 문서를 찾을 수 없습니다. id=" + id));
        document.changeContent(newContent);
        return document;
    }

}
