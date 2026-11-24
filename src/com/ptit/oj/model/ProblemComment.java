package com.ptit.oj.model;

import java.time.LocalDateTime;

/**
 * Binh luan cua nguoi dung duoi mot bai tap.
 *
 * Binh luan moi o trang thai PENDING va chi tac gia thay; quan tri vien duyet thi chuyen sang
 * APPROVED va moi nguoi mo duoc bai deu thay. Binh luan bi tu choi thi xoa han.
 */
public class ProblemComment extends Entity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";

    private final String problemId;
    private final String authorId;
    private final String authorName;
    private final String authorRole;       // ten vai tro de hien thi: Sinh vien / Giang vien / Quan tri
    private final String text;
    private final LocalDateTime createdAt = LocalDateTime.now();
    private String status;

    public ProblemComment(String id, String problemId, User author, String text, String status) {
        super(id);
        this.problemId = problemId;
        this.authorId = author.getId();
        this.authorName = author.getFullName();
        this.authorRole = author.getRole();
        this.text = text;
        this.status = status;
    }

    public String getProblemId() { return problemId; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorRole() { return authorRole; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getStatus() { return status; }
    public boolean isApproved() { return APPROVED.equals(status); }
    public void approve() { this.status = APPROVED; }
}
