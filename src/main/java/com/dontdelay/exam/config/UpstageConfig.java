package com.dontdelay.exam.config;

import com.dontdelay.exam.infra.OcrClient;
import com.dontdelay.exam.infra.UpstageOcrClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UpstageProperties.class)
public class UpstageConfig {

    @Bean
    @ConditionalOnExpression("!'${upstage.api-key:}'.isBlank()")
    public OcrClient upstageOcrClient(UpstageProperties upstageProperties, ObjectMapper objectMapper) {
        return new UpstageOcrClient(upstageProperties, objectMapper);
    }
}
