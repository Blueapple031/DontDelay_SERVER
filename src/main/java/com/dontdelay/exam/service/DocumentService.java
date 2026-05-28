package com.dontdelay.exam.service;

import com.dontdelay.domain.User;
import com.dontdelay.exam.config.ExamProperties;
import com.dontdelay.exam.domain.DocumentStatus;
import com.dontdelay.exam.domain.StudyDocument;
import com.dontdelay.exam.dto.DocumentDetailResponse;
import com.dontdelay.exam.dto.DocumentListResponse;
import com.dontdelay.exam.dto.DocumentSummaryDto;
import com.dontdelay.exam.dto.UploadDocumentResponse;
import com.dontdelay.exam.exception.ExamApiException;
import com.dontdelay.exam.exception.ExamErrorCode;
import com.dontdelay.exam.infra.StorageClient;
import com.dontdelay.exam.job.DocumentIndexingJob;
import com.dontdelay.exam.repository.StudyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final StudyDocumentRepository studyDocumentRepository;
    private final StorageClient storageClient;
    private final CurrentUserService currentUserService;
    private final ExamProperties examProperties;
    private final DocumentIndexingJob documentIndexingJob;

    @Transactional
    public UploadDocumentResponse upload(MultipartFile file, String title, String subject) {
        ensureEnabled();
        User user = currentUserService.requireCurrentUser();
        enforceUploadRateLimit(user.getId());
        validatePdf(file);

        UUID documentId = UUID.randomUUID();
        String resolvedTitle = resolveTitle(title, file.getOriginalFilename());
        String storageKey = buildStorageKey(user.getId(), documentId);

        try {
            storageClient.put(
                    storageKey,
                    file.getInputStream(),
                    file.getSize(),
                    PDF_CONTENT_TYPE
            );
        } catch (IOException e) {
            throw new IllegalStateException("PDF 업로드 스트림을 읽지 못했습니다.", e);
        }

        OffsetDateTime now = OffsetDateTime.now();
        StudyDocument document = StudyDocument.builder()
                .id(documentId)
                .user(user)
                .title(resolvedTitle)
                .subject(subject)
                .s3Key(storageKey)
                .status(DocumentStatus.UPLOADED)
                .progress(0)
                .pageCount(null)
                .chunkCount(0)
                .fileSizeBytes(file.getSize())
                .errorCode(null)
                .errorMessage(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        studyDocumentRepository.save(document);
        documentIndexingJob.start(documentId);

        return new UploadDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSubject(),
                document.getStatus(),
                document.getFileSizeBytes(),
                document.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public DocumentListResponse list(String statusFilter, int page, int size) {
        ensureEnabled();
        User user = currentUserService.requireCurrentUser();
        PageRequest pageable = PageRequest.of(page, size);

        Page<StudyDocument> result;
        if (statusFilter != null && !statusFilter.isBlank()) {
            DocumentStatus status = DocumentStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
            result = studyDocumentRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    user.getId(), status, pageable);
        } else {
            result = studyDocumentRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        }

        List<DocumentSummaryDto> items = result.getContent().stream()
                .map(this::toSummary)
                .toList();

        return new DocumentListResponse(items, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse getDetail(UUID documentId) {
        ensureEnabled();
        User user = currentUserService.requireCurrentUser();
        StudyDocument document = findOwnedDocument(documentId, user.getId());
        return toDetail(document);
    }

    private StudyDocument findOwnedDocument(UUID documentId, Long userId) {
        return studyDocumentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ExamApiException(
                        ExamErrorCode.NOT_FOUND,
                        "문서를 찾을 수 없습니다."
                ));
    }

    private void ensureEnabled() {
        if (!examProperties.getGenerator().isEnabled()) {
            throw new ExamApiException(
                    ExamErrorCode.EXAM_DISABLED,
                    "시험 문제 생성 기능이 비활성화되어 있습니다."
            );
        }
    }

    private void enforceUploadRateLimit(Long userId) {
        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);
        long recentCount = studyDocumentRepository.countByUserIdAndCreatedAtAfter(userId, oneHourAgo);
        if (recentCount >= examProperties.getRateLimitUploadPerHour()) {
            throw new ExamApiException(
                    ExamErrorCode.RATE_LIMITED,
                    "업로드 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."
            );
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExamApiException(ExamErrorCode.INVALID_FILE, "PDF 파일이 필요합니다.");
        }
        if (file.getSize() > examProperties.getMaxUploadBytes()) {
            throw new ExamApiException(ExamErrorCode.INVALID_FILE, "파일 크기가 허용 한도를 초과했습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ExamApiException(ExamErrorCode.INVALID_FILE, "PDF 확장자(.pdf)만 업로드할 수 있습니다.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new ExamApiException(ExamErrorCode.INVALID_FILE, "application/pdf 형식만 업로드할 수 있습니다.");
        }
    }

    private String resolveTitle(String title, String originalFilename) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (originalFilename != null && !originalFilename.isBlank()) {
            return originalFilename;
        }
        return "untitled.pdf";
    }

    private String buildStorageKey(Long userId, UUID documentId) {
        return "users/" + userId + "/documents/" + documentId + "/original.pdf";
    }

    private DocumentSummaryDto toSummary(StudyDocument document) {
        return new DocumentSummaryDto(
                document.getId(),
                document.getTitle(),
                document.getSubject(),
                document.getStatus(),
                document.getPageCount(),
                document.getChunkCount(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private DocumentDetailResponse toDetail(StudyDocument document) {
        return new DocumentDetailResponse(
                document.getId(),
                document.getTitle(),
                document.getSubject(),
                document.getStatus(),
                document.getProgress(),
                document.getPageCount(),
                document.getChunkCount(),
                document.getErrorCode(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
