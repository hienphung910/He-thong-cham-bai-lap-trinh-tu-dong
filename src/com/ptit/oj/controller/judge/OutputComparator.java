package com.ptit.oj.controller.judge;

/**
 * STRATEGY PATTERN: mot cach so sanh output cua thi sinh voi dap an.
 * Judge chi lam viec voi interface nay, khong biet thuat toan cu the ben trong.
 */
public interface OutputComparator {

    /** true neu output cua thi sinh duoc coi la dung. */
    boolean matches(String expected, String actual);

    /**
     * Giai thich vi sao sai, de gui ve thi sinh va luu vao CSDL.
     *
     * TUYET DOI KHONG chua dap an: test an ma lo dap an thi thi sinh chi can nop bua
     * vai lan, doc "ky vong" cua tung test roi chep lai la qua het. Chi noi lech o dau
     * va thi sinh da in ra gi (cai do ho biet san roi).
     */
    default String explain(String expected, String actual) {
        return "Nhận được: [" + preview(actual) + "]";
    }

    /** Rut gon output thanh mot dong ngan de dua vao thong bao. */
    static String preview(String s) {
        String flat = s == null ? "" : s.replace("\r", "").replace("\n", " / ").trim();
        return flat.length() <= 60 ? flat : flat.substring(0, 57) + "...";
    }
}
