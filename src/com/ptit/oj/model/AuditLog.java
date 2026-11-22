package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Mot dong nhat ky hoat dong (Module 40): ai, lam gi, luc nao, thanh cong hay khong. */
public class AuditLog extends Entity {

    private final LocalDateTime time = LocalDateTime.now();
    private final String username;
    private final String action;
    private final String detail;
    private final boolean success;

    public AuditLog(String id, String username, String action, String detail, boolean success) {
        super(id);
        this.username = username;
        this.action = action;
        this.detail = detail;
        this.success = success;
    }

    public LocalDateTime getTime() { return time; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public boolean isSuccess() { return success; }
}
