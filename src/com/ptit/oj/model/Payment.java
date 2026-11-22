package com.ptit.oj.model;

import java.time.LocalDateTime;

/** Mot giao dich mua goi dich vu (Module 34). Cong thanh toan la MO PHONG, khong co tien that. */
public class Payment extends Entity {

    private final String userId;
    private final Plan plan;
    private final long amount;
    private final LocalDateTime createdAt = LocalDateTime.now();

    public Payment(String id, String userId, Plan plan) {
        super(id);
        this.userId = userId;
        this.plan = plan;
        this.amount = plan.getPrice();
    }

    public String getUserId() { return userId; }
    public Plan getPlan() { return plan; }
    public long getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** Goi co hieu luc 30 ngay ke tu luc thanh toan. */
    public LocalDateTime getExpiresAt() { return createdAt.plusDays(30); }
    public boolean isActive() { return LocalDateTime.now().isBefore(getExpiresAt()); }
}
