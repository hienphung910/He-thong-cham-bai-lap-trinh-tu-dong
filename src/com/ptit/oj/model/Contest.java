package com.ptit.oj.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuoc thi lap trinh (Module 21): mot khoang thoi gian + danh sach bai.
 * Moi cuoc thi co mot nguoi tao; chi nguoi do (hoac quan tri vien) duoc them / bot bai va xoa cuoc thi.
 */
public class Contest extends Entity {

    private final String title;
    private final String description;
    private final LocalDateTime startAt;
    private final int durationMin;
    private final List<String> problemIds;
    private final String ownerUsername;     // ten dang nhap cua nguoi tao
    private final String ownerName;         // ho ten nguoi tao, de hien thi

    public Contest(String id, String title, String description, LocalDateTime startAt,
                   int durationMin, List<String> problemIds, String ownerUsername, String ownerName) {
        super(id);
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.durationMin = durationMin;
        this.problemIds = new ArrayList<>(problemIds);     // ban sao rieng de them / bot bai ve sau
        this.ownerUsername = ownerUsername;
        this.ownerName = ownerName;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getStartAt() { return startAt; }
    public int getDurationMin() { return durationMin; }
    public List<String> getProblemIds() { return problemIds; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getOwnerName() { return ownerName; }

    public LocalDateTime getEndAt() { return startAt.plusMinutes(durationMin); }

    /** UPCOMING (chua bat dau) / RUNNING (dang thi) / ENDED (da ket thuc). */
    public String getStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt)) return "UPCOMING";
        return now.isBefore(getEndAt()) ? "RUNNING" : "ENDED";
    }
}
