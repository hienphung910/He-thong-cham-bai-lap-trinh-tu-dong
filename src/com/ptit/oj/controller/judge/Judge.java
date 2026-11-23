package com.ptit.oj.controller.judge;

import com.ptit.oj.model.JudgeResult;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.TestCase;
import com.ptit.oj.model.TestCaseResult;
import com.ptit.oj.model.Verdict;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Bo cham bai - lop trung tam cua he thong.
 *
 * Quy trinh: tao thu muc lam viec -> ghi ma nguon -> bien dich -> chay lan luot
 * tung test -> so sanh output -> tong hop verdict -> don dep.
 *
 * Judge KHONG biet ngon ngu nao dang duoc dung (Language) cung khong biet cach
 * so sanh nao dang ap dung (OutputComparator): no chi lam viec voi lop truu tuong.
 */
public class Judge {

    /** Do dai toi da cua phan chi tiet (stderr, gia tri in ra) trong thong bao cua mot test. */
    private static final int MAX_DETAIL_CHARS = 300;

    private final ProcessRunner runner = new ProcessRunner();

    /** Cham mot bai nop va gan ket qua vao chinh bai nop do. */
    public JudgeResult judge(Submission submission) {
        Problem problem = submission.getProblem();
        Language language = submission.getLanguage();
        JudgeResult result = new JudgeResult(submission);
        long startedAt = System.currentTimeMillis();

        Path workDir = null;
        try {
            // Loi chung (IE, CE): ghi verdict cho ca bai roi dung, khong chay test nao.
            if (!language.isAvailable()) {
                result.markGlobal(Verdict.IE, "Máy này chưa cài toolchain cho " + language.getName());
                return result;
            }
            if (problem.getTestCases().isEmpty()) {
                result.markGlobal(Verdict.IE, "Bài " + problem.getId() + " chưa có test nào");
                return result;
            }

            workDir = Files.createTempDirectory("oj-judge-");
            Path source = workDir.resolve(submission.getFileName());
            Files.write(source, submission.getSourceCode().getBytes(StandardCharsets.UTF_8));

            CompileResult compile = language.compile(source, workDir);
            if (!compile.isSuccess()) {
                result.markGlobal(Verdict.CE, compile.getMessage());
                return result;
            }

            OutputComparator comparator = ComparatorFactory.create(problem.getComparatorSpec());
            for (TestCase test : problem.getTestCases()) {
                result.addTestResult(runTestCase(problem, language, source, workDir, test, comparator));
            }
        } catch (IOException e) {
            result.markGlobal(Verdict.IE, "Lỗi vào/ra khi chấm: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // ton trong co ngat cua thread
            result.markGlobal(Verdict.IE, "Quá trình chấm bị ngắt");
        } catch (RuntimeException e) {
            // Vi du problem.properties sua tay ghi sai kieu so sanh: bao IE va van luu bai nop,
            // thay vi de loi lan ra ngoai thanh HTTP 500.
            result.markGlobal(Verdict.IE, "Bài " + problem.getId() + " cấu hình sai: " + e.getMessage());
        } finally {
            // Chay ca khi da return o tren: don dep roi gan ket qua vao bai nop.
            deleteRecursively(workDir);
            result.setJudgeTimeMs(System.currentTimeMillis() - startedAt);
            submission.setResult(result);
        }
        return result;
    }

    /** Chay dung mot test va quy ra verdict. */
    private TestCaseResult runTestCase(Problem problem, Language language, Path source,
                                       Path workDir, TestCase test, OutputComparator comparator)
            throws IOException, InterruptedException {
        List<String> command = language.buildRunCommand(source, workDir, problem.getMemoryLimitMb());
        long limit = problem.getTimeLimitMs() + language.getStartupOverheadMs();
        ExecutionResult exec = runner.run(command, workDir, test.getInput(), limit);

        if (exec.isTimedOut()) {
            return new TestCaseResult(test, Verdict.TLE, exec.getElapsedMs(), 0,
                    "Chạy quá " + problem.getTimeLimitMs() + " ms");
        }
        if (exec.getExitCode() != 0) {
            // Chi xet MLE khi chuong trinh that su chet: bai chay dung ma in chu "out of memory"
            // ra stderr khong bi cham oan.
            if (looksLikeOutOfMemory(exec.getStderr())) {
                return new TestCaseResult(test, Verdict.MLE, exec.getElapsedMs(), 0,
                        "Vượt giới hạn " + problem.getMemoryLimitMb() + " MB");
            }
            return new TestCaseResult(test, Verdict.RE, exec.getElapsedMs(), 0,
                    runtimeErrorMessage(test, exec));
        }
        if (comparator.matches(test.getExpectedOutput(), exec.getStdout())) {
            return new TestCaseResult(test, Verdict.AC, exec.getElapsedMs(), test.getPoints(), "");
        }
        return new TestCaseResult(test, Verdict.WA, exec.getElapsedMs(), 0,
                wrongAnswerMessage(test, comparator, exec));
    }

    /**
     * Thong bao RE / WA chua noi dung do chinh chuong trinh in ra. Voi test AN, chuong trinh co the
     * in lai input ra stderr / stdout de lay bo test, nen chi test vi du (input da cong khai) moi
     * duoc kem noi dung do; test an chi bao loai loi.
     */
    private String runtimeErrorMessage(TestCase test, ExecutionResult exec) {
        String base = "Exit code " + exec.getExitCode();
        if (!test.isSample()) return base;
        return base + ": " + Language.clip(firstLine(exec.getStderr()), MAX_DETAIL_CHARS);
    }

    private String wrongAnswerMessage(TestCase test, OutputComparator comparator, ExecutionResult exec) {
        if (!test.isSample()) return "Kết quả không khớp đáp án";
        return Language.clip(comparator.explain(test.getExpectedOutput(), exec.getStdout()), MAX_DETAIL_CHARS);
    }

    /**
     * Nhan biet loi tran bo nho qua thong bao cua runtime.
     * Han che: day la cach xap xi. Muon do bo nho chinh xac phai dung
     * cgroups (Linux) hoac Job Object (Windows) - vuot pham vi bai tap lon.
     */
    private boolean looksLikeOutOfMemory(String stderr) {
        if (stderr == null || stderr.isEmpty()) return false;
        String s = stderr.toLowerCase();
        return s.contains("outofmemoryerror")            // Java
                || s.contains("bad_alloc")               // C++
                || s.contains("memoryerror")             // Python
                || s.contains("out of memory")           // JavaScript (Node) va cac runtime khac
                || s.contains("cannot allocate memory");
    }

    private String firstLine(String text) {
        if (text != null) {
            for (String line : text.split("\r?\n")) {
                if (!line.trim().isEmpty()) return line.trim();
            }
        }
        return "(không có thông báo)";
    }

    /** Xoa thu muc lam viec tam (bao gom file .class / .exe da sinh ra). */
    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // file .exe co the con bi khoa tren Windows -> bo qua
                }
            });
        } catch (IOException ignored) {
            // khong xoa duoc thi de OS don dep thu muc temp
        }
    }
}
