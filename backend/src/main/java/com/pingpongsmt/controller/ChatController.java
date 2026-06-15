package com.pingpongsmt.controller;

import com.pingpongsmt.dto.ChatHistoryResponse;
import com.pingpongsmt.dto.ChatRequest;
import com.pingpongsmt.entity.ChatMessage;
import com.pingpongsmt.repository.ChatMessageRepository;
import com.pingpongsmt.service.LlmService;
import com.pingpongsmt.service.SessionManager;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Chat controller.
 * Provides POST /api/chat endpoint returning SSE stream,
 * and GET /api/chat/history for loading conversation history.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long TIMEOUT_MS = 120_000L;

    private final LlmService llmService;
    private final ChatMessageRepository messageRepository;
    private final SessionManager sessionManager;

    @Value("${pingpong.chat.history-limit:20}")
    private int historyLimit;

    @Autowired
    public ChatController(LlmService llmService,
                          ChatMessageRepository messageRepository,
                          SessionManager sessionManager) {
        this.llmService = llmService;
        this.messageRepository = messageRepository;
        this.sessionManager = sessionManager;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) throws IOException {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }

        String sessionId = request.getSessionId();

        // Create session if none provided
        if (sessionId == null || sessionId.isBlank() || !sessionManager.isValidSession(sessionId)) {
            sessionId = sessionManager.createSession(null).getSessionId();
        }

        // Save user message to database
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("user");
        userMessage.setContent(request.getMessage());
        messageRepository.save(userMessage);
        log.info("Saved user message to session: {}", sessionId);

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        // Buffer to collect assistant's full response for DB persistence
        // (LlmService.chatStreamWithSave handles saving internally)

        final String messageText = request.getMessage();
        final String finalSessionId = sessionId;

        CompletableFuture.runAsync(() -> {
            Flux<String> flux = llmService.chatStreamWithSave(messageText, finalSessionId);

            flux.subscribe(
                    data -> {
                        try {
                            emitter.send(SseEmitter.event().data(data));
                        } catch (IOException e) {
                            log.error("Failed to send SSE event", e);
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        log.error("SSE stream error", error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("{\"content\":\"Request failed, please try again\",\"done\":true}"));
                        } catch (IOException ignored) {
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        log.info("SSE stream completed for session: {}", finalSessionId);
                        emitter.complete();
                    }
            );
        });

        emitter.onTimeout(() -> log.warn("SSE emitter timed out"));
        emitter.onError((ex) -> log.error("SSE emitter error", ex));

        return emitter;
    }

    /**
     * Basic JSON string escaping for SSE data.
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    @GetMapping("/chat/history")
    public ChatHistoryResponse history(@RequestParam String sessionId,
                                       @RequestParam(defaultValue = "20") int limit) {
        if (sessionId == null || sessionId.isBlank() || !sessionManager.isValidSession(sessionId)) {
            return new ChatHistoryResponse(sessionId, java.util.Collections.emptyList());
        }

        int actualLimit = Math.min(limit, historyLimit);
        var messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(
                sessionId, PageRequest.of(0, actualLimit));

        return ChatHistoryResponse.from(sessionId, messages);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SseEmitter handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"content\":\"" + escapeJson(ex.getMessage()) + "\",\"done\":true}"));
        } catch (IOException ignored) {
        }
        emitter.complete();
        return emitter;
    }
}
