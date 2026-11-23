package com.ptit.oj.controller.api;

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
 * Module 7 (quan ly bo test). Day la noi duy nhat tra ve test an, nen ca ba endpoint deu
 * kiem tra quyen SUA BAI: quan tri vien voi moi bai, giang vien chi voi bai trong kho cua minh.
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
            User user = req.staff();
            // findProblemForEdit nem 403 neu day khong phai bai nguoi nay duoc sua.
            req.send(testsJson(judge.findProblemForEdit(user, req.arg(0))));
            return true;
        }
        if (req.is("POST", "admin", "problems", "*", "tests")) {
            User user = req.staff();
            Json.Value body = req.largeBody();
            Problem updated = judge.addTest(user, req.arg(0), new ProblemDraft.TestDraft(
                    body.getString("name"), body.getString("input"), body.getString("output"),
                    body.getBoolean("sample", false)));
            req.send(testsJson(updated));
            return true;
        }
        if (req.is("DELETE", "admin", "problems", "*", "tests", "*")) {
            User user = req.staff();
            req.send(testsJson(judge.deleteTest(user, req.arg(0), req.arg(1))));
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
