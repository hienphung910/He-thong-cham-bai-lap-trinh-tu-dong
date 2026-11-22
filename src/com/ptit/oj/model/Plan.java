package com.ptit.oj.model;

/** Cac goi dich vu cua he thong (Module 34). Gia tinh theo VND / thang. */
public enum Plan {
    FREE("Miễn phí", 0, "Luyện tập 2 lớp học, nộp bài không giới hạn"),
    PRO("Học viên Pro", 99_000, "Thêm trợ lý AI, phân tích điểm yếu và gợi ý bài tập"),
    CENTER("Trung tâm", 499_000, "Quản lý nhiều lớp, phòng học trực tuyến, báo cáo cho phụ huynh");

    private final String displayName;
    private final long price;
    private final String features;

    Plan(String displayName, long price, String features) {
        this.displayName = displayName;
        this.price = price;
        this.features = features;
    }

    public String getDisplayName() { return displayName; }
    public long getPrice() { return price; }
    public String getFeatures() { return features; }
}
