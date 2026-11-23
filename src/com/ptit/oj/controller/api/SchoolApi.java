package com.ptit.oj.controller.api;

import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.controller.SchoolService;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Organization;
import com.ptit.oj.model.ParentContact;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 2 (to chuc), 3 (lop hoc), 32 (phu huynh), 35 (tro giang):
 *   /api/organizations, /api/classrooms, /api/parents
 */
public class SchoolApi extends ApiModule {

    private final SchoolService school;

    public SchoolApi(Platform platform) {
        super(platform);
        this.school = platform.getSchool();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        return handleOrganizations(req) || handleClassrooms(req) || handleMembers(req) || handleParents(req);
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
            school.requireManager(user, c);
            school.deleteClassroom(c.getId());
            // Xoa luon bai tap giao, buoi hoc va ban ghi cua lop: neu de lai thi khong ai xoa duoc
            // nua (moi thao tac deu can tim thay lop) va bai kho rieng trong do van bi coi la da cong bo.
            platform.getAssignments().deleteByClassroom(c.getId());
            platform.getMeetings().deleteByClassroom(c.getId());
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
            school.addStudent(c, requireUsername(req.body().getString("username")));
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "classrooms", "*", "students", "*")) {
            Classroom c = managedClassroom(req);
            school.removeStudent(c, req.arg(1));
            req.sendOk();
            return true;
        }
        if (req.is("POST", "classrooms", "*", "assistants")) {
            Classroom c = managedClassroom(req);
            school.addAssistant(c, requireUsername(req.body().getString("username")));
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "classrooms", "*", "assistants", "*")) {
            Classroom c = managedClassroom(req);
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
                .put("canManage", school.canManage(viewer, c))
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
        return classroomJson(c, viewer, users)
                .putRaw("students", Json.array(students))
                .putRaw("assistants", Json.array(assistants))
                .build();
    }

    // ----------------------------------------------------------- phu huynh

    private boolean handleParents(Request req) throws IOException {
        if (req.is("GET", "parents")) {
            req.staff();
            Map<String, User> users = usersById();
            List<String> items = new ArrayList<>();
            for (ParentContact p : school.getParents()) {
                User student = users.get(p.getStudentId());
                items.add(Json.obj().put("id", p.getId()).put("fullName", p.getFullName())
                        .put("phone", p.getPhone()).put("email", p.getEmail())
                        .put("studentName", nameOf(users, p.getStudentId()))
                        .put("studentUsername", student == null ? "" : student.getUsername())
                        .build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "parents")) {
            req.staff();
            Json.Value body = req.body();
            User student = requireUsername(body.getString("studentUsername"));
            school.addParent(student, body.getString("fullName"), body.getString("phone"), body.getString("email"));
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "parents", "*")) {
            req.staff();
            school.deleteParent(req.arg(0));
            req.sendOk();
            return true;
        }
        // Bao cao hoc tap cua con gui cho phu huynh (noi dung do AssistantService soan).
        if (req.is("GET", "parents", "*", "report")) {
            req.staff();
            ParentContact parent = school.findParent(req.arg(0));
            User student = usersById().get(parent.getStudentId());
            List<String> lines = new ArrayList<>();
            if (student != null) lines = platform.getAssistant().studentComment(student);
            req.send(Json.obj().put("parentName", parent.getFullName())
                    .put("studentName", student == null ? "(đã xoá)" : student.getFullName())
                    .putRaw("lines", stringArray(lines)).build());
            return true;
        }
        return false;
    }
}
