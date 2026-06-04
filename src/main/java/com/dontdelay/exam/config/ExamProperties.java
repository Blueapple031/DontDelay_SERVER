package com.dontdelay.exam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "exam")
public class ExamProperties {

    private Generator generator = new Generator();
    private long maxUploadBytes = 52_428_800L;
    private int rateLimitUploadPerHour = 10;
    private int chunkSize = 800;
    private int chunkOverlap = 120;
    private int minChunkSize = 100;
    private int embeddingDimensions = 1536;
    private Ocr ocr = new Ocr();
    private Storage storage = new Storage();

    @Getter
    @Setter
    public static class Ocr {
        private boolean enabled = true;
        private int minCharsPerPage = 50;
    }

    @Getter
    @Setter
    public static class Generator {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Storage {
        private String type = "local";
        private String localBasePath = "./data/exam-storage";
    }
}
