package com.pingpongsmt.dto;

/**
 * SSE 消息体
 */
public class SseMessage {

    private String content;
    private boolean done;

    public SseMessage() {
    }

    public SseMessage(String content, boolean done) {
        this.content = content;
        this.done = done;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
