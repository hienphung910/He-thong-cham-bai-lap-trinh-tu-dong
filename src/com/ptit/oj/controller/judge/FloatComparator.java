package com.ptit.oj.controller.judge;

/**
 * So sanh so thuc voi sai so cho phep (epsilon).
 *
 * Ke thua TokenComparator va CHI ghi de phep so sanh mot token (tokenEquals) cung
 * cach goi ten cho lech - vong lap so sanh, dem token, sinh thong bao deu dung lai
 * cua lop cha.
 */
public class FloatComparator extends TokenComparator {

    private double epsilon;

    public FloatComparator(double epsilon) {
        this.epsilon = epsilon;
    }

    @Override
    protected boolean tokenEquals(String e, String a) {
        if (e.equals(a)) return true;
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

    @Override
    protected String mismatchLabel() {
        return "Lệch quá sai số";
    }
}
