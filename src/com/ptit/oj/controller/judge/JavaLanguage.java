package com.ptit.oj.controller.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ngon ngu Java: bien dich bang javac, gioi han bo nho bang -Xmx. */
public class JavaLanguage extends Language {

    @Override public String getName() { return "Java"; }
    @Override public String getFileExtension() { return ".java"; }
    @Override public long getStartupOverheadMs() { return 400; }    // JVM khoi dong cham

    @Override
    protected List<String> getVersionCommand() {
        return Arrays.asList("javac", "-version");
    }

    /**
     * Java bat buoc ten file trung ten kieu public (neu co), nen phai doc ten tu ma nguon.
     * Khong co kieu public thi lay class chua ham main, roi den class dau tien.
     */
    @Override
    public String defaultFileName(String sourceCode) {
        return SourceInfo.of(sourceCode).fileBaseName() + getFileExtension();
    }

    @Override
    public CompileResult compile(Path source, Path workDir) throws IOException, InterruptedException {
        return runCompiler(workDir, "javac", "-J-XX:+UseSerialGC", "-J-XX:ActiveProcessorCount=1", "-J-Xmx512m",
                "-encoding", "UTF-8", "-d", workDir.toString(), source.toString());
    }

    /** JVM giu cho truoc nhieu dia chi ao nen gioi han theo bo nho du lieu; javac can 16 luong. */
    @Override
    protected Sandbox.Limits compileLimits() {
        return Sandbox.Limits.forManaged(24, 1024, 60, 256);
    }

    /**
     * JVM can 14 luong voi -XX:+UseSerialGC -XX:ActiveProcessorCount=1 (mac dinh 19, tang theo so CPU cua may);
     * de 24 cho chuong trinh tao them vai luong (vd. luong co ngan xep lon de de quy sau). Bo nho du lieu =
     * heap + 128 MB cho metaspace, code cache, ngan xep: bo nho ngoai heap (ByteBuffer.allocateDirect...) cung
     * khong vuot duoc.
     */
    @Override
    public Sandbox.Limits runLimits(int memoryLimitMb, long timeLimitMs) {
        return Sandbox.Limits.forManaged(24, memoryLimitMb + 128, Sandbox.Limits.cpuSecondsFor(timeLimitMs, 2), 128);
    }

    @Override
    public List<String> buildRunCommand(Path source, Path workDir, int memoryLimitMb) {
        // Log cua JVM (vd. "Failed to start thread" khi cham gioi han luong) mac dinh in ra STDOUT, lan vao
        // ket qua cua thi sinh: chuyen sang stderr.
        List<String> command = new ArrayList<>(Arrays.asList("java",
                "-Xlog:disable", "-Xlog:all=warning:stderr",
                "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=1",
                "-Xmx" + memoryLimitMb + "m",
                "-Dfile.encoding=UTF-8"));
        command.addAll(Sandbox.javaRunArgs());
        command.addAll(Arrays.asList("-cp", workDir.toString(), mainClassOf(source)));
        return command;
    }

    /** Ten day du (kem package) cua class chua ham main; khong doc duoc file thi dung ten file. */
    private String mainClassOf(Path source) {
        try {
            String code = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            return SourceInfo.of(code).runClassName(baseName(source));
        } catch (IOException e) {
            return baseName(source);
        }
    }

    /**
     * Nhung gi can biet ve mot file ma nguon Java de dat ten file va chon class de chay.
     *
     * Doc MOT LUOT tung ky tu (khong dung regex): thoi gian ty le thuan voi do dai ma nguon,
     * nen mot bai nop toan dong trong hay toan tu khoa khong lam bo cham dung lai. Chu thich,
     * chuoi va ky tu duoc bo qua; chi cac kieu khai bao o ngoai cung (do sau ngoac = 0) duoc ghi nhan,
     * nen class long ben trong khong bi nham la class chinh.
     */
    static final class SourceInfo {
        private String packageName = "";
        private String publicType;      // kieu public o ngoai cung -> quyet dinh ten file
        private String mainType;        // kieu ngoai cung chua "void main("
        private String firstType;

        static SourceInfo of(String sourceCode) {
            SourceInfo info = new SourceInfo();
            info.scan(sourceCode == null ? "" : sourceCode);
            return info;
        }

        String fileBaseName() {
            if (publicType != null) return publicType;
            if (mainType != null) return mainType;
            return firstType != null ? firstType : "Main";
        }

        String runClassName(String fallback) {
            String simple = mainType != null ? mainType
                    : publicType != null ? publicType
                    : firstType != null ? firstType : fallback;
            return packageName.isEmpty() ? simple : packageName + "." + simple;
        }

        private void scan(String s) {
            int n = s.length();
            int depth = 0;
            boolean sawPublic = false;       // da gap "public" ke tu dau khai bao hien tai
            boolean expectTypeName = false;  // vua gap class / interface / enum o ngoai cung
            boolean inPackage = false;
            String currentType = null;       // kieu ngoai cung dang mo
            String pendingType = null;       // da doc ten kieu, dang cho dau {
            String previous = "";            // dinh danh ngay truoc
            StringBuilder pkg = new StringBuilder();

            int i = 0;
            while (i < n) {
                char c = s.charAt(i);

                // --- bo qua chu thich, chuoi, ky tu ---
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    i += 2;
                    while (i < n && s.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    int end = s.indexOf("*/", i + 2);
                    i = end < 0 ? n : end + 2;
                    continue;
                }
                if (c == '"' && s.startsWith("\"\"\"", i)) {          // text block
                    int end = s.indexOf("\"\"\"", i + 3);
                    i = end < 0 ? n : end + 3;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    i++;
                    while (i < n && s.charAt(i) != c && s.charAt(i) != '\n') {
                        if (s.charAt(i) == '\\') i++;
                        i++;
                    }
                    i++;
                    continue;
                }

                // --- dinh danh / tu khoa ---
                if (Character.isJavaIdentifierStart(c)) {
                    int start = i;
                    while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                    String word = s.substring(start, i);

                    if (inPackage) {
                        pkg.append(word);
                    } else if (expectTypeName) {
                        expectTypeName = false;
                        pendingType = word;
                        if (firstType == null) firstType = word;
                        if (sawPublic && publicType == null) publicType = word;
                    } else if (depth == 0) {
                        if (word.equals("public")) {
                            sawPublic = true;
                        } else if (word.equals("package") && firstType == null) {
                            inPackage = true;
                        } else if ((word.equals("class") || word.equals("interface") || word.equals("enum"))
                                && pendingType == null) {
                            expectTypeName = true;
                        }
                    } else if (word.equals("main") && previous.equals("void")
                            && mainType == null && nextNonSpaceIs(s, i, '(')) {
                        mainType = currentType;
                    }
                    previous = word;
                    continue;
                }

                // --- dau cau ---
                if (c == '.' && inPackage) {
                    pkg.append('.');
                } else if (c == ';') {
                    if (inPackage) {
                        inPackage = false;
                        packageName = pkg.toString();
                    }
                    if (depth == 0) sawPublic = false;
                } else if (c == '{') {
                    if (depth == 0) {
                        currentType = pendingType;
                        pendingType = null;
                        sawPublic = false;
                    }
                    depth++;
                } else if (c == '}') {
                    if (depth > 0) depth--;
                    if (depth == 0) {
                        currentType = null;
                        sawPublic = false;
                    }
                }
                if (!Character.isWhitespace(c)) previous = "";
                i++;
            }
        }

        private static boolean nextNonSpaceIs(String s, int from, char expected) {
            int i = from;
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            return i < s.length() && s.charAt(i) == expected;
        }
    }
}
