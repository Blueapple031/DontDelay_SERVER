package com.dontdelay.exam.service;

import com.dontdelay.exam.config.ExamProperties;
import com.dontdelay.exam.service.model.ExtractedPage;
import com.dontdelay.exam.service.model.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkServiceTest {

    @Test
    void chunkPages_splitsWithOverlap() {
        ExamProperties properties = new ExamProperties();
        properties.setChunkSize(20);
        properties.setChunkOverlap(5);
        properties.setMinChunkSize(5);

        ChunkService chunkService = new ChunkService(properties);
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, "가".repeat(50))
        );

        List<TextChunk> chunks = chunkService.chunkPages(pages);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).pageNo()).isEqualTo(1);
        assertThat(chunks.get(0).content()).hasSizeLessThanOrEqualTo(20);
    }
}
