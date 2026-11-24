package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Mot thong bao gui toi mot nguoi dung (Module 29). */
public class Notification extends Entity {

    private final String userId;
    private final String title;
    private final String body;
    /**
     * Trang mo ra khi nguoi dung bam vao thong bao: ten trang cua giao dien ("Meetings",
     * "Assignments", "Classrooms") hoac "Problems/<ma bai>". Rong = thong bao chi de doc.
     */
    private final String link;
    private final LocalDateTime createdAt = LocalDateTime.now();
    private boolean read = false;

    public Notification(String id, String userId, String title, String body, String link) {
        super(id);
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.link = link == null ? "" : link;
    }

    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public void markRead() { this.read = true; }
}
