package com.ptit.oj.controller.judge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ngon ngu JavaScript, chay bang Node.js.
 * La ngon ngu thong dich nen "bien dich" chi la kiem tra cu phap (node --check).
 * Gioi han bo nho bang --max-old-space-size.
 */
public class JavaScriptLanguage extends Language {

    @Override public String getName() { return "JavaScript"; }
    @Override public String getFileExtension() { return ".js"; }
    @Override public long getStartupOverheadMs() { return 250; }

    @Override
    protected List<String> getVersionCommand() {
        return Arrays.asList("node", "--version");
    }

    @Override
    public CompileResult compile(Path source, Path workDir) throws IOException, InterruptedException {
        return runCompiler(workDir, "node", "--v8-pool-size=1", "--check", source.toString());
    }

    /** V8 giu cho truoc nhieu dia chi ao nen gioi han theo bo nho du lieu; Node can 4 luong voi --v8-pool-size=1. */
    @Override
    protected Sandbox.Limits compileLimits() {
        return Sandbox.Limits.forManaged(8, 512, 30, 64);
    }

    @Override
    public Sandbox.Limits runLimits(int memoryLimitMb, long timeLimitMs) {
        return Sandbox.Limits.forManaged(8, memoryLimitMb + 128, Sandbox.Limits.cpuSecondsFor(timeLimitMs, 2), 64);
    }

    @Override
    public List<String> buildRunCommand(Path source, Path workDir, int memoryLimitMb) {
        List<String> command = new ArrayList<>(Arrays.asList("node", "--v8-pool-size=1", "--max-old-space-size=" + memoryLimitMb));
        command.addAll(Sandbox.nodeRunArgs(workDir));
        command.add(source.toString());
        return command;
    }
}
