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
 * Module 2 (to chuc), 3 (lop hoc), 35 (tro giang):
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
        if (req.is("DELETE", "classrooms", "*")) {
            User user = req.staff();
            Classroom c = school.findClassroom(req.arg(0));
            school.requireOwner(user, c);          // tro giang khong xoa duoc lop
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
        // Hoc sinh tu tham gia lop: doan * trong duong dan la MA THAM GIA, khong phai ma lop.
        if (req.is("POST", "classrooms", "*", "join")) {
            User user = req.user();
            Classroom c = school.join(req.arg(0), user);
            req.send(classroomJson(c, user, usersById()).build());
            return true;
        }
        return false;
    }

    /** Them / xoa hoc sinh va tro giang cua mot lop - chi nguoi quan ly lop. */
    private boolean handleMembers(Request req) throws IOException {
        if (req.is("POST", "classrooms", "*", "students")) {
            Classroom c = managedClassroom(req);
            User student = requireUsername(req.body().getString("username"));
            // Chi MOI: hoc sinh phai tu dong y moi vao lop (moi lai nguoi da moi / da trong lop thi khong bao nua).
            if (school.invite(c, student)) {
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
        if (req.is("POST", "classrooms", "*", "assistants")) {
            Classroom c = ownedClassroom(req);
            User assistant = requireUsername(req.body().getString("username"));
            boolean alreadyIn = c.getAssistantIds().contains(assistant.getId());
            school.addAssistant(c, assistant);
            if (!alreadyIn) {
                platform.getCommunity().notifyUser(assistant.getId(), "Bạn được thêm làm trợ giảng lớp " + c.getName(),
                        req.user().getFullName() + " đã thêm bạn làm trợ giảng của lớp " + c.getName()
                                + ". Lớp đã có trong Lớp của tôi.",
                        "Classrooms");
            }
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "classrooms", "*", "assistants", "*")) {
            Classroom c = ownedClassroom(req);
            school.removeAssistant(c, req.arg(1));
            req.sendOk();
            return true;
        }
        return false;
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
                .put("canManage", school.canManage(viewer, c))
                // isOwner: giang vien phu trach (hoac quan tri vien) - xoa lop, quan ly tro giang, xoa hoc sinh.
                .put("isOwner", school.isOwner(viewer, c))
                // Ma tham gia chi nguoi quan ly lop thay, de dua cho hoc sinh.
                .put("joinCode", school.canManage(viewer, c) ? c.getJoinCode() : "");
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
        List<String> assistants = new ArrayList<>();
        for (String id : c.getAssistantIds()) {
            User u = users.get(id);
            if (u != null) assistants.add(userJson(u).build());
        }
        // Hoc sinh da duoc moi nhung chua dong y: chi nguoi quan ly lop thay.
        List<String> invited = new ArrayList<>();
        if (school.canManage(viewer, c)) {
            for (String id : c.getInvitedIds()) {
                User u = users.get(id);
                if (u != null) invited.add(userJson(u).build());
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
                .putRaw("assistants", Json.array(assistants))
                .build();
    }

}
