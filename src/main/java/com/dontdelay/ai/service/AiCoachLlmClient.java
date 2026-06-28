package com.dontdelay.ai.service;

import com.dontdelay.ai.dto.ChatRequest;

public interface AiCoachLlmClient {

    AiCoachGeneratedReply generate(ChatRequest request);
}
