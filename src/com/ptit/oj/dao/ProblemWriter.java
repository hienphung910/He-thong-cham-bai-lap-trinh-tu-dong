package com.ptit.oj.dao;

import com.ptit.oj.model.ProblemDraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Ghi bai tap ra o dia theo dung cau truc ma ProblemLoader doc duoc:
 *
 *   data/problems/P004/problem.properties
 *   data/problems/P004/statement.txt
 *   data/problems/P004/tests/sample01.in / .out / 01.in / 01.out ...
 *
 * Luon ghi vao cho TAM truoc roi moi doi ten thanh ten that, nen tren o dia
 * khong bao gio co bai tap hay file problem.properties bi ghi do dang.
 */
public class ProblemWriter {

    /** ProblemLoader bo qua cac thu muc bat dau bang tien to nay. */
    public static final String TEMP_PREFIX = "_tmp_";

    private final Path problemsDir;

    public ProblemWriter(Path problemsDir) {
        this.problemsDir = problemsDir;
    }

    public boolean exists(String problemId) {
        return Files.isDirectory(problemsDir.resolve(problemId));
    }

    /**
     * Tao thu muc cho bai tap moi va tra ve thu muc do.
     * Nguoi goi phai kiem tra hop le TRUOC (xem ProblemAdminService).
     */
    public Path write(ProblemDraft draft) throws IOException {
        Files.createDirectories(problemsDir);
        Path target = problemsDir.resolve(draft.getId());
        if (Files.exists(target)) {
            throw new IOException("Thư mục bài " + draft.getId() + " đã tồn tại");
        }

        Path temp = problemsDir.resolve(tempName());
        try {
            Files.createDirectory(temp);
            writeText(temp.resolve("problem.properties"), buildProperties(draft));
            writeText(temp.resolve("statement.txt"), draft.getStatement());

            Path testsDir = temp.resolve("tests");
            Files.createDirectory(testsDir);
            for (ProblemDraft.TestDraft t : draft.getTests()) {
                writeText(testsDir.resolve(t.getName() + ".in"), t.getInput());
                writeText(testsDir.resolve(t.getName() + ".out"), t.getOutput());
            }

            Files.move(temp, target);   // doi ten trong cung thu muc cha: xong la co du ca bai
            return target;
        } catch (IOException | RuntimeException e) {
            deleteRecursively(temp);
            throw e;
        }
    }

    /**
     * Sua vai khoa trong problem.properties cua mot bai DA CO, vi du topic / difficulty.
     * Gia tri null nghia la xoa dong do. Moi dong khac giu nguyen, ke ca thu tu.
     */
    public Path updateProperties(String problemId, Map<String, String> changes) throws IOException {
        Path dir = problemsDir.resolve(problemId);
        Path file = dir.resolve("problem.properties");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Không thấy " + file);
        }

        String text = TextUtils.stripBom(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        String newline = text.contains("\r\n") ? "\r\n" : "\n";

        List<String> out = new ArrayList<>();
        Set<String> written = new HashSet<>();
        for (String line : text.split("\r?\n")) {
            String key = keyOf(line);
            if (key == null || !changes.containsKey(key)) {
                out.add(line);                              // dong khong lien quan: giu nguyen
            } else if (changes.get(key) != null && written.add(key)) {
                out.add(key + "=" + escapeProperty(changes.get(key)));
            }
        }
        // Khoa chua co trong file thi them vao cuoi.
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (e.getValue() != null && !written.contains(e.getKey())) {
                out.add(e.getKey() + "=" + escapeProperty(e.getValue()));
            }
        }

        Path temp = dir.resolve(tempName() + ".properties");
        try {
            writeText(temp, String.join(newline, out) + newline);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return dir;
    }

    public Path problemDir(String problemId) {
        return problemsDir.resolve(problemId);
    }

    /** Them mot test (hai file ten.in / ten.out) vao bai da co. Ten test da duoc kiem tra truoc. */
    public void writeTest(String problemId, String name, String input, String output) throws IOException {
        Path testsDir = problemDir(problemId).resolve("tests");
        Files.createDirectories(testsDir);
        writeText(testsDir.resolve(name + ".in"), input);
        writeText(testsDir.resolve(name + ".out"), output);
    }

    public void deleteTest(String problemId, String name) throws IOException {
        Path testsDir = problemDir(problemId).resolve("tests");
        Files.deleteIfExists(testsDir.resolve(name + ".in"));
        Files.deleteIfExists(testsDir.resolve(name + ".out"));
    }

    /** Ten khoa cua mot dong "khoa=gia tri", hoac null neu la dong trong / ghi chu. */
    private static String keyOf(String line) {
        String trimmed = line.trim();
        int eq = trimmed.indexOf('=');
        return trimmed.startsWith("#") || eq <= 0 ? null : trimmed.substring(0, eq).trim();
    }

    private static String tempName() {
        return TEMP_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Noi dung problem.properties cua bai moi. */
    private String buildProperties(ProblemDraft d) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Sinh tu giao dien quan tri (POST /api/admin/problems)\n");
        sb.append("id=").append(escapeProperty(d.getId())).append('\n');
        sb.append("title=").append(escapeProperty(d.getTitle())).append('\n');
        sb.append("timeLimitMs=").append(d.getTimeLimitMs()).append('\n');
        sb.append("memoryLimitMb=").append(d.getMemoryLimitMb()).append('\n');
        sb.append("comparator=").append(escapeProperty(d.getComparator())).append('\n');
        sb.append("totalPoints=").append(formatPoints(d.getTotalPoints())).append('\n');
        if (d.getTopic() != null && !d.getTopic().isEmpty()) {
            sb.append("topic=").append(escapeProperty(d.getTopic())).append('\n');
        }
        if (d.getDifficulty() > 0) {
            sb.append("difficulty=").append(d.getDifficulty()).append('\n');
        }
        return sb.toString();
    }

    private String formatPoints(double points) {
        if (points == Math.rint(points) && Math.abs(points) < 1e15) return String.valueOf((long) points);
        return String.valueOf(points);
    }

    /**
     * Trong file .properties cac ky tu = : # ! va dau xuong dong phai duoc escape,
     * de ten bai co dau ':' hay '=' khong lam hong file.
     */
    private String escapeProperty(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '=':  sb.append("\\="); break;
                case ':':  sb.append("\\:"); break;
                case '#':  sb.append("\\#"); break;
                case '!':  sb.append("\\!"); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Moi file deu ghi UTF-8 - ProblemLoader cung doc UTF-8. */
    private void writeText(Path file, String content) throws IOException {
        Files.write(file, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // don dep het suc; file con lai se mang tien to _tmp_ nen bi bo qua khi nap
                }
            });
        } catch (IOException ignored) {
            // bo qua
        }
    }
}
