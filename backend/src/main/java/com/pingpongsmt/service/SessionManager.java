package com.pingpongsmt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manages chat sessions.
 * Session IDs are stored on the frontend (localStorage); this service only creates new ones.
 */
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /**
     * Session information holder.
     */
    public static class SessionInfo {
        private final String sessionId;
        private final String username;
        private final LocalDateTime createdAt;

        public SessionInfo(String sessionId, String username) {
            this.sessionId = sessionId;
            this.username = username;
            this.createdAt = LocalDateTime.now();
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUsername() {
            return username;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * Create a new session with the given username.
     */
    public SessionInfo createSession(String username) {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session: {}", sessionId);
        return new SessionInfo(sessionId, username);
    }

    /**
     * Validate a session ID format (basic UUID check).
     */
    public boolean isValidSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(sessionId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
