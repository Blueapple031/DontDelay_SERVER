package com.dontdelay.ai.infra;

import com.dontdelay.ai.service.EmbeddingClient;
import com.dontdelay.exam.config.ExamProperties;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * OpenAI 키가 없을 때 로컬·테스트용 결정적(deterministic) 임베딩.
 */
public class DeterministicEmbeddingClient implements EmbeddingClient {

    private final int dimensions;

    public DeterministicEmbeddingClient(ExamProperties examProperties) {
        this.dimensions = examProperties.getEmbeddingDimensions();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embedOne(text));
        }
        return vectors;
    }

    private float[] embedOne(String text) {
        float[] vector = new float[dimensions];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < dimensions; i++) {
            CRC32 crc32 = new CRC32();
            crc32.update(bytes);
            crc32.update(i);
            vector[i] = (crc32.getValue() % 1000) / 1000.0f;
        }
        return vector;
    }
}
