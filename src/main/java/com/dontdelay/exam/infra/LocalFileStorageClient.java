package com.dontdelay.exam.infra;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class LocalFileStorageClient implements StorageClient {

    private final Path basePath;

    public LocalFileStorageClient(String basePath) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream body, long sizeBytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(body, target);
        } catch (IOException e) {
            throw new IllegalStateException("로컬 저장소에 파일을 저장하지 못했습니다: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("로컬 저장소에서 파일을 읽지 못했습니다: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("로컬 저장소에서 파일을 삭제하지 못했습니다: " + key, e);
        }
    }

    @Override
    public URL presignedGetUrl(String key, Duration ttl) {
        try {
            return resolve(key).toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("presigned URL을 생성하지 못했습니다: " + key, e);
        }
    }

    private Path resolve(String key) {
        Path resolved = basePath.resolve(key).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("잘못된 저장소 키입니다.");
        }
        return resolved;
    }
}
