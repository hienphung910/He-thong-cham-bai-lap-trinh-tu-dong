package com.ptit.oj.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Ma dat lai mat khau gui qua email ("Quen mat khau").
 *
 *  - Ma la 32 byte ngau nhien; may chu CHI giu ban bam SHA-256, nen ai doc duoc bo nho / nhat ky
 *    cung khong lay duoc ma con dung.
 *  - Het han sau 30 phut, dung duoc MOT LAN. Xin ma moi thi ma cu cua tai khoan do bi huy.
 *  - Chi giu trong RAM: khoi dong lai may chu thi cac ma dang cho mat hieu luc, nguoi dung xin lai.
 */
public final class PasswordResetService {

    public static final long TTL_MS = 30 * 60_000L;
    /** Tran so ma dang cho, de khong ai lam day bo nho bang cach xin ma lien tuc. */
    private static final int MAX_PENDING = 5000;
    /** Ma that dai 43 ky tu; chuoi dai hon chac chan sai, khong can bam. */
    private static final int MAX_TOKEN_CHARS = 100;

    private static final class Entry {
        private final String userId;
        private final long expiresAt;

        Entry(String userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> byHash = new HashMap<>();

    /** Cap ma moi cho tai khoan va huy cac ma cu cua tai khoan do. Tra ve ma GOC de dua vao link trong thu. */
    public synchronized String issue(String userId) {
        purgeExpired();
        revokeUser(userId);
        if (byHash.size() >= MAX_PENDING) {
            throw new ApiException(503, "Đang có quá nhiều yêu cầu đặt lại mật khẩu. Hãy thử lại sau");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        byHash.put(hash(token), new Entry(userId, System.currentTimeMillis() + TTL_MS));
        return token;
    }

    /** Ma nay con dung duoc khong; khong lam mat hieu luc cua ma. */
    public synchronized boolean isValid(String token) {
        return find(token) != null;
    }

    /** Ma nay thuoc tai khoan nao; khong lam mat hieu luc cua ma. Rong neu ma sai / het han. */
    public synchronized Optional<String> peek(String token) {
        Entry entry = find(token);
        return entry == null ? Optional.<String>empty() : Optional.of(entry.userId);
    }

    /** Dung ma: tra ve ma tai khoan va huy ma ngay. Rong neu ma sai, het han hoac da dung. */
    public synchronized Optional<String> consume(String token) {
        Entry entry = find(token);
        if (entry == null) return Optional.empty();
        byHash.remove(hash(token));
        return Optional.of(entry.userId);
    }

    /** Huy moi ma dang cho cua mot tai khoan. */
    public synchronized void revokeUser(String userId) {
        for (Iterator<Entry> it = byHash.values().iterator(); it.hasNext();) {
            if (it.next().userId.equals(userId)) it.remove();
        }
    }

    private Entry find(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_CHARS) return null;
        Entry entry = byHash.get(hash(token));
        if (entry == null) return null;
        if (entry.expiresAt < System.currentTimeMillis()) {
            byHash.remove(hash(token));
            return null;
        }
        return entry;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Entry> it = byHash.values().iterator(); it.hasNext();) {
            if (it.next().expiresAt < now) it.remove();
        }
    }

    /** SHA-256 dang hex. Cung dung lam khoa chong gui trung (idempotency key) cho thu chua ma do. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK thiếu SHA-256", e);
        }
    }
}
