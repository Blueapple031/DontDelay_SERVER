package com.dontdelay.ai.controller;

import com.dontdelay.domain.User;
import com.dontdelay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiCoachControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByUsername("aicoachtest")) {
            userRepository.save(User.builder()
                    .username("aicoachtest")
                    .password(passwordEncoder.encode("password"))
                    .realName("AI코치테스트")
                    .email("aicoach@example.com")
                    .department("컴퓨터공학과")
                    .build());
        }
    }

    @Test
    void chat_withoutSession_isBlocked() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"오늘 뭐부터 해야 해?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void chat_withSession_returnsReplyAndRecommendations() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"aicoachtest\",\"password\":\"password\"}"))
                .andExpect(status().isOk());

        String body = """
                {
                  "message": "오늘 뭐부터 해야 해?",
                  "locale": "ko-KR",
                  "context": {
                    "today": "2026-06-28",
                    "todos": [
                      {
                        "id": "todo-1",
                        "title": "알고리즘 과제",
                        "date": "2026-06-27",
                        "status": "todo",
                        "priority": "high",
                        "urgency": 8,
                        "importance": 7,
                        "tag": "전공"
                      }
                    ],
                    "upcomingEvents": []
                  }
                }
                """;

        mockMvc.perform(post("/api/ai/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.reply.role").value("assistant"))
                .andExpect(jsonPath("$.reply.content").isString())
                .andExpect(jsonPath("$.reply.recommendations[0].title").value("알고리즘 과제"))
                .andExpect(jsonPath("$.reply.recommendations[0].tagLevel").value("urgent"))
                .andExpect(jsonPath("$.reply.recommendations[0].relatedTodoId").value("todo-1"))
                .andExpect(jsonPath("$.reply.recommendations[0].action").value("completeTodo"));
    }

    @Test
    void chat_withoutTodos_canReturnCreateTodoRecommendation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"aicoachtest\",\"password\":\"password\"}"))
                .andExpect(status().isOk());

        String body = """
                {
                  "message": "오늘 할 일 추천해줘",
                  "locale": "ko-KR",
                  "context": {
                    "today": "2026-06-28",
                    "todos": [],
                    "upcomingEvents": []
                  }
                }
                """;

        mockMvc.perform(post("/api/ai/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.recommendations[0].action").value("createTodo"))
                .andExpect(jsonPath("$.reply.recommendations[0].todoDraft.title").isString())
                .andExpect(jsonPath("$.reply.recommendations[0].todoDraft.date").value("2026-06-28"));
    }
}
