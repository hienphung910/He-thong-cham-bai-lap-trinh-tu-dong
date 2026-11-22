package com.ptit.oj.model;

import java.time.LocalDateTime;
import java.util.List;

/** Bai tap giao cho mot lop (Module 13): danh sach bai can lam truoc han nop. */
public class Assignment extends Entity {

    private final String classroomId;
    private final String title;
    private final String description;
    private final List<String> problemIds;
    private final LocalDateTime dueAt;
    private final String createdBy;        // ho ten nguoi giao

    public Assignment(String id, String classroomId, String title, String description,
                      List<String> problemIds, LocalDateTime dueAt, String createdBy) {
        super(id);
        this.classroomId = classroomId;
        this.title = title;
        this.description = description;
        this.problemIds = problemIds;
        this.dueAt = dueAt;
        this.createdBy = createdBy;
    }

    public String getClassroomId() { return classroomId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getProblemIds() { return problemIds; }
    public LocalDateTime getDueAt() { return dueAt; }
    public String getCreatedBy() { return createdBy; }

    public boolean isOverdue() { return LocalDateTime.now().isAfter(dueAt); }
}
