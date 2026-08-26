package com.example.backend_prep.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend_prep.service.DocumentService;

// @RestController: 이 클래스의 메서드가 반환하는 값을 화면(HTML)이 아니라 HTTP 응답 바디로
// 그대로 직렬화(String은 그대로, 객체/List는 JSON으로) 해서 내려보내라는 표시.
@RestController
public class DocumentController {

    // 생성자로 DocumentService를 받아옴(생성자 주입) — Spring이 앱 기동 시 DocumentService
    // 객체를 만들어서 여기 자동으로 넣어줌. 우리가 직접 new DocumentController(...)를
    // 호출하는 게 아니라, Spring 컨테이너가 대신 해줌 — 01번 문서 DIP와 연결되는 부분.
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    // @GetMapping("/document"): GET /document 요청이 오면 이 메서드가 처리한다는 매핑.
    // @RequestParam: URL 쿼리스트링(?id=1)의 값을 파라미터로 꺼내옴 — 명시 안 해도 컴파일
    // 옵션에 따라 동작하는 경우가 있지만, 헷갈리지 않게 항상 명시하는 게 원칙(03번 문서).
    @GetMapping("/document")
    public String document(@RequestParam Integer id) {
        return documentService.getOwner(id);
    }

    @GetMapping("/documents")
    public List<String> documents() {
        return documentService.getOwners();
    }
}
