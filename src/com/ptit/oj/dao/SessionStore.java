package com.ptit.oj.dao;

import java.util.Optional;

/**
 * Noi luu phien dang nhap de khoi dong lai may chu (deploy) khong dang xuat moi nguoi.
 *
 * Khoa la BAN BAM SHA-256 cua token, khong bao gio la token goc: ai doc duoc bang nay (sao luu CSDL,
 * lo mat khau CSDL) cung khong dung duoc phien cua nguoi khac. Thoi gian la mili giay epoch, khong phu
 * thuoc mui gio cua CSDL.
 */
public interface SessionStore {

    /** Mot phien da luu. */
    final class StoredSession {
        private final String userId;
        private final long expiresAtMs;
        private final boolean remember;

        public StoredSession(String userId, long expiresAtMs, boolean remember) {
            this.userId = userId;
            this.expiresAtMs = expiresAtMs;
            this.remember = remember;
        }

        public String getUserId() { return userId; }
        public long getExpiresAtMs() { return expiresAtMs; }
        public boolean isRemember() { return remember; }
    }

    void save(String tokenHash, String userId, long createdAtMs, long expiresAtMs, boolean remember);

    Optional<StoredSession> find(String tokenHash);

    /** Gia han phien (het han truot: moi lan dung lai keo dai them). */
    void extend(String tokenHash, long expiresAtMs);

    void delete(String tokenHash);

    /** Xoa moi phien cua mot tai khoan, tru phien keepHash (null = xoa het). */
    void deleteUser(String userId, String keepHash);

    /** Chi giu toi da "keep" phien moi nhat cua mot tai khoan: chan bang phinh ra vi dang nhap lien tuc. */
    void trimUser(String userId, int keep);

    void purgeExpired(long nowMs);
}
