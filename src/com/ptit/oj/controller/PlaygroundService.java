package com.ptit.oj.controller;

import com.ptit.oj.controller.judge.CompileResult;
import com.ptit.oj.controller.judge.ExecutionResult;
import com.ptit.oj.controller.judge.Language;
import com.ptit.oj.controller.judge.ProcessRunner;
import com.ptit.oj.controller.judge.TokenComparator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Chay thu ma nguon voi input tu nhap (Module 10 - Online IDE) va stress test (Module 12).
 *
 * Dung lai Language va ProcessRunner cua bo cham. Moi lan chay deu khoa tren
 * JudgeService de KHONG chay song song voi viec cham bai (tranh do sai thoi gian).
 */
public class PlaygroundService {

    private static final long RUN_LIMIT_MS = 5_000;
    private static final long STRESS_LIMIT_MS = 2_000;
    private static final int MEMORY_MB = 256;
    private static final int MAX_ROUNDS = 30;
    /** Tong thoi gian toi da cua mot lan stress test - trong luc do cac bai nop phai cho. */
    private static final long STRESS_BUDGET_MS = 25_000;
    private static final int MAX_CODE_CHARS = 100_000;

    private final JudgeService judge;
    private final ProcessRunner runner = new ProcessRunner();

    public PlaygroundService(JudgeService judge) {
        this.judge = judge;
    }

    /** Ket qua mot lan chay thu. */
    public static class RunResult {
        private final String compileError;     // khac "" nghia la khong bien dich duoc
        private final ExecutionResult execution;

        RunResult(String compileError, ExecutionResult execution) {
            this.compileError = compileError;
            this.execution = execution;
        }

        public String getCompileError() { return compileError; }
        public ExecutionResult getExecution() { return execution; }
    }

    /** Ket qua stress test: tim thay phan vi du hay khong, va noi dung cua no. */
    public static class StressResult {
        private final boolean found;
        private final int rounds;              // so vong da chay
        private final String message;
        private final String input;
        private final String expected;         // output cua loi giai vet can
        private final String actual;           // output cua loi giai can kiem tra

        StressResult(boolean found, int rounds, String message, String input, String expected, String actual) {
            this.found = found;
            this.rounds = rounds;
            this.message = message;
            this.input = input;
            this.expected = expected;
            this.actual = actual;
        }

        public boolean isFound() { return found; }
        public int getRounds() { return rounds; }
        public String getMessage() { return message; }
        public String getInput() { return input; }
        public String getExpected() { return expected; }
        public String getActual() { return actual; }
    }

    // ---------------------------------------------------------------- chay thu

