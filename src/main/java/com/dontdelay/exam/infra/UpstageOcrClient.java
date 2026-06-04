package com.dontdelay.exam.infra;

import com.dontdelay.exam.config.UpstageProperties;
import com.dontdelay.exam.service.ExtractionException;
import com.dontdelay.exam.service.model.ExtractedPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

public class UpstageOcrClient implements OcrClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public UpstageOcrClient(UpstageProperties upstageProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(upstageProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + upstageProperties.getApiKey())
                .build();
    }

    @Override
    public List<ExtractedPage> extract(byte[] documentBytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", "ocr");
        body.add("document", new ByteArrayResource(documentBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/v1/document-digitization")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new ExtractionException(
                    "OCR_FAILED",
                    "Upstage OCR 요청에 실패했습니다: " + e.getStatusCode().value(),
                    e
            );
        } catch (Exception e) {
            throw new ExtractionException("OCR_FAILED", "Upstage OCR 요청에 실패했습니다.", e);
        }

        return parseResponse(responseBody);
    }

    private List<ExtractedPage> parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode pagesNode = root.get("pages");
            if (pagesNode == null || !pagesNode.isArray() || pagesNode.isEmpty()) {
                String fullText = root.path("text").asText("");
                if (fullText.isBlank()) {
                    throw new ExtractionException("OCR_FAILED", "Upstage OCR 결과에 텍스트가 없습니다.");
                }
                return List.of(new ExtractedPage(1, fullText));
            }

            List<ExtractedPage> pages = new ArrayList<>(pagesNode.size());
            for (JsonNode pageNode : pagesNode) {
                int pageNo = pageNode.path("id").asInt(0) + 1;
                String text = pageNode.path("text").asText("");
                pages.add(new ExtractedPage(pageNo, text));
            }
            return pages;
        } catch (ExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtractionException("OCR_FAILED", "Upstage OCR 응답을 파싱하지 못했습니다.", e);
        }
    }
}
