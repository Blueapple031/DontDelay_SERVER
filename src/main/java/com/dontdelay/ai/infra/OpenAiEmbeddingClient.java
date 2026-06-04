package com.dontdelay.ai.infra;

import com.dontdelay.ai.config.OpenAiProperties;
import com.dontdelay.ai.service.EmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public OpenAiEmbeddingClient(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", openAiProperties.getEmbeddingModel());
        body.put("input", texts);

        String responseBody = restClient.post()
                .uri("/v1/embeddings")
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            List<float[]> embeddings = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                embeddings.add(vector);
            }
            return embeddings;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 임베딩 응답을 파싱하지 못했습니다.", e);
        }
    }
}
