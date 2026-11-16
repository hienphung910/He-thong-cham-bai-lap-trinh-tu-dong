package com.ptit.oj.judge.exception;

/** Nem ra khi ma nguon cua thi sinh khong bien dich duoc -> verdict CE. */
public class CompileErrorException extends JudgeException {
    private static final long serialVersionUID = 1L;

    public CompileErrorException(String message) { super(message); }
}
