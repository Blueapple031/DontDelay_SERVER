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
    private Storage storage = new Storage();

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
