package com.ptit.oj.controller.judge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ngon ngu Java: bien dich bang javac, gioi han bo nho bang -Xmx. */
public class JavaLanguage extends Language {

    /** Ten class trong ma nguon, de dat ten file cho khop (yeu cau cua javac). */
    private static final Pattern CLASS_NAME = Pattern.compile(
            "(?m)^\\s*(?:public\\s+)?(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_$][\\w$]*)");

    @Override public String getName() { return "Java"; }
    @Override public String getFileExtension() { return ".java"; }
    @Override public long getStartupOverheadMs() { return 400; }    // JVM khoi dong cham

    @Override
    protected List<String> getVersionCommand() {
        return Arrays.asList("javac", "-version");
    }

    /**
     * Java bat buoc ten file trung ten class public, va lenh chay cung dung
     * ten file lam ten class -> phai doc ten class tu ma nguon.
     * Han che: chi bat class dau tien khai bao o dau dong, du cho bai nop thong thuong.
     */
    @Override
    public String defaultFileName(String sourceCode) {
        Matcher m = CLASS_NAME.matcher(sourceCode == null ? "" : sourceCode);
        return (m.find() ? m.group(1) : "Main") + getFileExtension();
    }

    @Override
    public CompileResult compile(Path source, Path workDir) throws IOException, InterruptedException {
        return runCompiler(workDir,
                Arrays.asList("javac", "-encoding", "UTF-8", "-d", workDir.toString(), source.toString()));
    }

    @Override
    public List<String> buildRunCommand(Path source, Path workDir, int memoryLimitMb) {
        return Arrays.asList("java",
                "-Xmx" + memoryLimitMb + "m",
                "-Dfile.encoding=UTF-8",
                "-cp", workDir.toString(),
                baseName(source));
    }
}
