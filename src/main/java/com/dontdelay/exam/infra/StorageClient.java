package com.dontdelay.exam.infra;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

public interface StorageClient {

    void put(String key, InputStream body, long sizeBytes, String contentType);

    InputStream get(String key);

    void delete(String key);

    URL presignedGetUrl(String key, Duration ttl);
}
