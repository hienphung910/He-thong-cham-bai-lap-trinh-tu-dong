package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.controller.ReportService;
import com.ptit.oj.controller.SchoolService;
import com.ptit.oj.controller.ContestService;
import com.ptit.oj.model.Assignment;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Cong cu cho giao vien quan ly lop (huong toi lop pho thong / gia su):
 *   GET  /api/classrooms/{lop}/students/{userId}/report?days=30   bao cao hoc tap mot hoc sinh (gui phu huynh)
 *   POST /api/classrooms/{lop}/students/bulk                      tao tai khoan ca lop tu danh sach ho ten
 *   POST /api/classrooms/{lop}/students/{userId}/password         dat lai mat khau tai khoan do lop tao
 *   GET  /api/classrooms/{lop}/gradebook                          bang diem he 10 (xuat Excel o giao dien)
 * Chi giang vien phu trach lop va quan tri vien.
 */
public class TeachingApi extends ApiModule {

    /** Toi da so tai khoan tao trong mot lan: moi tai khoan bam mat khau mat mot luc (PBKDF2). */
    private static final int MAX_BULK = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Bo cac chu de nham (l/1, o/0, i) vi hoc sinh doc mat khau tu the giay. */
    private static final String PASSWORD_LETTERS = "abcdefghjkmnpqrstuvwxyz";
    private static final String PASSWORD_DIGITS = "23456789";

    private final SchoolService school;
    private final ReportService reports;

