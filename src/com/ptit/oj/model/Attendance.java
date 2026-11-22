package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Ket qua diem danh cua mot hoc sinh trong mot buoi hoc (Module 20). */
public class Attendance extends Entity {

    public static final String PRESENT = "PRESENT";
    public static final String LATE = "LATE";
    public static final String ABSENT = "ABSENT";

    private final String meetingId;
    private final String userId;
    private final LocalDateTime joinedAt;  // null neu giang vien diem danh tay
    private String status;

    public Attendance(String meetingId, String userId, String status, LocalDateTime joinedAt) {
        super(meetingId + ":" + userId);   // moi hoc sinh chi co mot dong cho moi buoi
        this.meetingId = meetingId;
        this.userId = userId;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public String getMeetingId() { return meetingId; }
    public String getUserId() { return userId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
