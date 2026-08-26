package com.example.backend_prep.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.example.backend_prep.repository.DocumentRepository;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public String getOwner(Integer id) {
        return documentRepository.findById(id)
                .map(document -> document.getOwner().getName())
                .orElseThrow(() -> new IllegalArgumentException("해당 문서를 찾을 수 없습니다. id=" + id));
    }

    // JOIN FETCH 버전 호출 — 04번 문서 N+1 실습에서 findTop20ByOrderByIdAsc()(N+1 재현용)와
    // 쿼리 로그(spring.jpa.show-sql=true)로 직접 비교해볼 것. 21번 -> 1번으로 줄어듦.
    public List<String> getOwners() {
        return documentRepository.findTop20WithOwnerOrderByIdAsc(PageRequest.of(0, 20)).stream()
                .map(document -> document.getOwner().getName())
                .toList();
    }

}
