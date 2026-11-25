package com.ptit.oj.controller;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Xac minh email khoi phuc: email nguoi dung khai (luc dang ky hoac khi doi) CHUA co hieu luc cho toi
 * khi ho bam lien ket trong thu gui toi chinh dia chi do.
 *
 * Vi sao can: khong xac minh thi ai cung khai duoc email cua nguoi khac - chiem cho cua dia chi do
 * (chu that khong dang ky duoc bang email cua minh) va khien chu that nhan thu dat lai mat khau cua
 * mot tai khoan la.
 *
 *  - Email dang cho chi nam o day, CHUA ghi vao tai khoan; nhieu tai khoan cung cho mot dia chi duoc,
 *    ai bam lien ket truoc thi dia chi thuoc ve tai khoan do.
 *  - Ma la 32 byte ngau nhien, may chu chi giu ban bam SHA-256; het han sau 24 gio, dung mot lan.
 *    Moi tai khoan chi co mot email dang cho: khai email khac (hoac gui lai thu) thi ma cu bi huy.
 *  - Chi giu trong RAM: khoi dong lai may chu thi nguoi dung bam "Gui lai thu xac minh" o trang tai
 *    khoan, hoac khai lai email.
 */
public final class EmailVerificationService {

    public static final long TTL_MS = 24 * 60 * 60_000L;
    private static final int MAX_PENDING = 20_000;
    private static final int MAX_TOKEN_CHARS = 100;

    /** Mot email dang cho xac minh. */
    public static final class Pending {
        private final String userId;
        private final String email;
        private final long expiresAt;

        Pending(String userId, String email, long expiresAt) {
            this.userId = userId;
            this.email = email;
            this.expiresAt = expiresAt;
        }

        public String getUserId() { return userId; }
        public String getEmail() { return email; }
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> byHash = new HashMap<>();

    /** Ghi nhan email dang cho cua mot tai khoan (thay cho email dang cho cu) va tra ve ma GOC de dua vao link. */
    public synchronized String issue(String userId, String email) {
        purgeExpired();
        revokeUser(userId);
        if (byHash.size() >= MAX_PENDING) {
            throw new ApiException(503, "Đang có quá nhiều email chờ xác minh. Hãy thử lại sau");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        byHash.put(PasswordResetService.hash(token), new Pending(userId, email, System.currentTimeMillis() + TTL_MS));
        return token;
    }

    /** Dung ma: tra ve tai khoan + email va huy ma ngay. Rong neu ma sai, het han hoac da dung. */
    public synchronized Optional<Pending> consume(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_CHARS) return Optional.empty();
        Pending pending = byHash.remove(PasswordResetService.hash(token));
        if (pending == null || pending.expiresAt < System.currentTimeMillis()) return Optional.empty();
        return Optional.of(pending);
    }

    /** Email dang cho xac minh cua mot tai khoan; "" neu khong co. */
    public synchronized String pendingEmailOf(String userId) {
        long now = System.currentTimeMillis();
        for (Pending p : byHash.values()) {
            if (p.userId.equals(userId) && p.expiresAt >= now) return p.email;
        }
        return "";
    }

    /** Huy email dang cho cua mot tai khoan (tai khoan bi xoa, khai email khac...). */
    public synchronized void revokeUser(String userId) {
        for (Iterator<Pending> it = byHash.values().iterator(); it.hasNext();) {
            if (it.next().userId.equals(userId)) it.remove();
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Pending> it = byHash.values().iterator(); it.hasNext();) {
            if (it.next().expiresAt < now) it.remove();
        }
    }
}
