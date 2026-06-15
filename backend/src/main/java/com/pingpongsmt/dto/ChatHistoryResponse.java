package com.pingpongsmt.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.pingpongsmt.entity.ChatMessage;

/**
 * Response DTO for chat history API.
 */
public class ChatHistoryResponse {

    private String sessionId;
    private List<HistoryMessage> messages;

    public ChatHistoryResponse(String sessionId, List<HistoryMessage> messages) {
        this.sessionId = sessionId;
        this.messages = messages;
    }

    /**
     * Build ChatHistoryResponse from a list of ChatMessage entities and a session ID.
     */
    public static ChatHistoryResponse from(String sessionId, List<ChatMessage> messages) {
        List<HistoryMessage> history = messages.stream()
                .map(msg -> new HistoryMessage(msg.getRole(), msg.getContent(), msg.getCreatedAt()))
                .collect(Collectors.toList());
        return new ChatHistoryResponse(sessionId, history);
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<HistoryMessage> getMessages() {
        return messages;
    }

    /**
     * History message entry.
     */
    public static class HistoryMessage {
        private String role;
        private String content;
        private LocalDateTime createdAt;

        public HistoryMessage(String role, String content, LocalDateTime createdAt) {
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
