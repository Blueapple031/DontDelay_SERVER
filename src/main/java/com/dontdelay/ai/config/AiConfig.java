package com.dontdelay.ai.config;

import com.dontdelay.ai.infra.DeterministicEmbeddingClient;
import com.dontdelay.ai.infra.OpenAiEmbeddingClient;
import com.dontdelay.ai.service.EmbeddingClient;
import com.dontdelay.exam.config.ExamProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, GeminiProperties.class})
public class AiConfig {

    @Bean
    @ConditionalOnExpression("!'${openai.api-key:}'.isBlank()")
    public EmbeddingClient openAiEmbeddingClient(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        return new OpenAiEmbeddingClient(openAiProperties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    public EmbeddingClient deterministicEmbeddingClient(ExamProperties examProperties) {
        return new DeterministicEmbeddingClient(examProperties);
    }
}
