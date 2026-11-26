package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Mot tin nhan trong mot kenh chat (Module 30). */
public class ChatMessage extends Entity {

    /** Lop nay duoc luu xuong o dia (StateStore): giu nguyen so nay de ban luu cu van doc duoc khi them truong. */
    private static final long serialVersionUID = 1L;

    private final String channel;          // "general", "class-CLS-001", "meeting-MTG-001"
    private final String senderName;
    private final String senderRole;
    private final String text;
    private final LocalDateTime sentAt = LocalDateTime.now();
    /** So thu tu trong kenh (tu 1, tang dan): trinh duyet dung de chi hoi cac tin moi hon tin da nhan. */
    private volatile long seq;

    /** Ma tai khoan nguoi gui, de nguoi gui tu xoa tin cua minh. Tin luu tu ban cu chua co: null. */
    private final String senderId;

    public ChatMessage(String id, String channel, String senderId, String senderName, String senderRole, String text) {
        super(id);
        this.channel = channel;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.text = text;
    }

    public String getChannel() { return channel; }
    public String getSenderId() { return senderId == null ? "" : senderId; }
    public String getSenderName() { return senderName; }
    public String getSenderRole() { return senderRole; }
    public String getText() { return text; }
    public LocalDateTime getSentAt() { return sentAt; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
}
