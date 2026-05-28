package com.dontdelay.exam.controller;

import com.dontdelay.exam.dto.DocumentDetailResponse;
import com.dontdelay.exam.dto.DocumentListResponse;
import com.dontdelay.exam.dto.UploadDocumentResponse;
import com.dontdelay.exam.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/exam/documents")
@RequiredArgsConstructor
public class ExamDocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<UploadDocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "subject", required = false) String subject
    ) {
        UploadDocumentResponse response = documentService.upload(file, title, subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public DocumentListResponse list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return documentService.list(status, page, size);
    }

    @GetMapping("/{documentId}")
    public DocumentDetailResponse get(@PathVariable UUID documentId) {
        return documentService.getDetail(documentId);
    }
}
