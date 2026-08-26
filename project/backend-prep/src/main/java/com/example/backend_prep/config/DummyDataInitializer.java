package com.example.backend_prep.config;

import com.example.backend_prep.domain.Document;
import com.example.backend_prep.domain.Member;
import com.example.backend_prep.repository.DocumentRepository;
import com.example.backend_prep.repository.MemberRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// CommandLineRunner를 구현하면(run 메서드 오버라이드) Spring이 앱 기동을 다 마친 직후
// 이 run()을 자동으로 한 번 호출해줌 — 우리가 직접 호출하는 코드는 어디에도 없음.
// @Component: 이 클래스를 Spring이 관리하는 객체(빈)로 등록 — 등록해야 Spring이 존재를
// 알고 위 run()을 실행 대상으로 인식함.
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