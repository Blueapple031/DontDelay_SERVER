package com.dontdelay.exam;

import com.dontdelay.domain.User;
import com.dontdelay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExamDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByUsername("examtest")) {
            userRepository.save(User.builder()
                    .username("examtest")
                    .password(passwordEncoder.encode("password"))
                    .build());
        }
    }

    @Test
    void uploadAndList_requiresAuth() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 test".getBytes()
        );

        mockMvc.perform(multipart("/api/exam/documents")
                        .file(file)
                        .param("subject", "테스트"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/exam/documents")
                        .file(file)
                        .param("subject", "테스트")
                        .with(user("examtest")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.subject").value("테스트"));

        mockMvc.perform(get("/api/exam/documents").with(user("examtest")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].subject").value("테스트"));
    }
}
