package com.pingpongsmt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingpongsmt.config.LlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * LLM (Large Language Model) API call service.
 * Uses OpenAI-compatible format to stream requests,
 * parses tokens one by one and returns Flux<String> (serialized SseMessage JSON).
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final LlmConfig llmConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public LlmService(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
        this.objectMapper = new ObjectMapper();

        this.webClient = WebClient.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfig.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Stream the LLM API call.
     * Each emitted String is a serialized SseMessage JSON.
     */
    public Flux<String> chatStream(String message) {
        Map<String, Object> body = Map.of(
                "model", llmConfig.getModel(),
                "messages", java.util.List.of(
                        Map.of("role", "user", "content", message)
                ),
                "stream", true
        );

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Failed to serialize request body", e);
            return Flux.just(serializeError("Request serialization failed"));
        }

        log.info("Calling LLM API: model={}, url={}/chat/completions",
                llmConfig.getModel(), llmConfig.getBaseUrl());

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(this::decodeDataBuffer)
                .concatMap(dataChunk -> {
                    String[] lines = dataChunk.split("\n");
                    return Flux.fromArray(lines);
                })
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6).trim())
                .filter(data -> !data.isEmpty() && !data.equals("[DONE]"))
                .map(data -> parseSseData(data))
                .filter(json -> json != null)
                .onErrorResume(WebClientResponseException.class, this::handleApiError)
                .onErrorResume(throwable -> {
                    log.error("LLM API stream error", throwable);
                    return Flux.just(serializeError("Request failed, please try again"));
                });
    }

    /**
     * Parse SSE response: extract token and serialize as SseMessage JSON.
     */
    private String parseSseData(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);

            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                JsonNode content = delta.path("content");

                JsonNode finishReason = choices.get(0).path("finish_reason");
                if (finishReason.isTextual() && !finishReason.asText().isEmpty()) {
                    return objectMapper.writeValueAsString(Map.of("content", "", "done", true));
                }

                String token = content.asText("");
                if (!token.isEmpty()) {
                    return objectMapper.writeValueAsString(Map.of("content", token, "done", false));
                }
            }

            // Direct done field
            if (root.has("done") && root.path("done").asBoolean(false)) {
                return objectMapper.writeValueAsString(Map.of("content", "", "done", true));
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to parse SSE data: {}", data, e);
            return null;
        }
    }

    /**
     * Handle API error responses.
     */
    private Flux<String> handleApiError(WebClientResponseException e) {
        String errorMsg;
        int statusCode = e.getStatusCode().value();
        if (statusCode == 401) {
            errorMsg = "Invalid API Key";
        } else if (statusCode == 429) {
            errorMsg = "Rate limited, please try again later";
        } else if (statusCode == 404) {
            errorMsg = "API endpoint not found, check base_url";
        } else {
            errorMsg = "API error (HTTP " + statusCode + ")";
        }
        log.error("LLM API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        return Flux.just(serializeError(errorMsg));
    }

    /**
     * Serialize an error message as SseMessage JSON.
     */
    private String serializeError(String errorMsg) {
        try {
            return objectMapper.writeValueAsString(Map.of("content", errorMsg, "done", true));
        } catch (Exception e) {
            log.error("Failed to serialize error message", e);
            return "{\"content\":\"System error\",\"done\":true}";
        }
    }

    private String decodeDataBuffer(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
