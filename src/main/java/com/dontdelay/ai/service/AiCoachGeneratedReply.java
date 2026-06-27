package com.dontdelay.ai.service;

import com.dontdelay.ai.dto.ChatResponse;

import java.util.List;

public record AiCoachGeneratedReply(
        String content,
        List<ChatResponse.Recommendation> recommendations
) {
}
