package com.example.backend_prep.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    // 반환값이 실제로는 "그 문서의 owner 이름"이라서, 경로/메서드명도 owner 기준으로 맞춤
    // (전엔 경로가 "/document"라 문서 자체를 돌려주는 것처럼 보였는데 실제 동작과 안 맞았음).
    //
    // @GetMapping("/documents/{id}/owner"): {id} 부분이 경로 변수(path variable) —
    // 예를 들어 GET /documents/3/owner 로 요청 오면 id 자리에 3이 들어옴.
    // @PathVariable: 그 경로 변수 값을 파라미터로 꺼내옴. 쿼리스트링(?id=3)을 쓰는
    // @RequestParam과 달리, URL 경로 자체의 일부로 리소스 식별자를 표현할 때 씀
    // (REST 관습상 "특정 리소스 하나"를 가리킬 땐 경로 변수 쪽이 더 자연스러움).
    @GetMapping("/documents/{id}/owner")
    public String owner(@PathVariable Integer id) {
        return documentService.getOwner(id);
    }

    // 상위 20개 문서 각각의 owner 이름 목록을 돌려줌 — 경로도 "여러 문서의 owner들"로 명확히.
    @GetMapping("/documents/owners")
    public List<String> owners() {
        return documentService.getOwners();
    }
}
