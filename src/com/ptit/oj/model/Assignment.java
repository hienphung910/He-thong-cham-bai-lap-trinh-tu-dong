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
    /** Luc giao bai: bai AC truoc thoi diem nay la "da giai tu truoc", khong tinh la lam bai tap nay. */
    private final LocalDateTime createdAt = LocalDateTime.now();

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
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * Trang thai cua MOT bai trong bai tap giao doi voi mot hoc sinh, tinh tu cac thoi diem em do AC bai ay:
     *   DONE    - co lan AC trong khoang tu luc giao toi han nop (lam dung han);
     *   LATE    - chi AC sau han nop;
     *   EARLIER - chi AC truoc khi bai duoc giao (da giai tu truoc, chua lam lai);
     *   TODO    - chua AC lan nao.
     */
    public String statusOf(List<LocalDateTime> acceptedAt) {
        if (acceptedAt == null) return "TODO";
        boolean late = false;
        boolean earlier = false;
        for (LocalDateTime at : acceptedAt) {
            if (at.isBefore(createdAt)) earlier = true;
            else if (!at.isAfter(dueAt)) return "DONE";
            else late = true;
        }
        return late ? "LATE" : earlier ? "EARLIER" : "TODO";
    }

    public boolean isOverdue() { return LocalDateTime.now().isAfter(dueAt); }
}
