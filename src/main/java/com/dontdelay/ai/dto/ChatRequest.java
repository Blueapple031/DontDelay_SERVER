package com.dontdelay.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ChatRequest(
        @NotBlank(message = "메시지를 입력해주세요.")
        @Size(max = 2000, message = "메시지는 2,000자 이하여야 합니다.")
        String message,
        String sessionId,
        String locale,
        @Valid
        ChatContext context
) {
    public record ChatContext(
            String today,
            List<TodoContext> todos,
            List<Map<String, Object>> upcomingEvents
    ) {
    }

    public record TodoContext(
            String id,
            String title,
            String date,
            String status,
            String priority,
            Integer urgency,
            Integer importance,
            String tag,
            String time,
            String memo,
            String repeat
    ) {
    }
}
