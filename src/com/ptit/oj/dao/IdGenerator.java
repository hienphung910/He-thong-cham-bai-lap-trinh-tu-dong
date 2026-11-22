package com.ptit.oj.dao;

import java.util.HashMap;
import java.util.Map;

/**
 * Sinh ma cho cac thuc the moi theo dang TIEN_TO-001, TIEN_TO-002...
 * Moi tien to (CLS, CRS, MTG...) co mot bo dem rieng. Du lieu cac module moi
 * nam trong RAM nen bo dem bat dau lai tu 1 moi lan khoi dong.
 */
public final class IdGenerator {

    private static final Map<String, Integer> COUNTERS = new HashMap<>();

    private IdGenerator() { }

    public static synchronized String next(String prefix) {
        int number = COUNTERS.merge(prefix, 1, Integer::sum);
        return String.format("%s-%03d", prefix, number);
    }
}
