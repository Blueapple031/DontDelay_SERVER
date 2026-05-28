package com.dontdelay.exam.repository;

import com.dontdelay.exam.domain.DocumentStatus;
import com.dontdelay.exam.domain.StudyDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudyDocumentRepository extends JpaRepository<StudyDocument, UUID> {

    Page<StudyDocument> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<StudyDocument> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, DocumentStatus status, Pageable pageable);

    Optional<StudyDocument> findByIdAndUserId(UUID id, Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, java.time.OffsetDateTime after);
}
