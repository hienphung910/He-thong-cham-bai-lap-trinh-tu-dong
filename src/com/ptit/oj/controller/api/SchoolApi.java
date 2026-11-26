package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.controller.SchoolService;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Organization;
import com.ptit.oj.model.ParentContact;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 2 (to chuc), 3 (lop hoc):
 *   /api/organizations, /api/classrooms
 */
public class SchoolApi extends ApiModule {

    private final SchoolService school;

    public SchoolApi(Platform platform) {
        super(platform);
        this.school = platform.getSchool();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        return handleOrganizations(req) || handleClassrooms(req) || handleMembers(req);
    }

    // ------------------------------------------------------------ to chuc

    private boolean handleOrganizations(Request req) throws IOException {
        if (req.is("GET", "organizations")) {
            req.user();
            List<String> items = new ArrayList<>();
            for (Organization o : school.getOrganizations()) {
                int classCount = 0;
                for (Classroom c : school.getClassrooms()) {
                    if (c.getOrganizationId().equals(o.getId())) classCount++;
                }
                items.add(Json.obj().put("id", o.getId()).put("name", o.getName())
                        .put("address", o.getAddress()).put("description", o.getDescription())
                        .put("classCount", classCount).build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "organizations")) {
            req.admin();
            Json.Value body = req.body();
            school.createOrganization(body.getString("name"), body.getString("address"), body.getString("description"));
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "organizations", "*")) {
            req.admin();
            school.deleteOrganization(req.arg(0));
            req.sendOk();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------- lop hoc

    private boolean handleClassrooms(Request req) throws IOException {
        if (req.is("GET", "classrooms")) {
            User user = req.user();
            Map<String, User> users = usersById();
            List<String> items = new ArrayList<>();
            for (Classroom c : school.classroomsOf(user)) {
                items.add(classroomJson(c, user, users).build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "classrooms")) {
            User teacher = req.staff();
            Json.Value body = req.body();
            Classroom created = school.createClassroom(body.getString("name"),
                    body.getString("organizationId"), body.getString("courseId"), teacher);
            req.send(classroomJson(created, teacher, usersById()).build());
            return true;
        }
        if (req.is("GET", "classrooms", "*")) {
            User user = req.user();
            Classroom c = school.findClassroom(req.arg(0));
            school.requireMember(user, c);
            req.send(classroomDetailJson(c, user));
            return true;
        }
        // Bang xep hang rieng cua lop: chi thanh vien lop (va quan tri vien) xem duoc.
        if (req.is("GET", "classrooms", "*", "scoreboard")) {
            User user = req.user();
            Classroom c = school.findClassroom(req.arg(0));
            school.requireMember(user, c);
            req.send(classScoreboardJson(c));
            return true;
        }
        if (req.is("DELETE", "classrooms", "*")) {
            User user = req.staff();
            Classroom c = school.findClassroom(req.arg(0));
            school.requireOwner(user, c);
            school.deleteClassroom(c.getId());
            // Xoa luon bai tap giao, buoi hoc va ban ghi cua lop: neu de lai thi khong ai xoa duoc
            // nua (moi thao tac deu can tim thay lop) va bai kho rieng trong do van bi coi la da cong bo.
            platform.getAssignments().deleteByClassroom(c.getId());
            platform.getMeetings().deleteByClassroom(c.getId());
            platform.getContests().deleteByClassroom(c.getId());
            req.sendOk();
            return true;
        }
        // Loi moi vao lop cua nguoi dang dang nhap, va viec dong y / tu choi.
        if (req.is("GET", "classroom-invites")) {
            User user = req.user();
            Map<String, User> users = usersById();
            List<String> items = new ArrayList<>();
            for (Classroom c : school.invitesOf(user)) {
                items.add(Json.obj().put("id", c.getId()).put("name", c.getName())
                        .put("teacherName", nameOf(users, c.getTeacherId())).build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "classrooms", "*", "accept")) {
            User user = req.user();
            school.acceptInvite(school.findClassroom(req.arg(0)), user);
            req.sendOk();
            return true;
        }
        if (req.is("POST", "classrooms", "*", "decline")) {
            User user = req.user();
            school.declineInvite(school.findClassroom(req.arg(0)), user);
            req.sendOk();
            return true;
        }
        // Hoc sinh xin vao lop: doan * trong duong dan la MA THAM GIA, khong phai ma lop.
        // Chi tao yeu cau cho giang vien duyet (pending = true); da duoc moi san thi vao luon (pending = false).
        if (req.is("POST", "classrooms", "*", "join")) {
            User user = req.user();
            // Ma tham gia dai 8 ky tu ngau nhien, nhung van chan viec do ma hang loat.
            if (platform.getLimiter().retryAfterSeconds("join:" + user.getId(), 10 * 60_000L, 20) > 0) {
                throw new ApiException(429, "Bạn nhập mã tham gia quá nhiều lần. Hãy thử lại sau ít phút");
            }
            platform.getLimiter().hit("join:" + user.getId(), 10 * 60_000L);
            Classroom c = school.join(req.arg(0), user);
            boolean pending = !c.getStudentIds().contains(user.getId());
            // Gui roi huy roi gui lai lien tuc: giang vien chi nhan MOT thong bao moi gio cho moi hoc sinh.
            if (pending && firstNotice("join-notice:" + c.getId() + "|" + user.getId())) {
                platform.getCommunity().notifyUser(c.getTeacherId(), "Yêu cầu vào lớp " + c.getName(),
                        user.getFullName() + " (@" + user.getUsername() + ") xin vào lớp " + c.getName()
                                + ". Vào Lớp của tôi để duyệt hoặc từ chối.",
                        "Classrooms");
            }
            req.send(Json.obj().put("id", c.getId()).put("name", c.getName()).put("pending", pending).build());
            return true;
        }
        // Cac yeu cau vao lop cua nguoi dang dang nhap con cho duyet, va viec tu huy.
        if (req.is("GET", "classroom-requests")) {
            User user = req.user();
            Map<String, User> users = usersById();
            List<String> items = new ArrayList<>();
            for (Classroom c : school.requestsOf(user)) {
                items.add(Json.obj().put("id", c.getId()).put("name", c.getName())
                        .put("teacherName", nameOf(users, c.getTeacherId())).build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("DELETE", "classrooms", "*", "request")) {
            User user = req.user();
            school.dropRequest(school.findClassroom(req.arg(0)), user.getId());
            req.sendOk();
            return true;
        }
        // Giang vien duyet / tu choi yeu cau vao lop.
        if (req.is("POST", "classrooms", "*", "requests", "*", "approve")) {
            Classroom c = managedClassroom(req);
            school.approveRequest(c, req.arg(1));
            platform.getCommunity().notifyUser(req.arg(1), "Bạn đã được duyệt vào lớp " + c.getName(),
                    req.user().getFullName() + " đã duyệt yêu cầu vào lớp " + c.getName() + " của bạn.", "Classrooms");
            req.sendOk();
            return true;
        }
        if (req.is("POST", "classrooms", "*", "requests", "*", "reject")) {
            Classroom c = managedClassroom(req);
            school.dropRequest(c, req.arg(1));
            platform.getCommunity().notifyUser(req.arg(1), "Yêu cầu vào lớp " + c.getName() + " bị từ chối",
                    "Giảng viên đã từ chối yêu cầu vào lớp " + c.getName() + " của bạn.", "Classrooms");
            req.sendOk();
            return true;
        }
        return false;
    }

    /** Moi / xoa hoc sinh va quan ly bai luyen tap cua mot lop - chi nguoi quan ly lop. */
    private boolean handleMembers(Request req) throws IOException {
        if (req.is("POST", "classrooms", "*", "students")) {
            Classroom c = managedClassroom(req);
            User student = requireUsername(req.body().getString("username"));
            // Chi MOI: hoc sinh phai tu dong y moi vao lop (moi lai nguoi da moi / da trong lop thi khong bao nua).
            // Moi roi rut roi moi lai lien tuc: hoc sinh chi nhan mot thong bao moi gio cho moi lop.
            if (school.invite(c, student) && firstNotice("invite-notice:" + c.getId() + "|" + student.getId())) {
                platform.getCommunity().notifyUser(student.getId(), "Bạn được mời vào lớp " + c.getName(),
                        req.user().getFullName() + " mời bạn vào lớp " + c.getName()
                                + ". Vào Lớp của tôi để đồng ý hoặc từ chối.",
                        "Classrooms");
            }
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "classrooms", "*", "students", "*")) {
            Classroom c = ownedClassroom(req);
            school.removeStudent(c, req.arg(1));
            req.sendOk();
            return true;
        }
        // Bai luyen tap rieng cua lop: giang vien chi dung duoc bai kho chung va bai kho cua minh.
        if (req.is("POST", "classrooms", "*", "problems")) {
            User user = req.staff();
            Classroom c = school.findClassroom(req.arg(0));
            school.requireManager(user, c);
            List<String> ids = problemIds(req.body(), "problemIds", user);
            if (ids.isEmpty()) throw ApiException.badRequest("Hãy nhập ít nhất một mã bài");
            school.addPracticeProblems(c, ids);
            platform.getCommunity().notifyMany(c.getStudentIds(), "Bài luyện tập mới của " + c.getName(),
                    "Giảng viên vừa thêm " + ids.size() + " bài luyện tập. Vào Lớp của tôi để làm.",
                    "Classrooms");
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "classrooms", "*", "problems", "*")) {
            Classroom c = managedClassroom(req);
            school.removePracticeProblem(c, req.arg(1));
            req.sendOk();
            return true;
        }
        return false;
    }

    /** true neu trong mot gio qua chua gui thong bao nao voi khoa nay (va ghi nhan lan nay). */
    private boolean firstNotice(String key) {
        if (platform.getLimiter().retryAfterSeconds(key, 60 * 60_000L, 1) > 0) return false;
        platform.getLimiter().hit(key, 60 * 60_000L);
        return true;
    }

    /** Lop trong duong dan, kem kiem tra nguoi goi co quyen quan ly lop do. */
    private Classroom managedClassroom(Request req) {
        User user = req.staff();
        Classroom c = school.findClassroom(req.arg(0));
        school.requireManager(user, c);
        return c;
    }

    /** Lop trong duong dan, kem kiem tra nguoi goi la giang vien phu trach (hoac quan tri vien). */
    private Classroom ownedClassroom(Request req) {
        User user = req.staff();
        Classroom c = school.findClassroom(req.arg(0));
        school.requireOwner(user, c);
        return c;
    }

    private Json.Obj classroomJson(Classroom c, User viewer, Map<String, User> users) {
        return Json.obj()
                .put("id", c.getId())
                .put("name", c.getName())
                .put("organizationId", c.getOrganizationId())
                .put("organizationName", school.organizationName(c.getOrganizationId()))
                .put("courseId", c.getCourseId())
                .put("courseTitle", platform.getCourses().courseTitle(c.getCourseId()))
                .put("teacherName", nameOf(users, c.getTeacherId()))
                .put("studentCount", c.getStudentIds().size())
                .put("practiceCount", c.getPracticeProblemIds().size())
                // So yeu cau vao lop dang cho duyet: chi nguoi quan ly lop thay.
                .put("requestCount", school.canManage(viewer, c) ? c.getPendingIds().size() : 0)
                .put("canManage", school.canManage(viewer, c))
                // isOwner: giang vien phu trach (hoac quan tri vien) - xoa lop, xoa hoc sinh.
                .put("isOwner", school.isOwner(viewer, c))
                // Ma tham gia chi nguoi quan ly lop thay, de dua cho hoc sinh.
                .put("joinCode", school.canManage(viewer, c) ? c.getJoinCode() : "");
    }

    /**
     * Bang xep hang cua lop: tinh tren bai luyen tap cua lop va bai trong cac bai tap giao cho lop (bai ky thi
     * da co bang xep hang rieng cua ky thi). Moi hoc sinh cua lop deu co mat, ke ca chua nop bai nao.
     * Cung dang voi /api/scoreboard de giao dien dung chung mot bang.
     */
    private String classScoreboardJson(Classroom c) {
        java.util.Set<String> problemIds = new java.util.LinkedHashSet<>();
        for (String id : c.getPracticeProblemIds()) {
            if (judge.findProblem(id).isPresent()) problemIds.add(id);
        }
        for (com.ptit.oj.model.Assignment a : platform.getAssignments().ofClassrooms(java.util.Collections.singletonList(c))) {
            for (String id : a.getProblemIds()) {
                if (judge.findProblem(id).isPresent()) problemIds.add(id);
            }
        }
        Map<String, User> users = usersById();
        List<User> members = new ArrayList<>();
        for (String id : c.getStudentIds()) {
            User u = users.get(id);
            if (u != null) members.add(u);
        }
        List<String> rows = new ArrayList<>();
        int rank = 1;
        for (com.ptit.oj.controller.ScoreboardService.ClassRow row : judge.buildClassScoreboard(members, problemIds)) {
            List<String> perProblem = new ArrayList<>();
            for (Map.Entry<String, Double> e : row.getBestPerProblem().entrySet()) {
                perProblem.add(Json.obj().put("problemId", e.getKey()).put("score", e.getValue()).build());
            }
            rows.add(Json.obj()
                    .put("rank", rank++)
                    .put("username", row.getUser().getUsername())
                    .put("fullName", row.getUser().getFullName())
                    .put("role", row.getUser().getRoleCode())
                    .put("roleName", row.getUser().getRole())
                    .put("total", row.getTotal())
                    .put("solved", row.getSolved())
                    .put("solvedCount", row.getAccepted())
                    .put("attempts", row.getAttempts())
                    .putRaw("perProblem", Json.array(perProblem))
                    .build());
        }
        return Json.obj()
                .put("classroomId", c.getId())
                .put("name", c.getName())
                .put("problemCount", problemIds.size())
                .putRaw("rows", Json.array(rows))
                .build();
    }

    private String classroomDetailJson(Classroom c, User viewer) {
        Map<String, User> users = usersById();
        Map<String, Set<String>> solvedByUser = platform.getAnalytics().sharedSolvedByUser();

        List<String> students = new ArrayList<>();
        for (String id : c.getStudentIds()) {
            User u = users.get(id);
            if (u == null) continue;
            Set<String> solved = solvedByUser.get(id);
            students.add(userJson(u).put("solved", solved == null ? 0 : solved.size()).build());
        }
        // Hoc sinh da duoc moi nhung chua dong y: chi nguoi quan ly lop thay.
        List<String> invited = new ArrayList<>();
        if (school.canManage(viewer, c)) {
            for (String id : c.getInvitedIds()) {
                User u = users.get(id);
                if (u != null) invited.add(userJson(u).build());
            }
        }
        // Hoc sinh dang xin vao lop, cho duyet: chi nguoi quan ly lop thay.
        List<String> requests = new ArrayList<>();
        if (school.canManage(viewer, c)) {
            for (String id : c.getPendingIds()) {
                User u = users.get(id);
                if (u != null) requests.add(userJson(u).put("detail", u instanceof com.ptit.oj.model.Student
                        ? ((com.ptit.oj.model.Student) u).getClassName() : "").build());
            }
        }
        // Bai luyen tap cua lop, kem viec nguoi dang xem da giai chua.
        Set<String> mySolved = platform.getAnalytics().solvedByUser().get(viewer.getId());
        List<String> practice = new ArrayList<>();
        for (String problemId : c.getPracticeProblemIds()) {
            Problem p = platform.getJudge().findProblem(problemId).orElse(null);
            if (p == null) continue;        // bai da bi xoa khoi dia
            practice.add(Json.obj().put("id", p.getId()).put("title", p.getTitle())
                    .put("difficulty", p.getDifficulty())
                    .put("solved", mySolved != null && mySolved.contains(p.getId())).build());
        }
        return classroomJson(c, viewer, users)
                .putRaw("practice", Json.array(practice))
                .putRaw("students", Json.array(students))
                .putRaw("invited", Json.array(invited))
                .putRaw("requests", Json.array(requests))
                .build();
    }

}
