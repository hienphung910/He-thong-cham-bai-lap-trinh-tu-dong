package com.ptit.oj.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Phien dang nhap trong MySQL (bang user_sessions): deploy lai may chu khong dang xuat ai.
 * Bang duoc tao luc khoi dong neu chua co; xoa tai khoan thi phien cua tai khoan do bay theo
 * (ON DELETE CASCADE).
 */
public class MySqlSessionStore implements SessionStore {

    static final String CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS user_sessions ("
      + "  token_hash  CHAR(64)     NOT NULL,"
      + "  user_id     VARCHAR(64)  NOT NULL,"
      + "  remember    TINYINT(1)   NOT NULL DEFAULT 0,"
      + "  created_ms  BIGINT       NOT NULL,"
      + "  expires_ms  BIGINT       NOT NULL,"
      + "  PRIMARY KEY (token_hash),"
      + "  KEY idx_user_sessions_user (user_id),"
      + "  KEY idx_user_sessions_expires (expires_ms),"
      + "  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE"
      + ") ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci";

    private final MySqlDatabaseManager database;

    public MySqlSessionStore(MySqlDatabaseManager database) {
        this.database = database;
        try (Connection c = database.open(); Statement st = c.createStatement()) {
            st.executeUpdate(CREATE_TABLE);
        } catch (SQLException e) {
            throw new DataAccessException("Không tạo được bảng user_sessions (phiên đăng nhập)", e);
        }
    }

    @Override
    public void save(String tokenHash, String userId, long createdAtMs, long expiresAtMs, boolean remember) {
        update("INSERT INTO user_sessions (token_hash, user_id, remember, created_ms, expires_ms) VALUES (?, ?, ?, ?, ?)",
                tokenHash, userId, remember, createdAtMs, expiresAtMs);
    }

    @Override
    public Optional<StoredSession> find(String tokenHash) {
        String sql = "SELECT user_id, expires_ms, remember FROM user_sessions WHERE token_hash = ?";
        try (Connection c = database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new StoredSession(rs.getString(1), rs.getLong(2), rs.getBoolean(3)));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không đọc được phiên đăng nhập", e);
        }
    }

    @Override
    public void extend(String tokenHash, long expiresAtMs) {
        update("UPDATE user_sessions SET expires_ms = ? WHERE token_hash = ?", expiresAtMs, tokenHash);
    }

    @Override
    public void delete(String tokenHash) {
        update("DELETE FROM user_sessions WHERE token_hash = ?", tokenHash);
    }

    @Override
    public void deleteUser(String userId, String keepHash) {
        if (keepHash == null) {
            update("DELETE FROM user_sessions WHERE user_id = ?", userId);
        } else {
            update("DELETE FROM user_sessions WHERE user_id = ? AND token_hash <> ?", userId, keepHash);
        }
    }

    @Override
    public void trimUser(String userId, int keep) {
        // MySQL khong cho LIMIT trong truy van con cua DELETE: lay moc thoi gian cua phien thu "keep" roi xoa cu hon.
        String sql = "SELECT created_ms FROM user_sessions WHERE user_id = ? ORDER BY created_ms DESC LIMIT 1 OFFSET ?";
        Long cutoff = null;
        try (Connection c = database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, keep - 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) cutoff = rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không dọn được phiên đăng nhập cũ", e);
        }
        if (cutoff != null) update("DELETE FROM user_sessions WHERE user_id = ? AND created_ms < ?", userId, cutoff);
    }

    @Override
    public void purgeExpired(long nowMs) {
        update("DELETE FROM user_sessions WHERE expires_ms <= ?", nowMs);
    }

    private void update(String sql, Object... params) {
        try (Connection c = database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Không ghi được phiên đăng nhập", e);
        }
    }
}
