package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.controller.PlaygroundService;
import com.ptit.oj.controller.judge.ExecutionResult;
import com.ptit.oj.controller.judge.Language;

import java.io.IOException;

/**
 * Module 10 (IDE truc tuyen) va 12 (stress test):
 *   POST /api/run     {language, code, input}                      - chay thu voi input tu nhap
 *   POST /api/stress  {language, solution, brute, generator, rounds} - tim phan vi du
 * Module 11 (cong cu CP: sinh test, so sanh output...) chay hoan toan o trinh duyet.
 */
public class PlaygroundApi extends ApiModule {

    private final PlaygroundService playground;

    public PlaygroundApi(Platform platform) {
        super(platform);
        this.playground = platform.getPlayground();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("POST", "run")) {
            req.user();
            Json.Value body = req.body();
            PlaygroundService.RunResult result = playground.run(
                    language(body), body.getString("code"), body.getString("input"));

            Json.Obj o = Json.obj().put("compileError", result.getCompileError());
            ExecutionResult exec = result.getExecution();
            if (exec != null) {
                o.put("stdout", exec.getStdout()).put("stderr", exec.getStderr())
                 .put("exitCode", exec.getExitCode()).put("timeMs", exec.getElapsedMs())
                 .put("timedOut", exec.isTimedOut());
            }
            req.send(o.build());
            return true;
        }
        return false;
    }

    private Language language(Json.Value body) {
        String name = body.getString("language");
        return judge.findLanguage(name)
                .orElseThrow(() -> ApiException.badRequest("Không hỗ trợ ngôn ngữ " + name));
    }
}
