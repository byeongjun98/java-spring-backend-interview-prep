package com.example.backend_prep.config;

import com.example.backend_prep.domain.Document;
import com.example.backend_prep.domain.Member;
import com.example.backend_prep.repository.DocumentRepository;
import com.example.backend_prep.repository.MemberRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DummyDataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final DocumentRepository documentRepository;

    public DummyDataInitializer(MemberRepository memberRepository, DocumentRepository documentRepository) {
        this.memberRepository = memberRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public void run(String... args) {
        if (memberRepository.count() > 0) {
            return; // 재기동마다 중복 삽입 방지
        }

        for (int i = 1; i <= 100; i++) {
            memberRepository.save(new Member("member" + i, "member" + i + "@test.com"));
        }
        System.out.println("member 100건 삽입 완료");

        // Document 1만건 — getReferenceById로 Member를 실제 SELECT 없이 프록시로만 참조 (FK만 필요하므로)
        for (int i = 1; i <= 10000; i++) {
            int ownerId = (i % 100) + 1; // owner를 100명한테 고르게 분산
            Member ownerRef = memberRepository.getReferenceById(ownerId);
            documentRepository.save(new Document("title" + i, "content" + i, ownerRef));
        }
        System.out.println("document 10000건 삽입 완료");
    }
}