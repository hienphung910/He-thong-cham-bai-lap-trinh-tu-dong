package com.ptit.oj.controller;

import com.ptit.oj.dao.DataAccessException;
import com.ptit.oj.dao.SessionStore;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Quan ly phien dang nhap.
 *
 *  - Token: 32 byte SecureRandom (khong dung Math.random / UUID). Noi luu (SessionStore: MySQL, hoac RAM
 *    voi --no-db) chi giu BAN BAM SHA-256 cua token, nen lo CSDL cung khong dung duoc phien nao.
 *  - Het han TRUOT, nhu cac nen tang lon: moi lan dung lai keo dai them. "Ghi nho dang nhap": 30 ngay ke tu
 *    lan dung gan nhat; khong ghi nho: 12 gio (va trinh duyet tu xoa token khi dong tab).
 *  - Phien luu trong MySQL nen khoi dong lai / deploy may chu KHONG dang xuat ai.
 *  - Bo nho dem trong RAM: moi request khong phai hoi CSDL; chi hoi khi gap token lan dau (vd. ngay sau
 *    khi deploy lai), va ghi CSDL de gia han toi da 10 phut mot lan cho moi phien.
 *  - Luu userId chu khong luu username, de doi ten khong lam hong phien.
 */
public class SessionService {

    private static final int TOKEN_BYTES = 32;
    /** Token that luon dai 43 ky tu base64url: chuoi khac chac chan sai, khong can hoi CSDL. */
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    public static final Duration REMEMBER_TTL = Duration.ofDays(30);
    public static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final long EXTEND_EVERY_MS = 10 * 60_000L;
    /** Moi tai khoan giu toi da chung nay phien (thiet bi); dang nhap them thi phien cu nhat bi bo. */
    private static final int MAX_SESSIONS_PER_USER = 20;
    private static final long PURGE_EVERY_MS = 60 * 60_000L;
    /** Token khong ton tai duoc nho 1 phut, de gui token bua lien tuc khong bat CSDL tra cuu moi lan. */
    private static final long MISS_CACHE_MS = 60_000L;
    private static final int MAX_MISSES = 10_000;

    /** Mot phien dang song trong bo nho dem. */
    private static final class Session {
        private final String userId;
        private final boolean remember;
        private volatile long expiresAtMs;
        private volatile long lastExtendMs;

        Session(String userId, boolean remember, long expiresAtMs, long lastExtendMs) {
            this.userId = userId;
            this.remember = remember;
            this.expiresAtMs = expiresAtMs;
            this.lastExtendMs = lastExtendMs;
        }
    }

    private final SessionStore store;
    private final SecureRandom random = new SecureRandom();
    /** Khoa: ban bam cua token. */
    private final Map<String, Session> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> misses = new ConcurrentHashMap<>();
    private volatile long lastPurgeMs;

    public SessionService(SessionStore store) {
        this.store = store;
    }

    private static long ttlMs(boolean remember) {
        return (remember ? REMEMBER_TTL : SESSION_TTL).toMillis();
    }

    /** Tao phien moi cho mot tai khoan. remember = "Ghi nho dang nhap". */
    public String createToken(String userId, boolean remember) {
        long now = System.currentTimeMillis();
        purgeIfDue(now);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = PasswordResetService.hash(token);
        long expires = now + ttlMs(remember);
        store.save(hash, userId, now, expires, remember);
        store.trimUser(userId, MAX_SESSIONS_PER_USER);
        cache.put(hash, new Session(userId, remember, expires, now));
        return token;
    }

    /**
     * Tra ve userId neu token con hieu luc (va gia han phien). Loi CSDL KHONG bi coi la "chua dang nhap":
     * no duoc nem tiep thanh HTTP 500, de trinh duyet khong xoa token chi vi CSDL tam thoi truc trac.
     */
    public Optional<String> resolve(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) return Optional.empty();
        String hash = PasswordResetService.hash(token);
        long now = System.currentTimeMillis();
        Session session = cache.get(hash);
        if (session == null) {
            Long missedAt = misses.get(hash);
            if (missedAt != null && now - missedAt < MISS_CACHE_MS) return Optional.empty();
            Optional<SessionStore.StoredSession> stored = store.find(hash);
            if (!stored.isPresent()) {
                rememberMiss(hash, now);
                return Optional.empty();
            }
            SessionStore.StoredSession s = stored.get();
            session = new Session(s.getUserId(), s.isRemember(), s.getExpiresAtMs(), 0);
            cache.put(hash, session);
        }
        if (session.expiresAtMs <= now) {
            cache.remove(hash);
            try {
                store.delete(hash);
            } catch (DataAccessException ignored) {
                // phien da het han; lan don dinh ky sau se xoa
            }
            return Optional.empty();
        }
        if (now - session.lastExtendMs >= EXTEND_EVERY_MS) {
            long expires = now + ttlMs(session.remember);
            try {
                store.extend(hash, expires);
                session.expiresAtMs = expires;
                session.lastExtendMs = now;
            } catch (DataAccessException e) {
                // Khong gia han duoc luc nay: phien van dung den han cu, request sau thu lai.
            }
        }
        return Optional.of(session.userId);
    }

    /** Dang xuat. */
    public boolean invalidate(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) return false;
        String hash = PasswordResetService.hash(token);
        boolean known = cache.remove(hash) != null;
        store.delete(hash);
        return known;
    }

    /**
     * Huy moi phien cua mot tai khoan, tru phien keepToken (null = huy het).
     * Goi khi doi mat khau, bi khoa / xoa / doi vai tro, hoac nguoi dung bam "Dang xuat cac thiet bi khac".
     */
    public void invalidateUser(String userId, String keepToken) {
        String keepHash = keepToken == null ? null : PasswordResetService.hash(keepToken);
        cache.entrySet().removeIf(e -> e.getValue().userId.equals(userId) && !e.getKey().equals(keepHash));
        store.deleteUser(userId, keepHash);
    }

    /** Doc header "Authorization: Bearer xxx" -> "xxx". */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = value.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private void rememberMiss(String hash, long now) {
        if (misses.size() >= MAX_MISSES) misses.clear();
        misses.put(hash, now);
    }

    /** Don phien het han trong bo nho dem va noi luu, toi da mot lan moi gio. */
    private void purgeIfDue(long now) {
        if (now - lastPurgeMs < PURGE_EVERY_MS) return;
        lastPurgeMs = now;
        for (Iterator<Session> it = cache.values().iterator(); it.hasNext();) {
            if (it.next().expiresAtMs <= now) it.remove();
        }
        misses.clear();
        try {
            store.purgeExpired(now);
        } catch (DataAccessException ignored) {
            // lan sau don tiep
        }
    }
}
