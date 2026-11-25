package com.ptit.oj.model;

import java.time.LocalDateTime;

/**
 * Binh luan cua nguoi dung duoi mot bai tap.
 *
 * Binh luan KHONG qua nguoi duyet: may chu loc tu bay va ma nguon loi giai luc gui (CommentFilter),
 * qua duoc thi hien ngay cho moi nguoi mo duoc bai. Truong status luon la APPROVED, giu lai de giao
 * dien cu doc duoc.
 */
public class ProblemComment extends Entity {

    /** Lop nay duoc luu xuong o dia (StateStore): giu nguyen so nay de ban luu cu van doc duoc khi them truong. */
    private static final long serialVersionUID = 1L;

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
}
