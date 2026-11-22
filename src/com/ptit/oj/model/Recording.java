package com.ptit.oj.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ban ghi NOI DUNG cua mot buoi hoc (Module 37): ma nguon cuoi buoi, bang trang
 * va noi dung chat. Day la ban mo phong - khong ghi hinh / ghi am.
 */
public class Recording extends Entity {

    private final String meetingId;
    private final String classroomId;
    private final String title;
    private final long durationMin;
    private final String code;
    private final String language;
    private final List<Stroke> strokes;
    private final List<ChatMessage> chat;
    private final LocalDateTime createdAt = LocalDateTime.now();

    public Recording(String id, Meeting meeting, long durationMin, String code,
                     String language, List<Stroke> strokes, List<ChatMessage> chat) {
        super(id);
        this.meetingId = meeting.getId();
        this.classroomId = meeting.getClassroomId();
        this.title = meeting.getTitle();
        this.durationMin = durationMin;
        this.code = code;
        this.language = language;
        this.strokes = strokes;
        this.chat = chat;
    }

    public String getMeetingId() { return meetingId; }
    public String getClassroomId() { return classroomId; }
    public String getTitle() { return title; }
    public long getDurationMin() { return durationMin; }
    public String getCode() { return code; }
    public String getLanguage() { return language; }
    public List<Stroke> getStrokes() { return strokes; }
    public List<ChatMessage> getChat() { return chat; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
