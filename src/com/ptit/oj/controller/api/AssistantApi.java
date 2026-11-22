package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Module 27 (tro ly cho giang vien) va 28 (tro ly cho hoc sinh) - ban mo phong theo luat:
 *   POST /api/assistant/hint                      - goi y cho mot bai
 *   GET  /api/assistant/classrooms/{id}           - tom tat tinh hinh lop
 *   GET  /api/assistant/students/{username}       - nhan xet ve mot hoc sinh
 */
public class AssistantApi extends ApiModule {

    public AssistantApi(Platform platform) {
        super(platform);
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("POST", "assistant", "hint")) {
            User user = req.user();
            Json.Value body = req.body();
            String problemId = body.getString("problemId");
            Problem problem = judge.findProblem(problemId)
                    .orElseThrow(() -> ApiException.notFound("Không có bài " + problemId));
            List<String> lines = platform.getAssistant().hint(problem, user,
                    body.getString("code"), body.getString("question"));
            req.send(linesJson(lines));
            return true;
        }
        if (req.is("GET", "assistant", "classrooms", "*")) {
            User user = req.staff();
            Classroom classroom = platform.getSchool().findClassroom(req.arg(0));
            platform.getSchool().requireManager(user, classroom);
            Map<String, User> users = usersById();
            List<User> students = new ArrayList<>();
            for (String id : classroom.getStudentIds()) {
                if (users.containsKey(id)) students.add(users.get(id));
            }
            req.send(linesJson(platform.getAssistant().classSummary(classroom, students)));
            return true;
        }
        if (req.is("GET", "assistant", "students", "*")) {
            req.staff();
            req.send(linesJson(platform.getAssistant().studentComment(requireUsername(req.arg(0)))));
            return true;
        }
        return false;
    }

    /** simulated = true de giao dien luon ghi ro day la cau tra loi sinh theo luat. */
    private String linesJson(List<String> lines) {
        return Json.obj().put("simulated", true).putRaw("lines", stringArray(lines)).build();
    }
}
