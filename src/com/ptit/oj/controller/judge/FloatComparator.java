package com.ptit.oj.controller.judge;

/**
 * So sanh so thuc voi sai so cho phep (epsilon).
 *
 * Ke thua TokenComparator va CHI ghi de phep so sanh mot token (tokenEquals) cung
 * cach goi ten cho lech - vong lap so sanh, dem token, sinh thong bao deu dung lai
 * cua lop cha.
 */
public class FloatComparator extends TokenComparator {

    private final double epsilon;

    public FloatComparator(double epsilon) {
        this.epsilon = epsilon;
    }

    @Override
    protected boolean tokenEquals(String e, String a) {
        if (e.equals(a)) return true;
        // Ca hai deu la so nguyen thi phai bang nhau tuyet doi: sai so chi danh cho so thuc
        // (1000001 khong duoc coi la 1000000).
        if (isInteger(e) && isInteger(a)) return new java.math.BigInteger(e).equals(new java.math.BigInteger(a));
        // Chi nhan so thap phan viet thuong: khong nhan "3d", "0x1.8p1", "NaN"...
        if (!isDecimal(e) || !isDecimal(a)) return false;
        try {
            double x = Double.parseDouble(e);
            double y = Double.parseDouble(a);
            double diff = Math.abs(x - y);
            // chap nhan sai so tuyet doi hoac sai so tuong doi
            return diff <= epsilon || diff <= epsilon * Math.max(Math.abs(x), Math.abs(y));
        } catch (NumberFormatException ex) {
            return false;   // khong phai so -> phai giong het
        }
    }

    private static boolean isInteger(String s) {
        int start = s.startsWith("-") || s.startsWith("+") ? 1 : 0;
        if (start == s.length()) return false;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    /** [dau] chu so [. chu so] [e [dau] chu so]; it nhat mot chu so o phan nguyen hoac phan thap phan. */
    private static boolean isDecimal(String s) {
        int i = 0, n = s.length();
        if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        int digits = 0;
        while (i < n && Character.isDigit(s.charAt(i))) { i++; digits++; }
        if (i < n && s.charAt(i) == '.') {
            i++;
            while (i < n && Character.isDigit(s.charAt(i))) { i++; digits++; }
        }
        if (digits == 0) return false;
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            int exp = 0;
            while (i < n && Character.isDigit(s.charAt(i))) { i++; exp++; }
            if (exp == 0) return false;
        }
        return i == n;
    }

    @Override
    protected String mismatchLabel() {
        return "Lệch quá sai số";
    }
}
