package com.dontdelay.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private String apiKey = "";
    private String embeddingModel = "text-embedding-3-small";
}
