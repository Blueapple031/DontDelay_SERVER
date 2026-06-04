package com.dontdelay.exam.service;

import com.dontdelay.exam.config.ExamProperties;
import com.dontdelay.exam.service.model.ExtractedPage;
import com.dontdelay.exam.service.model.TextChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkService {

    private final ExamProperties examProperties;

    public List<TextChunk> chunkPages(List<ExtractedPage> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ExtractedPage page : pages) {
            if (page.text().isBlank()) {
                continue;
            }
            for (TextChunk chunk : chunkText(page.text(), page.pageNo(), chunkIndex)) {
                chunks.add(chunk);
                chunkIndex = chunk.chunkIndex() + 1;
            }
        }

        return mergeSmallTrailingChunks(chunks);
    }

    private List<TextChunk> chunkText(String text, int pageNo, int startIndex) {
        int chunkSize = examProperties.getChunkSize();
        int overlap = examProperties.getChunkOverlap();
        List<TextChunk> chunks = new ArrayList<>();

        int offset = 0;
        int chunkIndex = startIndex;
        while (offset < text.length()) {
            int end = Math.min(offset + chunkSize, text.length());
            String content = text.substring(offset, end).trim();
            if (!content.isBlank()) {
                chunks.add(new TextChunk(chunkIndex, pageNo, content));
                chunkIndex++;
            }
            if (end >= text.length()) {
                break;
            }
            offset = Math.max(end - overlap, offset + 1);
        }

        return chunks;
    }

    private List<TextChunk> mergeSmallTrailingChunks(List<TextChunk> chunks) {
        if (chunks.size() < 2) {
            return chunks;
        }

        int minSize = examProperties.getMinChunkSize();
        List<TextChunk> merged = new ArrayList<>(chunks);
        TextChunk last = merged.get(merged.size() - 1);
        if (last.content().length() >= minSize) {
            return merged;
        }

        TextChunk previous = merged.get(merged.size() - 2);
        merged.remove(merged.size() - 1);
        merged.set(
                merged.size() - 1,
                new TextChunk(
                        previous.chunkIndex(),
                        previous.pageNo(),
                        previous.content() + " " + last.content()
                )
        );
        return merged;
    }
}
