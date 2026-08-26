package com.example.backend_prep.repository;

import java.util.List;

import com.example.backend_prep.domain.Document;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<Document, Integer> {

    // N+1 재현용 (일부러 남겨둠, 정상 경로에서 호출하지 말 것).
    // 이걸로 조회한 Document 목록을 반복문에서 owner.getName()으로 건드리면
    // document 조회 1번 + owner 조회 N번, 총 1+N번 쿼리가 나감 (owner가 LAZY라서).
    List<Document> findTop20ByOrderByIdAsc();

    // JOIN FETCH: owner를 SQL JOIN으로 미리 같이 가져와서 위 N+1을 없앰.
    // 결과 Document의 owner는 프록시가 아니라 이미 채워진 실제 객체 — 나중에 접근해도 추가 쿼리 안 나감.
    @Query("SELECT d FROM Document d JOIN FETCH d.owner ORDER BY d.id")
    List<Document> findTop20WithOwnerOrderByIdAsc(Pageable pageable);
}
