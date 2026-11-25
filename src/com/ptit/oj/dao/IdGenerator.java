package com.ptit.oj.dao;

import java.util.HashMap;
import java.util.Map;

/**
 * Sinh ma cho cac thuc the moi theo dang TIEN_TO-001, TIEN_TO-002...
 * Moi tien to (CLS, CRS, MTG...) co mot bo dem rieng. Bo dem duoc luu va nap lai cung du lieu
 * cua cac module mo rong (StateStore), de ma moi khong trung voi ma da cap truoc lan khoi dong lai.
 */
public final class IdGenerator {

    private static final Map<String, Integer> COUNTERS = new HashMap<>();

    private IdGenerator() { }

    /** Ban sao cac bo dem hien tai, de luu xuong o dia. */
    public static synchronized Map<String, Integer> snapshot() {
        return new HashMap<>(COUNTERS);
    }

    /** Nap lai bo dem da luu; khong bao gio lam bo dem lui lai. */
    public static synchronized void restore(Map<String, Integer> saved) {
        for (Map.Entry<String, Integer> e : saved.entrySet()) {
            COUNTERS.merge(e.getKey(), e.getValue(), Math::max);
        }
    }

    public static synchronized String next(String prefix) {
        int number = COUNTERS.merge(prefix, 1, Integer::sum);
        return String.format("%s-%03d", prefix, number);
    }
}
