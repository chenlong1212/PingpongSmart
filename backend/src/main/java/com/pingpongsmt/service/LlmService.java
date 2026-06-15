package com.pingpongsmt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingpongsmt.config.LlmConfig;
import com.pingpongsmt.entity.ChatMessage;
import com.pingpongsmt.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM (Large Language Model) API call service.
 * Uses OpenAI-compatible format to stream requests,
 * parses tokens one by one and returns Flux<String> (serialized SseMessage JSON).
 * Supports multi-turn conversation context from persisted messages.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final LlmConfig llmConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ChatMessageRepository messageRepository;
    private final SessionManager sessionManager;

    @Value("${pingpong.chat.context-rounds:10}")
    private int contextRounds;

    @Autowired
    public LlmService(LlmConfig llmConfig,
                      ChatMessageRepository messageRepository,
                      SessionManager sessionManager) {
        this.llmConfig = llmConfig;
        this.objectMapper = new ObjectMapper();
        this.messageRepository = messageRepository;
        this.sessionManager = sessionManager;

        this.webClient = WebClient.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfig.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Build the messages array for the LLM request.
     * Includes recent conversation context from the database.
     */
    private List<Map<String, String>> buildMessages(String currentMessage, String sessionId) {
        List<Map<String, String>> messages = new ArrayList<>();

        if (sessionId != null && !sessionId.isBlank() && sessionManager.isValidSession(sessionId)) {
            // Load all history for this session
            List<ChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

            // Calculate how many historical messages to include
            // Each round = 2 messages (user + assistant)
            int maxMessages = contextRounds * 2;

            // Take at most maxMessages from the end
            int startIndex = Math.max(0, history.size() - maxMessages);
            List<ChatMessage> contextMessages = history.subList(startIndex, history.size());

            for (ChatMessage msg : contextMessages) {
                Map<String, String> entry = new HashMap<>();
                entry.put("role", msg.getRole());
                entry.put("content", msg.getContent());
                messages.add(entry);
            }

            log.info("Added {} messages from context (session: {}, total history: {}, context rounds: {})",
                    contextMessages.size(), sessionId, history.size(), contextRounds);
        }

        // Append the current user message
        Map<String, String> currentEntry = new HashMap<>();
        currentEntry.put("role", "user");
        currentEntry.put("content", currentMessage);
        messages.add(currentEntry);

        return messages;
    }

    /**
     * Stream the LLM API call with conversation context.
     * Also saves the assistant's full response to the database after the stream completes.
     */
    public Flux<String> chatStreamWithSave(String message, String sessionId) {
        List<Map<String, String>> messages = buildMessages(message, sessionId);

        Map<String, Object> body = Map.of(
                "model", llmConfig.getModel(),
                "messages", messages,
                "stream", true
        );

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Failed to serialize request body", e);
            return Flux.just(serializeError("Request serialization failed"));
        }

        log.info("Calling LLM API: model={}, message_count={}, url={}/chat/completions",
                llmConfig.getModel(), messages.size(), llmConfig.getBaseUrl());

        // Buffer to collect full response
        final StringBuilder fullContent = new StringBuilder();

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
                .doOnNext(data -> {
                    // Extract content token from SSE data for DB persistence
                    try {
                        JsonNode root = objectMapper.readTree(data);
                        JsonNode content = root.path("choices").get(0).path("delta").path("content");
                        if (content.isTextual()) {
                            fullContent.append(content.asText(""));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract content for save: {}", data, e);
                    }
                })
                .map(data -> parseSseData(data))
                .filter(json -> json != null)
                .doOnComplete(() -> {
                    // Save AI reply to database
                    if (sessionId != null && sessionManager.isValidSession(sessionId)) {
                        ChatMessage assistantMessage = new ChatMessage();
                        assistantMessage.setSessionId(sessionId);
                        assistantMessage.setRole("assistant");
                        assistantMessage.setContent(fullContent.toString());
                        messageRepository.save(assistantMessage);
                        log.info("Saved assistant reply to session: {} (length: {})",
                                sessionId, fullContent.length());
                    }
                })
                .onErrorResume(WebClientResponseException.class, this::handleApiError)
                .onErrorResume(throwable -> {
                    log.error("LLM API stream error", throwable);
                    return Flux.just(serializeError("Request failed, please try again"));
                });
    }

    /**
     * Stream the LLM API call (legacy single-message mode, no context).
     * Kept for backward compatibility with v0.1.0 behavior.
     */
    public Flux<String> chatStream(String message) {
        return chatStreamWithSave(message, null);
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
