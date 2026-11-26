package com.ptit.oj.controller.judge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * So sanh theo DONG, trong moi dong so theo TOKEN - danh cho bai ma de quy dinh cach xuong dong
 * ("in tren cung mot dong", "moi so tren mot dong", "dong 1: ..., dong 2: ...").
 *
 * TokenComparator bo qua ca xuong dong nen in moi so mot dong van dung o bai bat in tren mot dong.
 * LineComparator thi bat ca so dau cach giua cac tu, de lam sai hoc sinh in " " truoc moi so.
 * Lop nay o giua:
 *   - so dong co noi dung phai khop, va thu tu cac dong phai khop;
 *   - trong mot dong, cac gia tri phai khop nhung bao nhieu dau cach / tab giua chung cung duoc;
 *   - dong trong (chi co khoang trang) bi bo qua.
 */
public class LineTokenComparator implements OutputComparator {

    @Override
    public boolean matches(String expected, String actual) {
        return lines(expected).equals(lines(actual));
    }

    /** Chi noi lech o dong nao va thi sinh da in gi o dong do - khong noi dap an. */
    @Override
    public String explain(String expected, String actual) {
        List<List<String>> e = lines(expected);
        List<List<String>> a = lines(actual);
        if (e.size() != a.size()) {
            return "Số dòng không khớp: nhận được " + a.size() + " dòng";
        }
        for (int i = 0; i < e.size(); i++) {
            if (!e.get(i).equals(a.get(i))) {
                return "Lệch tại dòng thứ " + (i + 1) + ": nhận được [" + String.join(" ", a.get(i)) + "]";
            }
        }
        return "Khớp";
    }

    /** Moi dong co noi dung thanh danh sach token; dong trong bi bo. */
    private List<List<String>> lines(String s) {
        List<List<String>> result = new ArrayList<>();
        if (s == null) return result;
        for (String line : s.split("\\r\\n|\\r|\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) result.add(Arrays.asList(trimmed.split("\\s+")));
        }
        return result;
    }
}
