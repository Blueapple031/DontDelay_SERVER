package com.dontdelay.exam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "upstage")
public class UpstageProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.upstage.ai";
}
