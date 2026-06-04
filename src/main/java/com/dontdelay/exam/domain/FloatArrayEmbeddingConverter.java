package com.dontdelay.exam.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;

@Converter
public class FloatArrayEmbeddingConverter implements AttributeConverter<float[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(embedding.length * Float.BYTES);
        for (float value : embedding) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    @Override
    public float[] convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        float[] embedding = new float[dbData.length / Float.BYTES];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = buffer.getFloat();
        }
        return embedding;
    }
}
