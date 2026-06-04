package com.dontdelay.exam.service;

import com.dontdelay.ai.service.EmbeddingClient;
import com.dontdelay.exam.config.ExamProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final int OPENAI_BATCH_SIZE = 64;

    private final EmbeddingClient embeddingClient;
    private final ExamProperties examProperties;

    public List<float[]> embedChunks(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allEmbeddings = new ArrayList<>(texts.size());
        for (int offset = 0; offset < texts.size(); offset += OPENAI_BATCH_SIZE) {
            int end = Math.min(offset + OPENAI_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(offset, end);
            List<float[]> batchEmbeddings = embeddingClient.embed(batch);
            validateBatch(batchEmbeddings, batch.size());
            allEmbeddings.addAll(batchEmbeddings);
        }
        return allEmbeddings;
    }

    private void validateBatch(List<float[]> batchEmbeddings, int expectedSize) {
        if (batchEmbeddings.size() != expectedSize) {
            throw new IllegalStateException("임베딩 결과 개수가 요청과 일치하지 않습니다.");
        }
        int dimensions = examProperties.getEmbeddingDimensions();
        for (float[] vector : batchEmbeddings) {
            if (vector.length != dimensions) {
                throw new IllegalStateException(
                        "임베딩 차원이 설정과 다릅니다. expected=" + dimensions + ", actual=" + vector.length
                );
            }
        }
    }
}
