package com.ptit.oj.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sua hang loat cau hinh de bai da nam san tren may chu.
 *
 * Vi sao can: tren Railway, data/ la mot Volume. Luc khoi dong, docker-entrypoint.sh chep de bai tu
 * image vao bang "cp -n" (khong ghi de), de giu cac chinh sua giang vien lam tren web. Nen sua
 * problem.properties trong repo KHONG toi duoc may chu dang chay - phai sua tai cho.
 *
 * Moi file data/migrations/&lt;ten&gt;.txt la mot dot sua:
 * <pre>
 *   # comparator: token -&gt; linetokens
 *   K002
 *   K003
 * </pre>
 * Chi doi bai DANG o kieu cu (bai giang vien da tu doi sang kieu khac thi de nguyen). Chay xong ghi
 * dau data/state/migrations/&lt;ten&gt;.done de khong chay lai - giang vien doi nguoc lai ve sau cung
 * khong bi ghi de.
 */
public final class ProblemMigrations {

    private static final Pattern HEADER = Pattern.compile("#\\s*comparator\\s*:\\s*(\\S+)\\s*->\\s*(\\S+)\\s*");

    private ProblemMigrations() { }

    public static void run(Path dataDir) {
        Path dir = dataDir.resolve("migrations");
        if (!Files.isDirectory(dir)) return;
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : stream) files.add(p);
        } catch (IOException e) {
            System.out.println("Không đọc được thư mục " + dir + ": " + e.getMessage());
            return;
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString().replaceFirst("\\.txt$", "");
            Path done = dataDir.resolve("state").resolve("migrations").resolve(name + ".done");
            if (Files.exists(done)) continue;
            try {
                int changed = apply(dataDir.resolve("problems"), file);
                Files.createDirectories(done.getParent());
                Files.write(done, ("changed=" + changed + "\n").getBytes(StandardCharsets.UTF_8));
                System.out.println("Cập nhật đề bài (" + name + "): đã sửa " + changed + " bài.");
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("Cập nhật đề bài (" + name + ") thất bại: " + e.getMessage());
            }
        }
    }

    private static int apply(Path problemsDir, Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String from = null;
        String to = null;
        List<String> ids = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m = HEADER.matcher(line);
            if (m.matches()) { from = m.group(1); to = m.group(2); continue; }
            if (line.startsWith("#")) continue;
            ids.add(line);
        }
        if (from == null) throw new IllegalArgumentException("thiếu dòng \"# comparator: cũ -> mới\"");
        Pattern old = Pattern.compile("^(\\s*comparator\\s*[=:]\\s*)" + Pattern.quote(from) + "\\s*$");
        int changed = 0;
        for (String id : ids) {
            // Ma bai chi gom chu, so, gach: khong de file danh sach tro ra ngoai thu muc de bai.
            if (!id.matches("[A-Za-z0-9_-]+")) continue;
            Path props = problemsDir.resolve(id).resolve("problem.properties");
            if (!Files.isRegularFile(props)) continue;
            String text = new String(Files.readAllBytes(props), StandardCharsets.UTF_8);
            String eol = text.contains("\r\n") ? "\r\n" : "\n";
            String[] parts = text.split("\r?\n", -1);
            boolean hit = false;
            for (int i = 0; i < parts.length; i++) {
                Matcher m = old.matcher(parts[i]);
                if (m.matches()) { parts[i] = m.group(1) + to; hit = true; }
            }
            if (!hit) continue;
            Files.write(props, String.join(eol, parts).getBytes(StandardCharsets.UTF_8));
            changed++;
        }
        return changed;
    }
}
