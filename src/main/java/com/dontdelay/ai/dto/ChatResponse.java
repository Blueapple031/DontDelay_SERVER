package com.dontdelay.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatResponse(
        String sessionId,
        Reply reply
) {
    public record Reply(
            String role,
            String content,
            List<Recommendation> recommendations,
            OffsetDateTime createdAt
    ) {
    }

    public record Recommendation(
            String title,
            String timeRange,
            String tag,
            String tagLevel,
            String reason,
            String relatedTodoId,
            String action,
            TodoDraft todoDraft
    ) {
    }

    public record TodoDraft(
            String title,
            String date,
            String priority,
            Integer urgency,
            Integer importance,
            String tag,
            String time,
            String memo
    ) {
    }
}
