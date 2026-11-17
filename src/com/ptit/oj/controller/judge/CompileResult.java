package com.ptit.oj.controller.judge;

/** Ket qua bien dich: thanh cong hay khong + thong bao loi cua compiler. */
public class CompileResult {

    private boolean success;
    private String message;

    private CompileResult(boolean success, String message) {
        this.success = success;
        this.message = message == null ? "" : message;
    }

    public static CompileResult ok() { return new CompileResult(true, ""); }
    public static CompileResult fail(String message) { return new CompileResult(false, message); }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