    public RunResult run(Language language, String code, String input) {
        requireUsable(language, code);
        synchronized (judge) {
            Path workDir = null;
            try {
                workDir = Files.createTempDirectory("oj-run-");
                Path source = writeSource(language, code, workDir);
                CompileResult compile = language.compile(source, workDir);
                if (!compile.isSuccess()) return new RunResult(hidePath(compile.getMessage(), workDir), null);
                return new RunResult("", execute(language, source, workDir, input, RUN_LIMIT_MS));
            } catch (IOException e) {
                throw new ApiException(500, "Lỗi vào/ra khi chạy thử: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(500, "Quá trình chạy bị ngắt");
            } finally {
                deleteRecursively(workDir);
            }
        }
    }

    // ------------------------------------------------------------- stress test

    /**
     * Stress test: moi vong chay "generator" voi stdin la so thu tu vong de sinh mot input,
     * dua input do cho loi giai vet can (brute) va loi giai can kiem tra (solution),
     * roi so hai output. Dung ngay khi tim thay input lam hai loi giai khac nhau.
     */
    public StressResult stress(Language language, String solution, String brute, String generator, int rounds) {
        requireUsable(language, solution);
        requireUsable(language, brute);
        requireUsable(language, generator);
        int totalRounds = Math.max(1, Math.min(rounds, MAX_ROUNDS));
        TokenComparator comparator = new TokenComparator();

        synchronized (judge) {
            Path workDir = null;
            try {
                workDir = Files.createTempDirectory("oj-stress-");
                Path solutionSrc = compileInto(language, solution, workDir.resolve("solution"), "lời giải cần kiểm tra");
                Path bruteSrc = compileInto(language, brute, workDir.resolve("brute"), "lời giải vét cạn");
                Path generatorSrc = compileInto(language, generator, workDir.resolve("generator"), "chương trình sinh test");

                long deadline = System.currentTimeMillis() + STRESS_BUDGET_MS;
                for (int round = 1; round <= totalRounds; round++) {
                    if (System.currentTimeMillis() > deadline) {
                        return new StressResult(false, round - 1, "Dừng sau " + (round - 1)
                                + " vòng vì đã hết " + STRESS_BUDGET_MS / 1000 + " giây; chưa tìm thấy phản ví dụ", "", "", "");
                    }
                    ExecutionResult gen = execute(language, generatorSrc, generatorSrc.getParent(),
                            round + "\n", STRESS_LIMIT_MS);
                    if (!gen.isSuccess()) {
                        throw ApiException.badRequest("Chương trình sinh test bị lỗi ở vòng " + round + ": " + gen.getStderr());
                    }
                    String input = gen.getStdout();

                    ExecutionResult expected = execute(language, bruteSrc, bruteSrc.getParent(), input, STRESS_LIMIT_MS);
                    if (!expected.isSuccess()) {
                        throw ApiException.badRequest("Lời giải vét cạn bị lỗi hoặc quá 2 giây ở vòng " + round);
                    }
                    ExecutionResult actual = execute(language, solutionSrc, solutionSrc.getParent(), input, STRESS_LIMIT_MS);
                    if (actual.isTimedOut()) {
                        return new StressResult(true, round, "Lời giải chạy quá 2 giây", input, expected.getStdout(), "");
                    }
                    if (actual.getExitCode() != 0) {
                        return new StressResult(true, round, "Lời giải bị lỗi khi chạy (exit code "
                                + actual.getExitCode() + ")", input, expected.getStdout(), actual.getStderr());
                    }
                    if (!comparator.matches(expected.getStdout(), actual.getStdout())) {
                        return new StressResult(true, round, "Hai lời giải cho kết quả khác nhau",
                                input, expected.getStdout(), actual.getStdout());
                    }
                }
                return new StressResult(false, totalRounds,
                        "Không tìm thấy phản ví dụ sau " + totalRounds + " vòng", "", "", "");
            } catch (IOException e) {
                throw new ApiException(500, "Lỗi vào/ra khi stress test: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(500, "Quá trình stress test bị ngắt");
            } finally {
                deleteRecursively(workDir);
            }
        }
    }

    // ----------------------------------------------------------------- tien ich

    private void requireUsable(Language language, String code) {
        if (!language.isAvailable()) {
            throw ApiException.badRequest("Máy này chưa cài toolchain cho " + language.getName());
        }
        if (code == null || code.trim().isEmpty()) throw ApiException.badRequest("Mã nguồn rỗng");
        if (code.length() > MAX_CODE_CHARS) throw ApiException.badRequest("Mã nguồn quá dài");
    }

    private Path writeSource(Language language, String code, Path dir) throws IOException {
        Path source = dir.resolve(language.defaultFileName(code));
        Files.write(source, code.getBytes(StandardCharsets.UTF_8));
        return source;
    }

    /** Ghi ma nguon vao thu muc rieng roi bien dich; loi bien dich thi bao ngay cho nguoi dung. */
    private Path compileInto(Language language, String code, Path dir, String label)
            throws IOException, InterruptedException {
        Files.createDirectories(dir);
        Path source = writeSource(language, code, dir);
        CompileResult compile = language.compile(source, dir);
        if (!compile.isSuccess()) {
            throw ApiException.badRequest("Lỗi biên dịch " + label + ": " + hidePath(compile.getMessage(), dir));
        }
        return source;
    }

    /** Bo duong dan thu muc tam khoi thong bao cua trinh bien dich, chi de lai ten file. */
    private String hidePath(String message, Path dir) {
        return message.replace(dir.toString() + java.io.File.separator, "");
    }

    private ExecutionResult execute(Language language, Path source, Path dir, String input, long limitMs)
            throws IOException, InterruptedException {
        return runner.run(language.buildRunCommand(source, dir, MEMORY_MB), dir,
                input == null ? "" : input, limitMs + language.getStartupOverheadMs());
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // file .exe co the con bi khoa tren Windows -> de he dieu hanh don thu muc temp
                }
            });
        } catch (IOException ignored) {
            // khong xoa duoc thi bo qua
        }
    }
}
