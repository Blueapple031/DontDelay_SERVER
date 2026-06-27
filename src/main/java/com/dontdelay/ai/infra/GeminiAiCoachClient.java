package com.dontdelay.ai.infra;

import com.dontdelay.ai.config.GeminiProperties;
import com.dontdelay.ai.dto.ChatRequest;
import com.dontdelay.ai.dto.ChatResponse;
import com.dontdelay.ai.exception.AiApiException;
import com.dontdelay.ai.exception.AiErrorCode;
import com.dontdelay.ai.service.AiCoachGeneratedReply;
import com.dontdelay.ai.service.AiCoachLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnExpression("!'${gemini.api-key:}'.isBlank()")
public class GeminiAiCoachClient implements AiCoachLlmClient {

    private static final String SYSTEM_PROMPT = """
            당신은 DontDelay의 한국어 AI 코치입니다.
            사용자가 보낸 할 일 컨텍스트만 근거로 오늘의 우선순위와 다음 행동을 제안하세요.
            context.todos에 없는 과제나 일정을 확정된 사실처럼 말하지 마세요.
            추천 카드는 최대 3개만 만드세요.
            relatedTodoId는 반드시 context.todos에 있는 id만 사용하고, 새 할 일이면 null로 두세요.
            tagLevel은 urgent, scheduled, review, normal 중 하나만 사용하세요.
            응답은 반드시 JSON 객체만 반환하세요.
            스키마: {"content":"string","recommendations":[{"title":"string","timeRange":"string","tag":"string","tagLevel":"urgent|scheduled|review|normal","reason":"string","relatedTodoId":"string|null"}]}
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiAiCoachClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public AiCoachGeneratedReply generate(ChatRequest request) {
        try {
            Map<String, Object> body = Map.of(
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                    ),
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", buildUserPrompt(request)))
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "responseMimeType", "application/json"
                    )
            );

            String responseBody = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", properties.getApiKey())
                            .build(properties.getModel()))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseBody);
        } catch (RestClientException e) {
            throw new AiApiException(AiErrorCode.LLM_UNAVAILABLE, "Gemini 응답을 가져오지 못했습니다.", e);
        } catch (Exception e) {
            throw new AiApiException(AiErrorCode.LLM_UNAVAILABLE, "Gemini 응답을 처리하지 못했습니다.", e);
        }
    }

    private String buildUserPrompt(ChatRequest request) throws Exception {
        String contextJson = request.context() == null
                ? "{}"
                : objectMapper.writeValueAsString(request.context());
        return """
                사용자 질문:
                %s

                컨텍스트 JSON:
                %s
                """.formatted(request.message(), contextJson);
    }

    private AiCoachGeneratedReply parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini candidates가 비어 있습니다.");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini content.parts가 비어 있습니다.");
        }

        String generatedText = parts.get(0).path("text").asText("");
        if (generatedText.isBlank()) {
            throw new IllegalStateException("Gemini text 응답이 비어 있습니다.");
        }

        JsonNode generated = objectMapper.readTree(generatedText);
        String content = generated.path("content").asText("");
        List<ChatResponse.Recommendation> recommendations = parseRecommendations(
                generated.path("recommendations")
        );

        if (content.isBlank()) {
            content = "추천할 내용을 정리하지 못했습니다. 질문을 조금 더 구체적으로 입력해주세요.";
        }

        return new AiCoachGeneratedReply(content, recommendations);
    }

    private List<ChatResponse.Recommendation> parseRecommendations(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }

        List<ChatResponse.Recommendation> recommendations = new ArrayList<>();
        for (JsonNode item : node) {
            String title = item.path("title").asText("");
            if (title.isBlank()) {
                continue;
            }

            recommendations.add(new ChatResponse.Recommendation(
                    title,
                    item.path("timeRange").asText("오늘 안에"),
                    item.path("tag").asText("추천"),
                    normalizeTagLevel(item.path("tagLevel").asText("normal")),
                    textOrNull(item, "reason"),
                    textOrNull(item, "relatedTodoId")
            ));

            if (recommendations.size() >= 3) {
                break;
            }
        }
        return recommendations;
    }

    private String normalizeTagLevel(String raw) {
        return switch (raw) {
            case "urgent", "scheduled", "review", "normal" -> raw;
            default -> "normal";
        };
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
