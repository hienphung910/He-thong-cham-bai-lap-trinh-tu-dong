package com.ptit.oj.controller.api;

import com.ptit.oj.controller.AssignmentService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.controller.SchoolService;
import com.ptit.oj.model.Assignment;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 13 (bai tap duoc giao):
 *   GET    /api/assignments                - bai tap giao cua cac lop minh thuoc ve, kem tien do cua minh
 *   POST   /api/assignments                - giang vien giao bai cho mot lop
 *   GET    /api/assignments/{id}/progress  - bang tien do ca lop (nguoi quan ly lop)
 *   DELETE /api/assignments/{id}
 */
public class AssignmentApi extends ApiModule {

    private final AssignmentService assignments;
    private final SchoolService school;

    public AssignmentApi(Platform platform) {
        super(platform);
        this.assignments = platform.getAssignments();
        this.school = platform.getSchool();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "assignments")) {
            User user = req.user();
            Set<String> solved = platform.getAnalytics().solvedIds(judge.getSubmissionsOf(user));
            List<String> items = new ArrayList<>();
            for (Assignment a : assignments.ofClassrooms(school.classroomsOf(user))) {
                items.add(assignmentJson(a, user, solved));
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "assignments")) {
            User user = req.staff();
            Json.Value body = req.body();
            Classroom classroom = school.findClassroom(body.getString("classroomId"));
            school.requireManager(user, classroom);
            Assignment created = assignments.create(classroom, body.getString("title"),
                    body.getString("description"), problemIds(body, "problemIds", user),
                    parseTime(body.getString("dueAt")), user);
            platform.getCommunity().notifyMany(classroom.getStudentIds(),
                    "Bài tập mới: " + created.getTitle(),
                    classroom.getName() + " có bài tập mới, hạn nộp " + time(created.getDueAt()).replace('T', ' '),
                    "Assignments");
            req.sendOk();
            return true;
        }
        if (req.is("GET", "assignments", "*", "progress")) {
            User user = req.staff();
            Assignment a = assignments.find(req.arg(0));
            Classroom classroom = school.findClassroom(a.getClassroomId());
            school.requireManager(user, classroom);
            req.send(progressJson(a, classroom));
            return true;
        }
        if (req.is("DELETE", "assignments", "*")) {
            User user = req.staff();
            Assignment a = assignments.find(req.arg(0));
            school.requireManager(user, school.findClassroom(a.getClassroomId()));
            assignments.delete(a.getId());
            req.sendOk();
            return true;
        }
        return false;
    }

    /** Mot bai tap giao kem trang thai tung bai doi voi nguoi dang xem. */
    private String assignmentJson(Assignment a, User viewer, Set<String> solved) {
        Classroom classroom = school.findClassroom(a.getClassroomId());
        List<String> problems = new ArrayList<>();
        int solvedCount = 0;
        for (String id : a.getProblemIds()) {
            boolean done = solved.contains(id);
            if (done) solvedCount++;
            problems.add(Json.obj().put("id", id)
                    .put("title", judge.findProblem(id).map(Problem::getTitle).orElse(id))
                    .put("solved", done).build());
        }
        return Json.obj()
                .put("id", a.getId())
                .put("classroomId", a.getClassroomId())
                .put("classroomName", classroom.getName())
                .put("title", a.getTitle())
                .put("description", a.getDescription())
                .put("dueAt", time(a.getDueAt()))
                .put("overdue", a.isOverdue())
                .put("createdBy", a.getCreatedBy())
                .put("solvedCount", solvedCount)
                .put("total", a.getProblemIds().size())
                .put("canManage", school.canManage(viewer, classroom))
                .putRaw("problems", Json.array(problems))
                .build();
    }

    /** Bang tien do: moi hoc sinh mot dong, moi bai mot o da lam / chua lam. */
    private String progressJson(Assignment a, Classroom classroom) {
        Map<String, User> users = usersById();
        Map<String, Set<String>> solvedByUser = platform.getAnalytics().solvedByUser();
        List<String> rows = new ArrayList<>();
        for (String studentId : classroom.getStudentIds()) {
            User student = users.get(studentId);
            if (student == null) continue;
            Set<String> solved = solvedByUser.getOrDefault(studentId, Collections.<String>emptySet());
            List<String> cells = new ArrayList<>();
            int solvedCount = 0;
            for (String problemId : a.getProblemIds()) {
                boolean done = solved.contains(problemId);
                if (done) solvedCount++;
                cells.add(String.valueOf(done));
            }
            rows.add(Json.obj().put("username", student.getUsername()).put("fullName", student.getFullName())
                    .put("solvedCount", solvedCount).putRaw("solved", Json.array(cells)).build());
        }
        return Json.obj().put("title", a.getTitle())
                .putRaw("problemIds", stringArray(a.getProblemIds()))
                .putRaw("rows", Json.array(rows)).build();
    }
}
