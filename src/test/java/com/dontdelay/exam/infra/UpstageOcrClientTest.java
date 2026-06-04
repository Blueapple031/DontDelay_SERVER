package com.dontdelay.exam.infra;

import com.dontdelay.exam.config.UpstageProperties;
import com.dontdelay.exam.service.ExtractionException;
import com.dontdelay.exam.service.model.ExtractedPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstageOcrClientTest {

    @Test
    void parseResponse_mapsPages() {
        UpstageProperties properties = new UpstageProperties();
        properties.setApiKey("test-key");
        UpstageOcrClient client = new UpstageOcrClient(properties, new ObjectMapper());

        String json = """
                {
                  "text": "전체 텍스트",
                  "pages": [
                    {"id": 0, "text": "1페이지"},
                    {"id": 1, "text": "2페이지"}
                  ]
                }
                """;

        List<ExtractedPage> pages = invokeParse(client, json);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).pageNo()).isEqualTo(1);
        assertThat(pages.get(0).text()).isEqualTo("1페이지");
        assertThat(pages.get(1).pageNo()).isEqualTo(2);
    }

    @Test
    void parseResponse_usesFullTextWhenPagesMissing() {
        UpstageProperties properties = new UpstageProperties();
        UpstageOcrClient client = new UpstageOcrClient(properties, new ObjectMapper());

        String json = """
                {"text": "단일 텍스트"}
                """;

        List<ExtractedPage> pages = invokeParse(client, json);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).text()).isEqualTo("단일 텍스트");
    }

    @Test
    void parseResponse_throwsWhenNoText() {
        UpstageProperties properties = new UpstageProperties();
        UpstageOcrClient client = new UpstageOcrClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> invokeParse(client, "{}"))
                .rootCause()
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("텍스트가 없습니다");
    }

    @SuppressWarnings("unchecked")
    private List<ExtractedPage> invokeParse(UpstageOcrClient client, String json) {
        try {
            var method = UpstageOcrClient.class.getDeclaredMethod("parseResponse", String.class);
            method.setAccessible(true);
            return (List<ExtractedPage>) method.invoke(client, json);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
