package com.ptit.oj.controller;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quan ly phien dang nhap.
 *
 * Day la ung dung chay cuc bo nen phien duoc giu trong mot HashMap (token -> phien)
 * (tat chuong trinh la phai dang nhap lai - chap nhan duoc). Cua so chinh giu
 * token cua phien va kiem tra lai moi lan chuyen trang. Token van lam nghiem tuc:
 *   - sinh bang SecureRandom 32 byte (khong dung Math.random / UUID),
 *   - co han su dung, het han la khong dung duoc nua,
 *   - luu userId chu khong luu username, de doi ten khong lam hong phien.
 */
public class SessionService {

    private static final int TOKEN_BYTES = 32;

    private Map<String, Session> sessions = new HashMap<>();
    private SecureRandom random = new SecureRandom();
    /** Phien het han sau 8 gio ke tu luc dang nhap. */
    private Duration ttl = Duration.ofHours(8);

    /** Mot phien dang nhap dang song. */
    public static class Session {
        private String userId;
        private Instant expiresAt;

        private Session(String userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }

        public String getUserId() { return userId; }
        public Instant getExpiresAt() { return expiresAt; }
        public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    /** Tao token moi cho mot tai khoan. */
    public String createToken(String userId) {
        purgeExpired();
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(userId, Instant.now().plus(ttl)));
        return token;
    }

    /** Tra ve userId neu token con hieu luc, null neu token sai hoac phien da het han. */
    public String resolve(String token) {
        if (token == null || token.isEmpty()) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (session.isExpired()) {
            sessions.remove(token);
            return null;
        }
        return session.getUserId();
    }

    /** Dang xuat. Tra ve true neu token dang ton tai. */
    public boolean invalidate(String token) {
        return token != null && sessions.remove(token) != null;
    }

    private void purgeExpired() {
        // Gom cac token het han truoc roi moi xoa, vi khong duoc xoa khi dang duyet map.
        List<String> expired = new ArrayList<>();
        for (String token : sessions.keySet()) {
            if (sessions.get(token).isExpired()) expired.add(token);
        }
        for (String token : expired) {
            sessions.remove(token);
        }
    }
}
