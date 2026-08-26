package com.example.backend_prep.repository;

import java.util.List;

import com.example.backend_prep.domain.Document;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// interface인데 본문(구현)이 하나도 없음 — Spring Data JPA가 실행 시점에 이 인터페이스를
// 보고 실제로 동작하는 클래스를 자동으로 만들어서 꽂아줌 (우리가 직접 구현 안 해도 됨).
// JpaRepository<Document, Integer>를 extends만 해도 save/findById/findAll/delete 등
// 기본 CRUD 메서드가 전부 공짜로 생김 — DocumentService에서 쓴 findById(id)도 여기서 옴.
public interface DocumentRepository extends JpaRepository<Document, Integer> {

    // findTop20ByOrderByIdAsc() 처럼 SQL을 안 써도, "메서드 이름 자체"를 규칙대로 지으면
    // Spring Data가 이름을 분석해서 쿼리를 자동으로 만들어줌:
    //   findTop20  → LIMIT 20
    //   By          → WHERE 절 시작 (여기선 조건 없이 바로 정렬로 넘어감)
    //   OrderByIdAsc → ORDER BY id ASC
    // (조건을 걸고 싶으면 findByOwnerId(Integer ownerId)처럼 필드명을 이어붙이면 됨 —
    // "Spring Data JPA query method naming"으로 검색하면 전체 규칙 나옴)
    //
    // N+1 재현용 (일부러 남겨둠, 정상 경로에서 호출하지 말 것).
    // 이걸로 조회한 Document 목록을 반복문에서 owner.getName()으로 건드리면
    // document 조회 1번 + owner 조회 N번, 총 1+N번 쿼리가 나감 (owner가 LAZY라서).
    List<Document> findTop20ByOrderByIdAsc();

    // 메서드 이름 규칙만으론 JOIN FETCH 같은 걸 표현 못 해서, @Query로 직접 JPQL(SQL과
    // 비슷하지만 테이블 대신 엔티티/필드명을 쓰는 JPA 전용 쿼리 언어)을 써준 것.
    // 파라미터로 받는 Pageable은 "몇 번째 페이지를, 몇 개씩" 정보를 담는 객체 —
    // 여기선 DocumentService에서 PageRequest.of(0, 20)(0번째 페이지, 20개씩)을 넘겨서
    // 위와 동일하게 "상위 20개"로 제한하는 용도로만 씀.
    //
    // JOIN FETCH: owner를 SQL JOIN으로 미리 같이 가져와서 위 N+1을 없앰.
    // 결과 Document의 owner는 프록시가 아니라 이미 채워진 실제 객체 — 나중에 접근해도 추가 쿼리 안 나감.
    @Query("SELECT d FROM Document d JOIN FETCH d.owner ORDER BY d.id")
    List<Document> findTop20WithOwnerOrderByIdAsc(Pageable pageable);
}
