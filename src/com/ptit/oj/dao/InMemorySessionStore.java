package com.ptit.oj.dao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Phien dang nhap trong RAM - che do --no-db: tat may chu la moi nguoi phai dang nhap lai. */
public class InMemorySessionStore implements SessionStore {

    private static final class Row {
        final String userId;
        final long createdAtMs;
        long expiresAtMs;
        final boolean remember;

        Row(String userId, long createdAtMs, long expiresAtMs, boolean remember) {
            this.userId = userId;
            this.createdAtMs = createdAtMs;
            this.expiresAtMs = expiresAtMs;
            this.remember = remember;
        }
    }

    private final Map<String, Row> rows = new LinkedHashMap<>();

    @Override
    public synchronized void save(String tokenHash, String userId, long createdAtMs, long expiresAtMs, boolean remember) {
        rows.put(tokenHash, new Row(userId, createdAtMs, expiresAtMs, remember));
    }

    @Override
    public synchronized Optional<StoredSession> find(String tokenHash) {
        Row row = rows.get(tokenHash);
        return row == null ? Optional.empty() : Optional.of(new StoredSession(row.userId, row.expiresAtMs, row.remember));
    }

    @Override
    public synchronized void extend(String tokenHash, long expiresAtMs) {
        Row row = rows.get(tokenHash);
        if (row != null) row.expiresAtMs = expiresAtMs;
    }

    @Override
    public synchronized void delete(String tokenHash) {
        rows.remove(tokenHash);
    }

    @Override
    public synchronized void deleteUser(String userId, String keepHash) {
        rows.entrySet().removeIf(e -> e.getValue().userId.equals(userId) && !e.getKey().equals(keepHash));
    }

    @Override
    public synchronized void trimUser(String userId, int keep) {
        List<Map.Entry<String, Row>> mine = new ArrayList<>();
        for (Map.Entry<String, Row> e : rows.entrySet()) {
            if (e.getValue().userId.equals(userId)) mine.add(e);
        }
        if (mine.size() <= keep) return;
        mine.sort(Comparator.comparingLong(e -> e.getValue().createdAtMs));
        for (int i = 0; i < mine.size() - keep; i++) rows.remove(mine.get(i).getKey());
    }

    @Override
    public synchronized void purgeExpired(long nowMs) {
        rows.values().removeIf(row -> row.expiresAtMs <= nowMs);
    }
}
