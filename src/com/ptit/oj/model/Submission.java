package com.ptit.oj.model;

import com.ptit.oj.controller.judge.Language;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/** Mot lan nop bai cua nguoi dung. */
public class Submission extends Entity {

    private final Problem problem;
    private final User author;
    private final Language language;
    private final String fileName;        // ten file ma nguon, vi du Main.java
    private final LocalDateTime submittedAt;
    private String sourceCode;
    private JudgeResult result;           // duoc gan sau khi cham

    /** Tao bai nop moi: sinh ma va lay thoi diem hien tai. */
    public Submission(Problem problem, User author, Language language, String fileName, String sourceCode) {
        this(newId(), problem, author, language, fileName, LocalDateTime.now());
        this.sourceCode = sourceCode;
    }

    /** Dung lai mot bai nop da luu trong CSDL: ma va thoi diem nop lay tu CSDL. */
    public Submission(String id, Problem problem, User author, Language language,
                      String fileName, LocalDateTime submittedAt) {
        super(id);
        this.problem = problem;
        this.author = author;
        this.language = language;
        this.fileName = fileName;
        this.submittedAt = submittedAt;
    }

    /** Ma bai nop dang SUB-<UUID>: khong trung nhau ke ca sau khi khoi dong lai may chu. */
    public static String newId() {
        return "SUB-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    public Problem getProblem() { return problem; }
    public User getAuthor() { return author; }
    public Language getLanguage() { return language; }
    public String getFileName() { return fileName; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public JudgeResult getResult() { return result; }
    public void setResult(JudgeResult result) { this.result = result; }

    public boolean isJudged() { return result != null; }

    /**
     * Ban sao KHONG co ma nguon, de giu trong bo nho dem (CachedSubmissionRepository): bang xep hang, thong
     * ke chi can ket qua; ma nguon chiem phan lon dung luong va chi can khi mo xem mot bai.
     */
    public Submission lightCopy() {
        Submission copy = new Submission(getId(), problem, author, language, fileName, submittedAt);
        if (result != null) copy.result = result.copyFor(copy);
        return copy;
    }
}
