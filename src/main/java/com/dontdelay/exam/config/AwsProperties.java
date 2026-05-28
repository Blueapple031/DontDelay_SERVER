package com.dontdelay.exam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region = "ap-northeast-2";
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {
        private String bucket = "dontdelay-exam";
        private int presignedUrlTtlMinutes = 15;
    }
}
