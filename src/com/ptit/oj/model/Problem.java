package com.ptit.oj.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mot bai tap tren he thong. */
public class Problem extends Entity {

    private final String title;
    private final String statement;
    private final long timeLimitMs;
    private final int memoryLimitMb;
    private final String comparatorSpec;   // "exact" | "token" | "lines" | "float:1e-6"
    /** Ma chu de trong lo trinh hoc (data/topics.txt), "" neu bai chua phan loai. */
    private final String topic;
    /** 1 = De, 2 = Vua, 3 = Kho; 0 = chua danh gia. */
    private final int difficulty;
    /**
     * Thu tu hoc trong chu de (khoa "order" trong problem.properties, tuy chon).
     * 0 = khong khai bao: bai dung sau cac bai co thu tu va xep theo ma bai.
     */
    private final int order;
    private final List<TestCase> testCases = new ArrayList<>();

    /** Bai chua phan loai: khong thuoc chu de nao, chua danh gia do kho. */
    public Problem(String id, String title, String statement,
                   long timeLimitMs, int memoryLimitMb, String comparatorSpec) {
        this(id, title, statement, timeLimitMs, memoryLimitMb, comparatorSpec, "", 0, 0);
    }

    public Problem(String id, String title, String statement,
                   long timeLimitMs, int memoryLimitMb, String comparatorSpec,
                   String topic, int difficulty, int order) {
        super(id);
        this.title = title;
        this.statement = statement == null ? "" : statement;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.comparatorSpec = comparatorSpec == null ? "token" : comparatorSpec;
        this.topic = topic == null ? "" : topic;
        this.difficulty = difficulty;
        this.order = order;
    }

    public void addTestCase(TestCase tc) {
        testCases.add(tc);
    }

    /** Tra ve ban sao khong sua duoc -> bao ve trang thai noi bo (encapsulation). */
    public List<TestCase> getTestCases() {
        return Collections.unmodifiableList(testCases);
    }

    public String getTitle() { return title; }
    public String getStatement() { return statement; }
    public long getTimeLimitMs() { return timeLimitMs; }
    public int getMemoryLimitMb() { return memoryLimitMb; }
    public String getComparatorSpec() { return comparatorSpec; }
    public String getTopic() { return topic; }
    public int getDifficulty() { return difficulty; }
    public int getOrder() { return order; }

    /** Tong diem toi da cua bai = tong diem cac test. */
    public double getMaxPoints() {
        double sum = 0;
        for (TestCase tc : testCases) sum += tc.getPoints();
        return sum;
    }
}