    public TeachingApi(Platform platform) {
        super(platform);
        this.school = platform.getSchool();
        this.reports = new ReportService(platform);
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "classrooms", "*", "students", "*", "report")) {
            Classroom c = managed(req);
            User student = memberStudent(c, req.arg(1));
            int days = req.queryInt("days", 30);
            if (days < 1 || days > 365) throw ApiException.badRequest("Khoảng thời gian phải từ 1 đến 365 ngày");
            req.send(reportJson(reports.build(c, student, days)));
            return true;
        }
        if (req.is("POST", "classrooms", "*", "students", "bulk")) {
            Classroom c = managed(req);
            Json.Value body = req.body();
            List<String> names = parseNames(body.getString("names", ""));
            if (names.isEmpty()) throw ApiException.badRequest("Hãy dán danh sách họ tên học sinh, mỗi dòng một em");
            if (names.size() > MAX_BULK) {
                throw ApiException.badRequest("Mỗi lần tạo tối đa " + MAX_BULK + " tài khoản (đang có " + names.size() + " dòng)");
            }
            String className = body.getString("className", "").trim();
            // Kiem tra HET truoc khi tao: mot ten sai o giua danh sach thi khong tao do dang nua lop.
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                String error = judge.getAuth().checkStudentName(names.get(i));
                if (error != null) problems.add("\"" + names.get(i) + "\": " + error);
            }
            if (!problems.isEmpty()) {
                throw ApiException.badRequest("Chưa tạo tài khoản nào. Hãy sửa các dòng sau rồi thử lại: " + String.join("; ", problems));
            }
            List<String> created = new ArrayList<>();
            for (String name : names) {
                String password = newPassword();
                User u = judge.getAuth().createManagedStudent(name, className, password);
                school.addStudent(c, u);
                c.getManagedIds().add(u.getId());
                created.add(Json.obj().put("userId", u.getId()).put("fullName", u.getFullName())
                        .put("username", u.getUsername()).put("password", password).build());
            }
            platform.getSystem().log(req.user().getUsername(), "Tạo tài khoản học sinh",
                    created.size() + " tài khoản cho lớp " + c.getName(), true);
            req.send(Json.obj().put("classroomName", c.getName()).putRaw("created", Json.array(created)).build());
            return true;
        }
        if (req.is("POST", "classrooms", "*", "students", "*", "password")) {
            Classroom c = managed(req);
            User student = memberStudent(c, req.arg(1));
            if (!c.getManagedIds().contains(student.getId())) {
                throw ApiException.forbidden("Chỉ đặt lại được mật khẩu cho tài khoản do lớp tạo. Học sinh tự đăng ký thì dùng Quên mật khẩu");
            }
            String password = newPassword();
            judge.getAuth().setPasswordFor(student.getId(), password);
            platform.getSystem().log(req.user().getUsername(), "Đặt lại mật khẩu học sinh", student.getUsername(), true);
            req.send(Json.obj().put("fullName", student.getFullName()).put("username", student.getUsername())
                    .put("password", password).build());
            return true;
        }
        if (req.is("GET", "classrooms", "*", "gradebook")) {
            Classroom c = managed(req);
            req.send(gradebookJson(c));
            return true;
        }
        return false;
    }

    private Classroom managed(Request req) {
        User user = req.staff();
        Classroom c = school.findClassroom(req.arg(0));
        school.requireManager(user, c);
        return c;
    }

    private User memberStudent(Classroom c, String userId) {
        if (!c.getStudentIds().contains(userId)) throw ApiException.notFound("Học sinh này không có trong lớp " + c.getName());
        User u = usersById().get(userId);
        if (u == null) throw ApiException.notFound("Không có tài khoản này");
        return u;
    }

    /**
     * Danh sach dan tu Excel / Word: moi dong mot em. Dong co nhieu cot (STT, ho ten, lop...) thi lay o dau
     * tien co chu cai va khong phai so thu tu; bo so thu tu dau dong ("1.", "2)").
     */
    static List<String> parseNames(String text) {
        List<String> names = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String picked = "";
            for (String cell : line.split("[\\t;,|]")) {
                String c = cell.trim().replaceFirst("^\\d+\\s*[.)\\-:]?\\s*", "").trim();
                if (c.codePoints().anyMatch(Character::isLetter)) { picked = c; break; }
            }
            if (!picked.isEmpty() && !isHeader(picked)) names.add(picked);
        }
        return names;
    }

    /** Dong tieu de cua bang Excel ("STT", "Họ và tên", "Họ tên học sinh"...). */
    private static boolean isHeader(String cell) {
        String folded = java.text.Normalizer.normalize(cell, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(java.util.Locale.ROOT).trim();
        return folded.matches("stt|so thu tu|ho va ten.*|ho ten.*|ten hoc sinh|ten|lop|hoc sinh|danh sach.*");
    }

    /** Mat khau tam de doc: 4 chu + 4 so, khong co chu de nham ("kmta4827"). */
    private static String newPassword() {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 4; i++) p.append(PASSWORD_LETTERS.charAt(RANDOM.nextInt(PASSWORD_LETTERS.length())));
        for (int i = 0; i < 4; i++) p.append(PASSWORD_DIGITS.charAt(RANDOM.nextInt(PASSWORD_DIGITS.length())));
        return p.toString();
    }

    // ------------------------------------------------------------ bao cao

    private String reportJson(ReportService.Report r) {
        List<String> assignments = new ArrayList<>();
        for (ReportService.AssignmentLine a : r.assignments) {
            assignments.add(Json.obj().put("title", a.assignment.getTitle()).put("dueAt", time(a.assignment.getDueAt()))
                    .put("done", a.done).put("late", a.late).put("total", a.total).put("state", a.state).build());
        }
        List<String> contests = new ArrayList<>();
        for (ReportService.ContestLine k : r.contests) {
            contests.add(Json.obj().put("title", k.contest.getTitle()).put("startAt", time(k.contest.getStartAt()))
                    .put("rank", k.rank).put("participants", k.participants).put("solved", k.solved)
                    .put("problemCount", k.contest.getProblemIds().size()).build());
        }
        User teacher = usersById().get(r.classroom.getTeacherId());
        return Json.obj()
                .put("fullName", r.student.getFullName())
                .put("username", r.student.getUsername())
                .put("classroomName", r.classroom.getName())
                .put("teacherName", teacher == null ? "" : teacher.getFullName())
                .put("days", r.days)
                .put("from", r.from.toString())
                .put("to", r.to.toString())
                .put("submissions", r.submissions)
                .put("accepted", r.accepted)
                .put("acRate", r.acRate())
                .put("activeDays", r.activeDays)
                .put("newSolved", r.newSolved)
                .put("prevNewSolved", r.prevNewSolved)
                .put("totalSolved", r.totalSolved)
                .put("streak", r.streak)
                .put("classRank", r.classRank)
                .put("classSize", r.classSize)
                .put("classSolved", r.classSolved)
                .put("classAverageSolved", Math.round(r.classAverageSolved * 10) / 10.0)
                .put("classProblemCount", r.classProblemCount)
                .putRaw("assignments", Json.array(assignments))
                .putRaw("contests", Json.array(contests))
                .putRaw("strongTopics", stringArray(r.strongTopics))
                .putRaw("weakTopics", stringArray(r.weakTopics))
                .put("comment", r.comment)
                .build();
    }

    // ------------------------------------------------------------ bang diem

    /**
     * Bang diem he 10 cua lop: moi bai tap giao mot cot (bai xong dung han tinh tron, nop muon tinh mot nua),
     * moi ky thi da ket thuc mot cot (so bai giai duoc / so bai), cot cuoi la trung binh cac cot.
     */
    private String gradebookJson(Classroom c) {
        Map<String, User> users = usersById();
        List<Assignment> assignments = new ArrayList<>(platform.getAssignments().ofClassrooms(Collections.singletonList(c)));
        assignments.sort(Comparator.comparing(Assignment::getCreatedAt));
        List<Contest> contests = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Contest k : platform.getContests().getContests()) {
            if (c.getId().equals(k.getClassroomId()) && k.getEndAt().isBefore(now)) contests.add(k);
        }
        contests.sort(Comparator.comparing(Contest::getStartAt));

        Map<String, Map<String, List<LocalDateTime>>> accepted = platform.getAnalytics().acceptedTimesByUser();
        List<Map<String, Integer>> contestSolved = new ArrayList<>();
        for (Contest k : contests) {
            Map<String, Integer> solved = new java.util.HashMap<>();
            for (ContestService.Row row : platform.getContests().standings(k, k.getDurationMin())) {
                solved.put(row.getUser().getId(), row.getSolved());
            }
            contestSolved.add(solved);
        }

        List<String> columns = new ArrayList<>();
        for (Assignment a : assignments) {
            columns.add(Json.obj().put("kind", "assignment").put("title", a.getTitle()).put("date", time(a.getDueAt())).build());
        }
        for (Contest k : contests) {
            columns.add(Json.obj().put("kind", "contest").put("title", k.getTitle()).put("date", time(k.getStartAt())).build());
        }

        List<User> students = new ArrayList<>();
        for (String id : c.getStudentIds()) {
            User u = users.get(id);
            if (u != null) students.add(u);
        }
        // Thu tu so diem: theo ten (chu cuoi) roi ho dem, nhu danh sach lop o truong.
        students.sort(Comparator.comparing((User u) -> givenName(u.getFullName())).thenComparing(User::getFullName));

        List<String> rows = new ArrayList<>();
        for (User u : students) {
            Map<String, List<LocalDateTime>> mine = accepted.getOrDefault(u.getId(), Collections.<String, List<LocalDateTime>>emptyMap());
            List<String> scores = new ArrayList<>();
            double sum = 0;
            int count = 0;
            for (Assignment a : assignments) {
                double points = 0;
                for (String pid : a.getProblemIds()) {
                    String st = a.statusOf(mine.get(pid));
                    if ("DONE".equals(st)) points += 1;
                    else if ("LATE".equals(st)) points += 0.5;
                }
                double score = a.getProblemIds().isEmpty() ? 0 : round1(points * 10 / a.getProblemIds().size());
                // Bai tap chua toi han va chua lam xong: chua cham, de trong.
                boolean pending = !a.isOverdue() && points < a.getProblemIds().size();
                scores.add(pending ? "null" : String.valueOf(score));
                if (!pending) { sum += score; count++; }
            }
            for (int i = 0; i < contests.size(); i++) {
                Contest k = contests.get(i);
                int solved = contestSolved.get(i).getOrDefault(u.getId(), 0);
                double score = k.getProblemIds().isEmpty() ? 0 : round1(solved * 10.0 / k.getProblemIds().size());
                scores.add(String.valueOf(score));
                sum += score;
                count++;
            }
            rows.add(Json.obj().put("fullName", u.getFullName()).put("username", u.getUsername())
                    .putRaw("scores", "[" + String.join(",", scores) + "]")
                    .putRaw("average", count == 0 ? "null" : String.valueOf(round1(sum / count))).build());
        }
        return Json.obj().put("classroomName", c.getName())
                .putRaw("columns", Json.array(columns)).putRaw("rows", Json.array(rows)).build();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String givenName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        return java.text.Normalizer.normalize(parts[parts.length - 1], java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
