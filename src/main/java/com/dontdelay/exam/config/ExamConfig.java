package com.dontdelay.exam.config;

import com.dontdelay.exam.infra.LocalFileStorageClient;
import com.dontdelay.exam.infra.S3StorageClient;
import com.dontdelay.exam.infra.StorageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties({ExamProperties.class, AwsProperties.class})
public class ExamConfig {

    @Bean
    @ConditionalOnProperty(name = "exam.storage.type", havingValue = "s3")
    public StorageClient s3StorageClient(AwsProperties awsProperties) {
        return new S3StorageClient(awsProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "exam.storage.type", havingValue = "local", matchIfMissing = true)
    public StorageClient localFileStorageClient(ExamProperties examProperties) {
        return new LocalFileStorageClient(examProperties.getStorage().getLocalBasePath());
    }
}
