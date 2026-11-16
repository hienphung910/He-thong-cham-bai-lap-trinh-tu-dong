package com.ptit.oj.model;

/**
 * Ket qua cham cua dung mot test.
 *
 * message la thong bao gui ve thi sinh va luu vao CSDL, nen khong bao gio chua
 * dap an cua test (xem OutputComparator.explain()).
 */
public class TestCaseResult {

    private final TestCase testCase;
    private final Verdict verdict;
    private final long runtimeMs;
    private final double earnedPoints;
    private final String message;

    public TestCaseResult(TestCase testCase, Verdict verdict, long runtimeMs,
                          double earnedPoints, String message) {
        this.testCase = testCase;
        this.verdict = verdict;
        this.runtimeMs = runtimeMs;
        this.earnedPoints = earnedPoints;
        this.message = message == null ? "" : message;
    }

    public TestCase getTestCase() { return testCase; }
    public Verdict getVerdict() { return verdict; }
    public long getRuntimeMs() { return runtimeMs; }
    public double getEarnedPoints() { return earnedPoints; }
    public String getMessage() { return message; }
}
