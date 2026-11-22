package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Mot thong bao gui toi mot nguoi dung (Module 29). */
public class Notification extends Entity {

    private final String userId;
    private final String title;
    private final String body;
    private final LocalDateTime createdAt = LocalDateTime.now();
    private boolean read = false;

    public Notification(String id, String userId, String title, String body) {
        super(id);
        this.userId = userId;
        this.title = title;
        this.body = body;
    }

    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public void markRead() { this.read = true; }
}
