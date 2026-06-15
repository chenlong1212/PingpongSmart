package com.pingpongsmt.repository;

import com.pingpongsmt.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for chat message persistence.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Find all messages for a session, ordered by creation time ascending.
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Find recent messages for a session with a limit.
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable);
}
