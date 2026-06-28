package com.dontdelay.ai.controller;

import com.dontdelay.ai.dto.ChatRequest;
import com.dontdelay.ai.dto.ChatResponse;
import com.dontdelay.ai.service.AiCoachService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiCoachController {

    private final AiCoachService aiCoachService;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return aiCoachService.chat(request);
    }
}
