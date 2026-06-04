package com.dontdelay.exam.service;

import com.dontdelay.exam.config.ExamProperties;
import com.dontdelay.exam.service.model.ExtractedPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtractionServiceTest {

    private ExamProperties examProperties;
    private ExtractionService extractionService;

    @BeforeEach
    void setUp() {
        examProperties = new ExamProperties();
        examProperties.getOcr().setMinCharsPerPage(50);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.dontdelay.exam.infra.OcrClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        extractionService = new ExtractionService(examProperties, provider);
    }

    @Test
    void needsOcr_whenAverageCharsBelowThreshold() {
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, "짧은"),
                new ExtractedPage(2, "텍스트")
        );

        assertThat(extractionService.needsOcr(pages)).isTrue();
    }

    @Test
    void needsOcr_whenEnoughTextPerPage() {
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, "가".repeat(100)),
                new ExtractedPage(2, "나".repeat(100))
        );

        assertThat(extractionService.needsOcr(pages)).isFalse();
    }

    @Test
    void needsOcr_whenEmpty() {
        assertThat(extractionService.needsOcr(List.of())).isTrue();
        assertThat(extractionService.needsOcr(List.of(new ExtractedPage(1, "")))).isTrue();
    }
}
