package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Tai lieu hoc tap (Module 36): mot doan van ban hoac mot duong link, gan voi khoa hoc. */
public class Document extends Entity {

    public static final String KIND_TEXT = "TEXT";
    public static final String KIND_LINK = "LINK";

    private final String courseId;         // "" = tai lieu chung
    private final String title;
    private final String kind;
    private final String content;          // noi dung van ban hoac URL
    private final String authorName;
    private final LocalDateTime createdAt = LocalDateTime.now();

    public Document(String id, String courseId, String title, String kind, String content, String authorName) {
        super(id);
        this.courseId = courseId;
        this.title = title;
        this.kind = kind;
        this.content = content;
        this.authorName = authorName;
    }

    public String getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public String getKind() { return kind; }
    public String getContent() { return content; }
    public String getAuthorName() { return authorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
