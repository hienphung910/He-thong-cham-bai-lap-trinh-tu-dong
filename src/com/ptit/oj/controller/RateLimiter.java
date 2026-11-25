package com.ptit.oj.controller;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Dem so lan mot viec xay ra trong mot khoang thoi gian gan day ("cua so truot"), theo khoa bat ky:
 * "dang nhap sai cua ten X tu IP Y", "dang ky tu IP Z", "tin nhan chat cua nguoi W"...
 *
 * Cach dung: goi retryAfterSeconds(khoa, cuaSo, gioiHan) truoc khi lam viec - khac 0 thi tu choi;
 * goi hit(khoa, cuaSo) khi viec do xay ra. Moi thu nam trong RAM va tu het han, khong can don dep.
 */
public final class RateLimiter {

    /** Tran so khoa giu trong bo nho, de mot nguoi la khong lam day RAM bang cach doi khoa lien tuc. */
    private static final int MAX_KEYS = 50_000;
    /** Moi khoa chi can nho toi da chung nay lan gan nhat (lon hon moi gioi han dang dung). */
    private static final int MAX_HITS_PER_KEY = 256;

    private static final class Entry {
        private final Deque<Long> times = new ArrayDeque<>();
        private long windowMs;
    }

    private final Map<String, Entry> entries = new HashMap<>();

    /** Ghi nhan mot lan. @return so lan trong cua so, tinh ca lan nay */
    public synchronized int hit(String key, long windowMs) {
        long now = System.currentTimeMillis();
        if (entries.size() >= MAX_KEYS && !entries.containsKey(key)) purge(now);
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        entry.windowMs = windowMs;
        prune(entry, now);
        if (entry.times.size() >= MAX_HITS_PER_KEY) entry.times.pollFirst();
        entry.times.addLast(now);
        return entry.times.size();
    }

    /**
     * 0 neu khoa nay con duoi gioi han; nguoc lai la so giay phai doi cho toi khi lan cu nhat trong
     * cua so het han (luc do so lan lai tut xuong duoi gioi han).
     */
    public synchronized long retryAfterSeconds(String key, long windowMs, int limit) {
        Entry entry = entries.get(key);
        if (entry == null) return 0;
        long now = System.currentTimeMillis();
        entry.windowMs = windowMs;
        prune(entry, now);
        if (entry.times.isEmpty()) {
            entries.remove(key);
            return 0;
        }
        if (entry.times.size() < limit) return 0;
        // Lan thu (size - limit + 1) tinh tu cu nhat het han thi con lai limit - 1 lan.
        Iterator<Long> it = entry.times.iterator();
        long releaseAt = 0;
        for (int i = 0; i <= entry.times.size() - limit; i++) releaseAt = it.next() + windowMs;
        return Math.max(1, (releaseAt - now + 999) / 1000);
    }

    public synchronized void reset(String key) {
        entries.remove(key);
    }

    private static void prune(Entry entry, long now) {
        while (!entry.times.isEmpty() && entry.times.peekFirst() <= now - entry.windowMs) entry.times.pollFirst();
    }

    /** Bo cac khoa da het han; van con qua nhieu thi xoa het (tha cho qua con hon tu choi nham moi nguoi). */
    private void purge(long now) {
        Iterator<Entry> it = entries.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            prune(entry, now);
            if (entry.times.isEmpty()) it.remove();
        }
        if (entries.size() >= MAX_KEYS) entries.clear();
    }
}
