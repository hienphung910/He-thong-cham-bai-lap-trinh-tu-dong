package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.ProblemDraft;
import com.ptit.oj.model.TestCase;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 7 (quan ly bo test) - chi quan tri vien, vi day la noi duy nhat tra ve test an:
 *   GET    /api/admin/problems/{id}/tests          - toan bo test cua bai (ca test an)
 *   POST   /api/admin/problems/{id}/tests          - them mot test
 *   DELETE /api/admin/problems/{id}/tests/{ten}    - xoa mot test
 */
public class TestcaseApi extends ApiModule {

    /** Chi gui ve toi da chung nay ky tu moi test de trang quan tri khong phai tai du lieu lon. */
    private static final int PREVIEW_CHARS = 2000;

    public TestcaseApi(Platform platform) {
        super(platform);
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "admin", "problems", "*", "tests")) {
            req.admin();
            String id = req.arg(0);
            Problem problem = judge.findProblem(id).orElseThrow(() -> ApiException.notFound("Không có bài " + id));
            req.send(testsJson(problem));
            return true;
        }
        if (req.is("POST", "admin", "problems", "*", "tests")) {
            User admin = req.admin();
            Json.Value body = req.body();
            Problem updated = judge.addTest(admin, req.arg(0), new ProblemDraft.TestDraft(
                    body.getString("name"), body.getString("input"), body.getString("output"),
                    body.getBoolean("sample", false)));
            req.send(testsJson(updated));
            return true;
        }
        if (req.is("DELETE", "admin", "problems", "*", "tests", "*")) {
            User admin = req.admin();
            req.send(testsJson(judge.deleteTest(admin, req.arg(0), req.arg(1))));
            return true;
        }
        return false;
    }

    private String testsJson(Problem problem) {
        List<String> items = new ArrayList<>();
        for (TestCase tc : problem.getTestCases()) {
            items.add(Json.obj().put("name", tc.getId()).put("sample", tc.isSample())
                    .put("points", tc.getPoints())
                    .put("input", preview(tc.getInput())).put("output", preview(tc.getExpectedOutput()))
                    .build());
        }
        return Json.array(items);
    }

    private String preview(String text) {
        return text.length() <= PREVIEW_CHARS ? text : text.substring(0, PREVIEW_CHARS) + "\n... (đã cắt bớt)";
    }
}
