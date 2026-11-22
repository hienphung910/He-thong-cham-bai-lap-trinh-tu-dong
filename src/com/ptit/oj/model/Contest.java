package com.ptit.oj.model;

import java.time.LocalDateTime;
import java.util.List;

/** Cuoc thi lap trinh (Module 21): mot khoang thoi gian + danh sach bai. */
public class Contest extends Entity {

    private final String title;
    private final String description;
    private final LocalDateTime startAt;
    private final int durationMin;
    private final List<String> problemIds;

    public Contest(String id, String title, String description, LocalDateTime startAt,
                   int durationMin, List<String> problemIds) {
        super(id);
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.durationMin = durationMin;
        this.problemIds = problemIds;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getStartAt() { return startAt; }
    public int getDurationMin() { return durationMin; }
    public List<String> getProblemIds() { return problemIds; }

    public LocalDateTime getEndAt() { return startAt.plusMinutes(durationMin); }

    /** UPCOMING (chua bat dau) / RUNNING (dang thi) / ENDED (da ket thuc). */
    public String getStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt)) return "UPCOMING";
        return now.isBefore(getEndAt()) ? "RUNNING" : "ENDED";
    }
}
