package com.ptit.oj.controller.judge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chay mot tien trinh con: bom stdin, thu stdout/stderr bang thread rieng
 * (tranh deadlock khi buffer cua OS bi day) va cuong che dung khi qua thoi gian.
 */
public class ProcessRunner {

    private static final int OUTPUT_CAP_BYTES = 1 << 20; // 1 MB, chong sinh output vo han

    public ExecutionResult run(List<String> command, Path workDir, String stdin, long timeLimitMs)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        // Khong truyen cau hinh cua may chu (mat khau CSDL, mat khau admin...) cho tien trinh con.
        // Day chi la mot lop chan them, KHONG thay duoc sandbox.
        pb.environment().keySet().removeIf(name -> name.toUpperCase().startsWith("OJ_"));
        Process process = pb.start();
        try {
            return collect(process, stdin, timeLimitMs);
        } finally {
            // Chay ca khi thread cham bi ngat (may chu dang dung): khong de lai tien trinh mo coi.
            killTree(process);
        }
    }

    /** Dung tien trinh va moi tien trinh con chau con song cua no. */
    private static void killTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (RuntimeException ignored) {
            // khong liet ke duoc tien trinh con tren he dieu hanh nay -> van dung tien trinh chinh
        }
        if (process.isAlive()) process.destroyForcibly();
    }

    private ExecutionResult collect(Process process, String stdin, long timeLimitMs) throws InterruptedException {
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
            killTree(process);
            process.waitFor();
        }

        out.join(2000);
        err.join(2000);
        feeder.join(200);

        int exitCode = finished ? process.exitValue() : -1;
        return new ExecutionResult(exitCode, out.getText(), err.getText(), elapsedMs, !finished);
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
