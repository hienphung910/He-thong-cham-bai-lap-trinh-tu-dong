package com.ptit.oj.controller.judge;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Lop truu tuong cho mot ngon ngu lap trinh duoc ho tro.
 * Day la khung chung (template): lop con chi khai bao lenh bien dich / lenh chay.
 */
public abstract class Language {

    private static final long COMPILE_TIME_LIMIT_MS = 20_000;
    private static final int MAX_COMPILE_MESSAGE_CHARS = 12_000;

    protected final ProcessRunner runner = new ProcessRunner();
    private Boolean availableCache;

    public abstract String getName();

    /** Duoi file tuong ung, vi du ".java". */
    public abstract String getFileExtension();

    /** Lenh kiem tra toolchain co san khong, vi du javac -version. */
    protected abstract List<String> getVersionCommand();

    /** Bien dich ma nguon trong workDir. Ngon ngu thong dich chi kiem tra cu phap. */
    public abstract CompileResult compile(Path source, Path workDir) throws IOException, InterruptedException;

    /** Lenh chay chuong trinh cua thi sinh. */
    public abstract List<String> buildRunCommand(Path source, Path workDir, int memoryLimitMb);

    /** Thoi gian cong them cho ngon ngu khoi dong cham (JVM, Python interpreter...). */
    public long getStartupOverheadMs() { return 0; }

    /**
     * Ten file dung de luu ma nguon thi sinh dan tren web.
     * Mac dinh la "solution" + duoi file; Java ghi de vi ten file phai trung ten class.
     */
    public String defaultFileName(String sourceCode) {
        return "solution" + getFileExtension();
    }

    /** Kiem tra compiler/interpreter co ton tai tren may khong (ket qua duoc nho lai). */
    public boolean isAvailable() {
        if (availableCache == null) {
            ExecutionResult r = probe(getVersionCommand());
            // Phai thoat voi ma 0: "python3" gia cua Windows Store van chay duoc nhung bao loi.
            availableCache = r != null && r.isSuccess();
        }
        return availableCache;
    }

    /** Chay thu mot lenh cua toolchain; tra ve null neu may khong co lenh do. */
    protected ExecutionResult probe(List<String> command) {
        try {
            // Lenh cua chinh he thong ("javac -version"...), khong chua ma cua thi sinh: khong qua sandbox.
            return runner.runTrusted(command, Paths.get(System.getProperty("user.dir")), "", 10_000);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    /**
     * Chay lenh bien dich (hoac lenh kiem tra cu phap) va quy ket qua ve CompileResult:
     * ma thoat khac 0 thi coi la loi, thong bao lay tu stderr cua trinh bien dich.
     */
    protected CompileResult runCompiler(Path workDir, String... command) throws IOException, InterruptedException {
        // Trinh bien dich doc ma cua thi sinh (#include, macro...) nen cung chay trong sandbox.
        ExecutionResult r = runner.run(Arrays.asList(command), workDir, "", COMPILE_TIME_LIMIT_MS, Sandbox.COMPILE_MEMORY_MB);
        if (r.isTimedOut()) return CompileResult.fail("Biên dịch quá lâu (> 20s)");
        if (r.getExitCode() != 0) {
            String message = r.getStderr().isEmpty() ? r.getStdout() : r.getStderr();
            return CompileResult.fail(clip(message.trim(), MAX_COMPILE_MESSAGE_CHARS));
        }
        return CompileResult.ok();
    }

    /**
     * Cat thong bao qua dai. Trinh bien dich C++ co the in hang tram KB cho mot loi template,
     * trong khi cot TEXT cua MySQL chi chua duoc 65 535 byte (moi ky tu UTF-8 toi da 4 byte).
     */
    public static String clip(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n... (đã cắt bớt, thông báo gốc dài " + text.length() + " ký tự)";
    }

    /** File thuc thi sinh ra khi bien dich (C++): cung ten voi file ma nguon. */
    protected static Path executablePath(Path source, Path workDir) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return workDir.resolve(baseName(source) + (windows ? ".exe" : ".bin"));
    }

    /** Ten file khong co duoi, dung lam ten class / ten file thuc thi. */
    protected static String baseName(Path source) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    @Override
    public String toString() { return getName(); }
}
