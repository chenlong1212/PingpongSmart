package com.pingpongsmt.controller;

import com.pingpongsmt.dto.ChatRequest;
import com.pingpongsmt.service.LlmService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Chat controller.
 * Provides POST /api/chat endpoint returning SSE stream.
 */
@Controller
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long TIMEOUT_MS = 120_000L;

    private final LlmService llmService;

    @Autowired
    public ChatController(LlmService llmService) {
        this.llmService = llmService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) throws IOException {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        log.info("Received chat request");

        CompletableFuture.runAsync(() -> {
            Flux<String> flux = llmService.chatStream(request.getMessage());

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
                        log.info("SSE stream completed");
                        emitter.complete();
                    }
            );
        });

        emitter.onTimeout(() -> log.warn("SSE emitter timed out"));
        emitter.onError((ex) -> log.error("SSE emitter error", ex));

        return emitter;
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

    /**
     * Basic JSON string escaping for SSE data.
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}
