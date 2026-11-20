package com.ptit.oj.controller.judge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Ngon ngu C++: bien dich bang g++ thanh file thuc thi roi chay truc tiep. */
public class CppLanguage extends Language {

    @Override public String getName() { return "C++"; }
    @Override public String getFileExtension() { return ".cpp"; }

    @Override
    protected List<String> getVersionCommand() {
        return Arrays.asList("g++", "--version");
    }

    @Override
    public CompileResult compile(Path source, Path workDir) throws IOException, InterruptedException {
        return runCompiler(workDir, Arrays.asList("g++", "-O2", "-std=c++17",
                "-o", executablePath(source, workDir).toString(), source.toString()));
    }

    @Override
    public List<String> buildRunCommand(Path source, Path workDir, int memoryLimitMb) {
        // Khong ep duoc bo nho o muc tien trinh -> chi phat hien MLE qua std::bad_alloc.
        return Collections.singletonList(executablePath(source, workDir).toString());
    }
}
