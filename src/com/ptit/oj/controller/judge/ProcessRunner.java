package com.ptit.oj.controller.judge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Chay mot tien trinh con: bom stdin, thu stdout/stderr bang thread rieng
 * (tranh deadlock khi buffer cua OS bi day) va cuong che dung khi qua thoi gian.
 *
 * run() la duong chay ma KHONG TIN (trinh bien dich doc ma cua thi sinh, chuong trinh cua thi sinh):
 * khi Sandbox bat, lenh duoc chay bang tai khoan he thong rieng kem gioi han tai nguyen.
 * runTrusted() chay lenh cua chinh he thong (vi du "javac -version"), khong qua sandbox.
 */
public class ProcessRunner {

    private static final int OUTPUT_CAP_BYTES = 1 << 20; // 1 MB, chong sinh output vo han

    /** limits: gioi han tai nguyen cua buoc nay (xem Language.compileLimits / runLimits); chi co tac dung khi Sandbox bat. */
    public ExecutionResult run(List<String> command, Path workDir, String stdin, long timeLimitMs, Sandbox.Limits limits)
            throws IOException, InterruptedException {
        boolean sandboxed = Sandbox.isActive();
        return start(sandboxed ? Sandbox.wrap(command, limits) : command, workDir, stdin, timeLimitMs, sandboxed);
    }

    /** Chay lenh cua chinh he thong (khong chua ma cua thi sinh), khong qua sandbox. */
    public ExecutionResult runTrusted(List<String> command, Path workDir, String stdin, long timeLimitMs)
            throws IOException, InterruptedException {
        return start(command, workDir, stdin, timeLimitMs, false);
    }

    private ExecutionResult start(List<String> command, Path workDir, String stdin, long timeLimitMs, boolean sandboxed)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        // Khong truyen cau hinh cua may chu (mat khau CSDL, mat khau admin...) cho tien trinh con.
        // Day chi la mot lop chan them, KHONG thay duoc sandbox (xem Sandbox).
        pb.environment().keySet().removeIf(name -> name.toUpperCase().startsWith("OJ_"));
        Path stdinDir = sandboxed ? stdinFromFile(pb, stdin) : null;
        try {
            Process process = pb.start();
            try {
                return collect(process, sandboxed ? null : stdin, timeLimitMs, sandboxed);
            } finally {
                // Chay ca khi thread cham bi ngat (may chu dang dung): khong de lai tien trinh mo coi.
                killTree(process, sandboxed);
            }
        } finally {
            deleteStdin(stdinDir);
        }
    }

    /**
     * Khi chay bang tai khoan sandbox, input duoc dua vao tu MOT FILE thay vi mot ong (pipe) cua may chu:
     * ong thuoc tai khoan may chu nen chuong trinh doc input qua duong dan "/dev/stdin" (rat pho bien voi
     * Node: readFileSync("/dev/stdin"), va Python: open("/dev/stdin")) bi tu choi quyen.
     *
     * File nam trong mot thu muc RIENG cua tai khoan may chu, quyen 711: tai khoan sandbox doc duoc file
     * nhung khong liet ke, tao, xoa hay thay file trong do - khong the tra file bang mot lien ket tro toi
     * test an de may chu ghi de len. Tra ve thu muc do de xoa sau khi chay.
     */
    private static Path stdinFromFile(ProcessBuilder pb, String stdin) throws IOException {
        Path dir = Sandbox.newWorkDir("oj-in-");
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx--x--x"));
            Path file = dir.resolve("stdin");
            Files.write(file, (stdin == null ? "" : stdin).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
            pb.redirectInput(file.toFile());
            return dir;
        } catch (IOException | RuntimeException e) {
            deleteStdin(dir);
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
    }

    private static void deleteStdin(Path dir) {
        if (dir == null) return;
        try {
            Files.deleteIfExists(dir.resolve("stdin"));
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // thu muc tam trong /tmp: khong xoa duoc thi he dieu hanh don sau
        }
    }

    /** Dung tien trinh va moi tien trinh con chau con song cua no. */
    private static void killTree(Process process, boolean sandboxed) {
        // Tien trinh trong sandbox thuoc tai khoan khac, may chu khong tu kill duoc: nho chinh tai khoan do diet.
        if (sandboxed && process.isAlive()) Sandbox.killAll();
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (RuntimeException ignored) {
            // khong liet ke duoc tien trinh con tren he dieu hanh nay -> van dung tien trinh chinh
        }
        if (process.isAlive()) process.destroyForcibly();
    }

    private ExecutionResult collect(Process process, String stdin, long timeLimitMs, boolean sandboxed)
            throws InterruptedException {
        long start = System.nanoTime();

        Thread feeder = new Thread(() -> {
            try (OutputStream os = process.getOutputStream()) {
                if (stdin != null) os.write(stdin.getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException ignored) {
                // tien trinh co the ket thuc som, khong doc het stdin -> bo qua
            }
        });
        feeder.setDaemon(true);

        StreamCollector out = new StreamCollector(process.getInputStream());
        StreamCollector err = new StreamCollector(process.getErrorStream());

        feeder.start();
        out.start();
        err.start();

        boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        if (!finished) {
            killTree(process, sandboxed);
            // Co gioi han: neu vi ly do nao do tien trinh khong chet, bo cham van di tiep thay vi treo mai.
            process.waitFor(15, TimeUnit.SECONDS);
        }

        out.join(2000);
        err.join(2000);
        feeder.join(200);

        int exitCode = finished ? process.exitValue() : -1;
        return new ExecutionResult(exitCode, out.getText(), cleanStderr(err.getText()), elapsedMs, !finished);
    }

    /** Canh bao co dinh cua JDK khi bat Security Manager (xem Sandbox): khong phai loi cua thi sinh. */
    private static final Pattern SECURITY_MANAGER_WARNING = Pattern.compile(
            "(?m)^WARNING: (?:A command line option has enabled the Security Manager|The Security Manager is deprecated[^\\n]*)\\.?\\r?\\n?");

    private static String cleanStderr(String stderr) {
        return SECURITY_MANAGER_WARNING.matcher(stderr).replaceAll("");
    }

    /** Thread doc mot luong den EOF, gioi han dung luong. */
    private static class StreamCollector extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamCollector(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            int n;
            try {
                while ((n = in.read(chunk)) != -1) {
                    if (buffer.size() < OUTPUT_CAP_BYTES) {
                        buffer.write(chunk, 0, n);
                    }
                }
            } catch (IOException ignored) {
                // luong bi dong khi tien trinh bi kill -> bo qua
            }
        }

        String getText() {
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
