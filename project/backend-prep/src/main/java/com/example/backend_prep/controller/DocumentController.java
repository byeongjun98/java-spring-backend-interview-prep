package com.example.backend_prep.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend_prep.service.DocumentService;

@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/document")
    public String document(Integer id) {
        return documentService.getOwner(id);
    }

    @GetMapping("/documents")
    public List<String> documents() {
        return documentService.getOwners();
    }
}
