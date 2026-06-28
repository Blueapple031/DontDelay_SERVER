package com.dontdelay.ai.service;

import com.dontdelay.ai.dto.ChatRequest;
import com.dontdelay.ai.dto.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AiCoachService {

    private final ObjectProvider<AiCoachLlmClient> llmClientProvider;

    public AiCoachService(ObjectProvider<AiCoachLlmClient> llmClientProvider) {
        this.llmClientProvider = llmClientProvider;
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        AiCoachGeneratedReply generatedReply = llmClientProvider
                .getIfAvailable(() -> this::generateRuleBasedReply)
                .generate(request);

        return new ChatResponse(
                sessionId,
                new ChatResponse.Reply(
                        "assistant",
                        generatedReply.content(),
                        generatedReply.recommendations(),
                        OffsetDateTime.now()
                )
        );
    }

    private AiCoachGeneratedReply generateRuleBasedReply(ChatRequest request) {
        List<ChatResponse.Recommendation> recommendations = buildRecommendations(request);
        return new AiCoachGeneratedReply(buildContent(request, recommendations), recommendations);
    }

    private List<ChatResponse.Recommendation> buildRecommendations(ChatRequest request) {
        if (request.context() == null || request.context().todos() == null) {
            return List.of();
        }

        String today = request.context().today();
        return request.context().todos().stream()
                .filter(todo -> todo.title() != null && !todo.title().isBlank())
                .sorted(Comparator
                        .comparing((ChatRequest.TodoContext todo) -> isOverdue(todo, today)).reversed()
                        .thenComparing(todo -> isToday(todo, today), Comparator.reverseOrder())
                        .thenComparing(this::score, Comparator.reverseOrder()))
                .limit(3)
                .map(todo -> toRecommendation(todo, today))
                .toList();
    }

    private String buildContent(
            ChatRequest request,
            List<ChatResponse.Recommendation> recommendations
    ) {
        int todoCount = request.context() != null && request.context().todos() != null
                ? request.context().todos().size()
                : 0;
        if (recommendations.isEmpty()) {
            return "현재 전달된 할 일이 없습니다. 오늘은 회고를 짧게 남기거나 다음 할 일을 1~2개만 정리해보세요.";
        }

        ChatResponse.Recommendation top = recommendations.get(0);
        return "지금은 \"" + top.title() + "\"부터 처리하는 걸 추천합니다.\n\n"
                + "현재 전달된 할 일은 " + todoCount + "개입니다. "
                + "아래 추천 순서대로 진행하면 마감 리스크와 중요도를 함께 줄일 수 있습니다.";
    }

    private ChatResponse.Recommendation toRecommendation(ChatRequest.TodoContext todo, String today) {
        if (isOverdue(todo, today)) {
            return new ChatResponse.Recommendation(
                    todo.title(),
                    "지금 바로",
                    "지난 마감",
                    "urgent",
                    "마감일이 지나 우선 정리가 필요합니다.",
                    todo.id()
            );
        }

        if (isToday(todo, today)) {
            return new ChatResponse.Recommendation(
                    todo.title(),
                    todo.time() == null || todo.time().isBlank() ? "오늘 안에" : todo.time(),
                    "오늘 할 일",
                    "scheduled",
                    "오늘 처리 대상입니다.",
                    todo.id()
            );
        }

        if (safeInt(todo.importance()) >= 6) {
            return new ChatResponse.Recommendation(
                    todo.title(),
                    "집중 40분",
                    "중요",
                    "review",
                    "중요도가 높아 미리 진도를 내는 편이 좋습니다.",
                    todo.id()
            );
        }

        return new ChatResponse.Recommendation(
                todo.title(),
                "여유 시간",
                "대기",
                "normal",
                "긴급한 항목 뒤에 처리하면 됩니다.",
                todo.id()
        );
    }

    private boolean isOverdue(ChatRequest.TodoContext todo, String today) {
        return today != null
                && todo.date() != null
                && todo.repeat() == null
                && todo.date().compareTo(today) < 0;
    }

    private boolean isToday(ChatRequest.TodoContext todo, String today) {
        return today != null && today.equals(todo.date());
    }

    private int score(ChatRequest.TodoContext todo) {
        int priorityBonus = switch (todo.priority() == null ? "" : todo.priority()) {
            case "high" -> 16;
            case "medium" -> 8;
            default -> 0;
        };
        return safeInt(todo.urgency()) * 2 + safeInt(todo.importance()) * 2 + priorityBonus;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
