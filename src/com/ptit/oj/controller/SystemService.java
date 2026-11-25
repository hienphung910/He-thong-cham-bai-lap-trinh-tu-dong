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
    /** Tu cam bo sung cho bo loc binh luan, cach nhau bang dau phay hoac xuong dong (xem CommentFilter). */
    public static final String BANNED_WORDS = "bannedWords";
    public static final String BANNED_WORDS_ENV = "OJ_BANNED_WORDS";

    /** Chi giu so dong nhat ky gan nhat nay de bo nho khong tang mai. */
    private static final int MAX_AUDIT_LOGS = 2000;

    /** File nhat ky lon hon muc nay thi doi ten thanh .1 (ghi de ban .1 cu) va mo file moi. */
    private static final long MAX_AUDIT_FILE_BYTES = 5L * 1024 * 1024;

    private final long startedAt = System.currentTimeMillis();
    private final Map<String, String> settings = new LinkedHashMap<>();
    private final List<AuditLog> auditLogs = new ArrayList<>();
    /**
     * Nhat ky con duoc ghi noi tiep ra file nay (moi dong mot JSON), de khoi dong lai khong mat va de
     * mot loat dang nhap sai khong day het lich su cu ra khoi 2000 dong trong RAM. null = chi giu trong RAM.
     */
    private final java.nio.file.Path auditFile;
    private boolean auditFileWarned;

    /**
     * Gia tri khoi dau cua "cho tu dang ky". Cau hinh nam trong RAM nen moi lan khoi
     * dong lai deu quay ve gia tri nay; dat OJ_ALLOW_REGISTER=false khi dua len mang
     * de viec khoi dong lai khong tu mo dang ky.
     */
    public static final String ALLOW_REGISTER_ENV = "OJ_ALLOW_REGISTER";

    public SystemService() {
        this(null);
    }

    public SystemService(java.nio.file.Path auditFile) {
        this.auditFile = auditFile;
        settings.put(ALLOW_REGISTER, defaultAllowRegister() ? "true" : "false");
        settings.put(ANNOUNCEMENT, "");
        // Cau hinh nam trong RAM: muon danh sach tu cam bo sung con sau khi khoi dong lai thi dat OJ_BANNED_WORDS.
        String banned = com.ptit.oj.dao.DatabaseConfig.setting(BANNED_WORDS_ENV);
        settings.put(BANNED_WORDS, banned == null ? "" : clip(banned.trim(), 500));
        loadAuditFile();
    }

    /**
     * Co dat OJ_ALLOW_REGISTER thi theo dung gia tri do. Khong dat: mo, TRU KHI may chu dang mo ra
     * mang ma bai nop khong duoc co lap (Sandbox tat) - luc do ai dang ky duoc cung chay duoc ma tuy y
     * bang quyen cua may chu, nen mac dinh phai dong va nguoi van hanh tu quyet dinh mo.
     */
    static boolean defaultAllowRegister() {
        String configured = com.ptit.oj.dao.DatabaseConfig.setting(ALLOW_REGISTER_ENV);
        if (configured != null && !configured.trim().isEmpty()) {
            return !"false".equalsIgnoreCase(configured.trim());
        }
        return !(exposedToNetwork() && !com.ptit.oj.controller.judge.Sandbox.isActive());
    }

    /** May chu co nghe tren dia chi ngoai loopback khong (OJ_BIND_ADDRESS; image Docker dat 0.0.0.0). */
    public static boolean exposedToNetwork() {
        String bind = com.ptit.oj.dao.DatabaseConfig.setting("OJ_BIND_ADDRESS");
        if (bind == null || bind.trim().isEmpty()) return false;
        String b = bind.trim();
        return !(b.equals("127.0.0.1") || b.equalsIgnoreCase("localhost") || b.equals("::1"));
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

    /** Cac tu cam quan tri vien bo sung cho bo loc binh luan. */
    public List<String> bannedWords() {
        List<String> words = new ArrayList<>();
        for (String w : getSetting(BANNED_WORDS).split("[,;\\n]")) {
            if (!w.trim().isEmpty()) words.add(w.trim());
        }
        return words;
    }

    /**
     * Nap lai cau hinh da luu (StateStore). Thong bao chung va tu cam: lay ban da luu (quan tri vien
     * da sua tren web). Cho tu dang ky: neu OJ_ALLOW_REGISTER duoc dat ro rang thi bien moi truong
     * thang, de nguoi van hanh luon khoa / mo duoc bang cau hinh trien khai.
     */
    synchronized void restoreSettings(Map<String, String> saved) {
        String configured = com.ptit.oj.dao.DatabaseConfig.setting(ALLOW_REGISTER_ENV);
        boolean envDecides = configured != null && !configured.trim().isEmpty();
        for (Map.Entry<String, String> e : saved.entrySet()) {
            if (!settings.containsKey(e.getKey()) || e.getValue() == null) continue;
            if (e.getKey().equals(ALLOW_REGISTER) && envDecides) continue;
            settings.put(e.getKey(), e.getValue());
        }
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
        info.put("Cô lập bài nộp", com.ptit.oj.controller.judge.Sandbox.describe());
        return info;
    }

    // -------------------------------------------------------------- nhat ky

    public synchronized void log(String username, String action, String detail, boolean success) {
        // Ten dang nhap sai lay thang tu body cua request chua dang nhap: cat ngan de mot nguoi la
        // khong the nhoi day bo nho bang cac lan dang nhap voi ten dai hang tram KB.
        AuditLog entry = new AuditLog(IdGenerator.next("LOG"), clip(username, 64), clip(action, 200),
                clip(detail, 500), success);
        auditLogs.add(entry);
        if (auditLogs.size() > MAX_AUDIT_LOGS) auditLogs.remove(0);
        appendToFile(entry);
    }

    private void appendToFile(AuditLog entry) {
        if (auditFile == null) return;
        try {
            java.nio.file.Files.createDirectories(auditFile.getParent());
            if (java.nio.file.Files.exists(auditFile) && java.nio.file.Files.size(auditFile) > MAX_AUDIT_FILE_BYTES) {
                java.nio.file.Files.move(auditFile, auditFile.resolveSibling(auditFile.getFileName() + ".1"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            String line = Json.obj().put("time", entry.getTime().toString()).put("username", entry.getUsername())
                    .put("action", entry.getAction()).put("detail", entry.getDetail())
                    .put("success", entry.isSuccess()).build() + "\n";
            java.nio.file.Files.write(auditFile, line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException | RuntimeException e) {
            if (!auditFileWarned) {
                auditFileWarned = true;
                System.err.println("Không ghi được nhật ký ra " + auditFile + ": " + e.getMessage()
                        + " (nhật ký vẫn được giữ trong bộ nhớ)");
            }
        }
    }

    /** Luc khoi dong: nap lai cac dong gan nhat tu file de trang Nhat ky khong trong sau moi lan khoi dong lai. */
    private void loadAuditFile() {
        if (auditFile == null || !java.nio.file.Files.isRegularFile(auditFile)) return;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(auditFile, java.nio.charset.StandardCharsets.UTF_8);
            for (String line : lines.subList(Math.max(0, lines.size() - MAX_AUDIT_LOGS), lines.size())) {
                try {
                    Json.Value v = Json.parseObject(line);
                    auditLogs.add(new AuditLog(IdGenerator.next("LOG"),
                            java.time.LocalDateTime.parse(v.getString("time", "")), v.getString("username", ""),
                            v.getString("action", ""), v.getString("detail", ""), v.getBoolean("success", false)));
                } catch (RuntimeException ignored) {
                    // dong hong (may tat giua luc ghi): bo qua dong do
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("Không đọc được nhật ký cũ ở " + auditFile + ": " + e.getMessage());
        }
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
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
