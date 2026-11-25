package com.ptit.oj.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cuoc thi lap trinh (Module 21): mot khoang thoi gian + danh sach bai, to chuc cho MOT lop.
 * Chi thanh vien cua lop do (va quan tri vien) thay ky thi; chi hoc sinh cua lop vao thi duoc.
 * Ky thi thuoc ve mot LOP: giang vien phu trach lop, nguoi tao ky thi (khi con day lop do) va quan
 * tri vien quan ly duoc - xem ContestService.canManage.
 */
public class Contest extends Entity {

    private final String title;
    private final String description;
    /** Gio bat dau va thoi luong sua duoc truoc khi thi; dang thi thi chi gia han duoc (ContestService.reschedule). */
    private volatile LocalDateTime startAt;
    private volatile int durationMin;
    private final List<String> problemIds;
    private final String ownerId;           // ma tai khoan cua nguoi tao (khong dung ten dang nhap: ten co the bi dang ky lai)
    private final String ownerName;         // ho ten nguoi tao, de hien thi
    private final String classroomId;       // lop duoc thi
    /** Nguoi tao ky thi bat / tat duoc: co ghi lai viec roi tab, sao chep, dan... cua thi sinh hay khong. */
    private volatile boolean proctored = true;
    /**
     * Thi sinh da bam "Vao thi": userId -> luc vao. Tu luc do toi khi nop bai som hoac het gio,
     * giao dien cua thi sinh chi con man hinh ky thi.
     */
    private final Map<String, LocalDateTime> joinedAt = new ConcurrentHashMap<>();
    /** Thi sinh da nop bai som: userId -> luc nop. Bai nop sau thoi diem nay khong tinh vao ky thi. */
    private final Map<String, LocalDateTime> finishedAt = new ConcurrentHashMap<>();

    public Contest(String id, String title, String description, LocalDateTime startAt,
                   int durationMin, List<String> problemIds, String ownerId, String ownerName,
                   String classroomId) {
        super(id);
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.durationMin = durationMin;
        // Ban sao rieng de them / bot bai ve sau; CopyOnWrite de request khac doc duoc trong luc sua.
        this.problemIds = new CopyOnWriteArrayList<>(problemIds);
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.classroomId = classroomId;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getStartAt() { return startAt; }
    public int getDurationMin() { return durationMin; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }
    public List<String> getProblemIds() { return problemIds; }
    public String getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getClassroomId() { return classroomId; }

    public boolean isProctored() { return proctored; }
    public void setProctored(boolean proctored) { this.proctored = proctored; }

    public Map<String, LocalDateTime> getJoinedAt() { return joinedAt; }
    public Map<String, LocalDateTime> getFinishedAt() { return finishedAt; }
    public boolean hasJoined(String userId) { return joinedAt.containsKey(userId); }
    public boolean hasFinished(String userId) { return finishedAt.containsKey(userId); }

    /** Thi sinh dang trong bai thi: da vao, chua nop bai som, ky thi con dang dien ra. */
    public boolean isSitting(String userId) {
        return "RUNNING".equals(getStatus()) && hasJoined(userId) && !hasFinished(userId);
    }

    public LocalDateTime getEndAt() { return startAt.plusMinutes(durationMin); }

    /** UPCOMING (chua bat dau) / RUNNING (dang thi) / ENDED (da ket thuc). */
    public String getStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt)) return "UPCOMING";
        return now.isBefore(getEndAt()) ? "RUNNING" : "ENDED";
    }
}
