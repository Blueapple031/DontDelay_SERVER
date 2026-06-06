package com.dontdelay.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityPublicEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signup_withoutSession_isAllowed() throws Exception {
        String username = "signup_" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {
                  "username": "%s",
                  "password": "password123",
                  "realName": "공개테스트",
                  "email": "%s@example.com",
                  "department": "컴퓨터공학과"
                }
                """.formatted(username, username);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원가입 성공"));
    }

    @Test
    void login_withoutSession_isAllowed() throws Exception {
        String username = "login_" + UUID.randomUUID().toString().substring(0, 8);
        String signupBody = """
                {
                  "username": "%s",
                  "password": "password123",
                  "realName": "로그인테스트",
                  "email": "%s@example.com",
                  "department": "컴퓨터공학과"
                }
                """.formatted(username, username);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인 성공"));
    }

    @Test
    void health_withoutSession_isAllowed() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void examDocuments_withoutSession_isBlocked() throws Exception {
        mockMvc.perform(get("/api/exam/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }
}
