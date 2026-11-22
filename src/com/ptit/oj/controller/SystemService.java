package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.model.AuditLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quan tri he thong va nhat ky hoat dong (Module 39, 40).
 *
 *  - Cau hinh: vai cap khoa/gia tri ma quan tri vien doi duoc luc dang chay.
 *  - Nhat ky : ApiServer ghi mot dong cho moi request lam thay doi du lieu
 *              (POST / PUT / DELETE) va cho moi lan dang nhap.
 */
public class SystemService {

    public static final String ALLOW_REGISTER = "allowRegister";
    public static final String ANNOUNCEMENT = "announcement";

    /** Chi giu so dong nhat ky gan nhat nay de bo nho khong tang mai. */
    private static final int MAX_AUDIT_LOGS = 2000;

    private final long startedAt = System.currentTimeMillis();
    private final Map<String, String> settings = new LinkedHashMap<>();
    private final List<AuditLog> auditLogs = new ArrayList<>();

    public SystemService() {
        settings.put(ALLOW_REGISTER, "true");
        settings.put(ANNOUNCEMENT, "");
    }

    // ------------------------------------------------------------- cau hinh

    public synchronized Map<String, String> getSettings() {
        return new LinkedHashMap<>(settings);
    }

    public synchronized String getSetting(String key) {
        return settings.getOrDefault(key, "");
    }

    /** Chi cho doi nhung khoa da khai bao san, khong cho them khoa la. */
    public synchronized void setSetting(String key, String value) {
        if (!settings.containsKey(key)) throw ApiException.badRequest("Không có cấu hình " + key);
        settings.put(key, Check.optional(value, "Giá trị cấu hình", 500));
    }

    public boolean isRegisterAllowed() {
        return "true".equals(getSetting(ALLOW_REGISTER));
    }

    // -------------------------------------------------------- thong tin may chu

    /** Thong tin ve may chu dang chay, theo thu tu hien thi. */
    public Map<String, String> systemInfo() {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long uptimeMin = (System.currentTimeMillis() - startedAt) / 60_000;

        Map<String, String> info = new LinkedHashMap<>();
        info.put("Phiên bản Java", System.getProperty("java.version"));
        info.put("Hệ điều hành", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        info.put("Số nhân CPU", String.valueOf(runtime.availableProcessors()));
        info.put("Bộ nhớ đang dùng", usedMb + " MB / " + runtime.maxMemory() / (1024 * 1024) + " MB");
        info.put("Thời gian đã chạy", uptimeMin / 60 + " giờ " + uptimeMin % 60 + " phút");
        return info;
    }

    // -------------------------------------------------------------- nhat ky

    public synchronized void log(String username, String action, String detail, boolean success) {
        auditLogs.add(new AuditLog(IdGenerator.next("LOG"), username, action, detail, success));
        if (auditLogs.size() > MAX_AUDIT_LOGS) auditLogs.remove(0);
    }

    /** Nhat ky moi nhat truoc; keyword rong = lay het, khac rong = loc theo nguoi dung hoac hanh dong. */
    public synchronized List<AuditLog> getAuditLogs(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<AuditLog> result = new ArrayList<>();
        for (int i = auditLogs.size() - 1; i >= 0; i--) {
            AuditLog log = auditLogs.get(i);
            if (k.isEmpty() || log.getUsername().toLowerCase().contains(k)
                    || log.getAction().toLowerCase().contains(k)) {
                result.add(log);
            }
        }
        return result;
    }
}
