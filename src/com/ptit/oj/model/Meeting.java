package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Buoi hoc truc tuyen cua mot lop (Module 15). Trang thai: SCHEDULED -> LIVE -> ENDED. */
public class Meeting extends Entity {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String LIVE = "LIVE";
    public static final String ENDED = "ENDED";

    private final String classroomId;
    private final String title;
    private final LocalDateTime startAt;
    private final int durationMin;
    private final String hostId;
    private final boolean recorded;        // co luu ban ghi noi dung khi ket thuc khong
    private String status = SCHEDULED;
    private LocalDateTime startedAt;       // luc giang vien bam "Bat dau"; null neu chua bat dau

    public Meeting(String id, String classroomId, String title, LocalDateTime startAt,
                   int durationMin, String hostId, boolean recorded) {
        super(id);
        this.classroomId = classroomId;
        this.title = title;
        this.startAt = startAt;
        this.durationMin = durationMin;
        this.hostId = hostId;
        this.recorded = recorded;
    }

    public String getClassroomId() { return classroomId; }
    public String getTitle() { return title; }
    public LocalDateTime getStartAt() { return startAt; }
    public int getDurationMin() { return durationMin; }
    public String getHostId() { return hostId; }
    public boolean isRecorded() { return recorded; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isLive() { return LIVE.equals(status); }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    /**
     * Moc de tinh di muon: gio hen, hoac luc that su bat dau neu giang vien mo phong tre hon gio hen.
     * (Giang vien mo muon thi hoc sinh khong bi tinh la di muon.)
     */
    public LocalDateTime getEffectiveStart() {
        return startedAt != null && startedAt.isAfter(startAt) ? startedAt : startAt;
    }
}
