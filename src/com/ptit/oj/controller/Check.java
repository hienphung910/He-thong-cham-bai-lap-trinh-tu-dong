package com.ptit.oj.controller;

/** Kiem tra du lieu van ban nguoi dung gui len, dung chung cho cac service moi. */
public final class Check {

    private Check() { }

    /** Bat buoc co noi dung va khong dai qua max ky tu. Tra ve chuoi da cat khoang trang. */
    public static String text(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) throw ApiException.badRequest(label + " không được để trống");
        if (v.length() > max) throw ApiException.badRequest(label + " tối đa " + max + " ký tự");
        return v;
    }

    /** Duoc phep de trong, nhung van gioi han do dai. */
    public static String optional(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.length() > max) throw ApiException.badRequest(label + " tối đa " + max + " ký tự");
        return v;
    }
}
