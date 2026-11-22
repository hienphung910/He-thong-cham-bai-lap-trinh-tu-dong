package com.ptit.oj.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Mot chu de tren dien dan (Module 31) kem cac cau tra loi. */
public class ForumPost extends Entity {

    /** Mot cau tra loi trong chu de. */
    public static class Reply {
        private final String authorName;
        private final String body;
        private final LocalDateTime createdAt = LocalDateTime.now();

        public Reply(String authorName, String body) {
            this.authorName = authorName;
            this.body = body;
        }

        public String getAuthorName() { return authorName; }
        public String getBody() { return body; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    private final String title;
    private final String body;
    private final String authorId;
    private final String authorName;
    private final String problemId;        // "" neu khong hoi ve bai cu the
    private final LocalDateTime createdAt = LocalDateTime.now();
    private final List<Reply> replies = new ArrayList<>();

    public ForumPost(String id, String title, String body, String authorId, String authorName, String problemId) {
        super(id);
        this.title = title;
        this.body = body;
        this.authorId = authorId;
        this.authorName = authorName;
        this.problemId = problemId;
    }

    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getProblemId() { return problemId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<Reply> getReplies() { return replies; }
}
