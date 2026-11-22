package com.ptit.oj.controller;

import com.ptit.oj.controller.judge.Language;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Course;
import com.ptit.oj.model.Document;
import com.ptit.oj.model.Organization;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Du lieu mau cho cac module mo rong, tao lai moi lan khoi dong (vi chung nam trong RAM).
 * Nho vay mo web len la co san mot lop hoc, khoa hoc, bai tap giao, buoi hoc, cuoc thi...
 * de trinh dien, khong phai nhap tay tu dau.
 */
public class DemoData {

    private final Platform platform;
    private final JudgeService judge;

    public DemoData(Platform platform) {
        this.platform = platform;
        this.judge = platform.getJudge();
    }

    /**
     * @param sampleSubmissions true (che do --no-db): tao them vai chuc bai nop mau.
     *                          O che do MySQL thi KHONG tao, de khong lam ban lich su nop that.
     */
    public void seed(boolean sampleSubmissions) {
        User teacher = firstUserWithRole(User.ROLE_TEACHER);
        if (teacher == null) teacher = firstUserWithRole(User.ROLE_ADMIN);
        List<User> students = new ArrayList<>();
        for (User u : judge.getUsers()) {
            if (User.ROLE_STUDENT.equals(u.getRoleCode()) && students.size() < 5) students.add(u);
        }
        List<Problem> path = platform.getAnalytics().problemsInPathOrder();
        if (teacher == null || path.size() < 8) return;      // thieu du lieu goc thi khong tao mau

        // --- to chuc, khoa hoc, tai lieu ---
        Organization org = platform.getSchool().createOrganization("Trung tâm CodeHub PTIT",
                "Km10 Nguyễn Trãi, Hà Đông, Hà Nội", "Trung tâm đào tạo lập trình thi đấu");
        CourseService courses = platform.getCourses();
        Course course = courses.createCourse("Nhập môn lập trình",
                "Khóa học cho người mới bắt đầu: nhập xuất, rẽ nhánh, vòng lặp, mảng.", "Cơ bản");
        courses.addLesson(course, "Bài 1 - Nhập xuất và biến",
                "Đọc dữ liệu từ bàn phím, in kết quả ra màn hình, các kiểu dữ liệu cơ bản.", ids(path, 0, 2));
        courses.addLesson(course, "Bài 2 - Câu lệnh rẽ nhánh",
                "Dùng if / else để chương trình chọn việc cần làm theo điều kiện.", ids(path, 2, 4));
        courses.addLesson(course, "Bài 3 - Vòng lặp",
                "Lặp lại một khối lệnh nhiều lần bằng for và while.", ids(path, 4, 6));
        courses.createCourse("Lộ trình thi đấu VNOI",
                "26 chặng theo lộ trình tổng quát của VNOI Wiki, từ đệ quy đến quy hoạch động.", "Nâng cao");
        courses.addDocument(course.getId(), "Mẹo đọc đề và tự kiểm tra", Document.KIND_TEXT,
                "1. Đọc kỹ giới hạn của đề.\n2. Tự tính tay ví dụ.\n3. Thử các trường hợp biên trước khi nộp.",
                teacher.getFullName());
        courses.addDocument(course.getId(), "VNOI Wiki - Lộ trình tổng quát", Document.KIND_LINK,
                "https://wiki.vnoi.info/roadmap/general-roadmap", teacher.getFullName());

        // --- lop hoc, phu huynh, bai tap giao, buoi hoc ---
        SchoolService school = platform.getSchool();
        Classroom classroom = school.createClassroom("Lớp Nhập môn K24", org.getId(), course.getId(), teacher);
        for (User s : students) school.addStudent(classroom, s);
        if (!students.isEmpty()) {
            school.addParent(students.get(0), "Phụ huynh của " + students.get(0).getFullName(),
                    "0900000001", "phuhuynh@example.com");
        }
        platform.getAssignments().create(classroom, "Bài tập tuần 1",
                "Hoàn thành các bài đầu tiên của lộ trình.", ids(path, 0, 3),
                LocalDateTime.now().plusDays(7), teacher);
        platform.getMeetings().schedule(classroom, "Buổi 1 - Làm quen với hệ thống",
                LocalDateTime.now().withSecond(0).withNano(0), 90, teacher, true);

        // --- cuoc thi: mot cuoc da xong (de xem lai) va mot cuoc dang dien ra ---
        LocalDateTime lastNight = LocalDate.now().minusDays(1).atTime(19, 0);
        platform.getContests().create("Kỳ thi thử số 1", "Kỳ thi đã kết thúc, dùng để xem lại diễn biến.",
                lastNight, 120, ids(path, 0, 4));
        platform.getContests().create("Kỳ thi luyện tập tuần này", "Mở trong 7 ngày; nộp bài trong khung giờ này sẽ được tính điểm.",
                LocalDateTime.now().minusMinutes(30).withSecond(0).withNano(0), 7 * 24 * 60, ids(path, 4, 8));

        // --- cong dong ---
        CommunityService community = platform.getCommunity();
        community.postMessage("general", teacher, "Chào mừng các em đến với CodeHub!");
        community.createPost(teacher, "Nội quy diễn đàn",
                "Hỏi đáp về bài tập, không đăng lời giải hoàn chỉnh của bài đang trong kỳ thi.", "");
        for (User u : judge.getUsers()) {
            community.notifyUser(u.getId(), "Chào mừng đến với CodeHub",
                    "Xem lớp học, bài tập được giao và kỳ thi ở thanh bên trái.");
        }

        if (sampleSubmissions && students.size() >= 3) {
            seedSubmissions(students, path);
            System.out.println("Đã tạo dữ liệu mẫu kèm " + SAMPLE_PLAN.length
                    + " bài nộp mẫu (chỉ ở chế độ --no-db).");
        } else {
            System.out.println("Đã tạo dữ liệu mẫu cho lớp học, khóa học, cuộc thi (lưu trong RAM).");
        }
    }

    /**
     * Ke hoach bai nop mau, da xep theo thoi gian tang dan. Moi dong:
     * { hoc sinh thu may, bai thu may trong lo trinh, 1 = AC / 0 = WA, cach day may ngay, gio, phut }.
     * Cac dong "1 ngay truoc, 19h-20h" roi dung vao khung gio cua "Kỳ thi thử số 1".
     */
    private static final int[][] SAMPLE_PLAN = {
        {0, 0, 1, 4, 20, 5},  {0, 1, 0, 4, 20, 30}, {0, 1, 1, 4, 20, 41},
        {1, 0, 0, 3, 9, 10},  {1, 0, 1, 3, 9, 25},  {0, 4, 1, 3, 21, 0},
        {2, 0, 0, 2, 14, 0},  {2, 0, 0, 2, 14, 12}, {0, 5, 1, 2, 22, 15},
        {0, 0, 1, 1, 19, 6},  {1, 0, 1, 1, 19, 11}, {2, 0, 0, 1, 19, 14},
        {0, 1, 1, 1, 19, 20}, {2, 0, 1, 1, 19, 31}, {1, 1, 0, 1, 19, 35},
        {0, 2, 0, 1, 19, 44}, {1, 1, 1, 1, 19, 52}, {0, 2, 1, 1, 20, 3},
        {2, 1, 0, 1, 20, 10}, {1, 2, 0, 1, 20, 21}, {0, 3, 1, 1, 20, 40},
        {2, 1, 0, 1, 20, 48}, {0, 6, 1, 0, 0, 5},   {2, 2, 0, 0, 0, 10},
    };

    private void seedSubmissions(List<User> students, List<Problem> path) {
        List<Language> languages = judge.getLanguages();
        for (int[] row : SAMPLE_PLAN) {
            LocalDateTime at = LocalDate.now().minusDays(row[3]).atTime(row[4], row[5]);
            // Hoc sinh dau tien nop xen ke 2 ngon ngu, cac ban khac dung ngon ngu dau tien.
            Language language = row[0] == 0 ? languages.get(row[1] % 2) : languages.get(0);
            judge.addSampleSubmission(path.get(row[1]), students.get(row[0]), language, row[2] == 1, at);
        }
    }

    private User firstUserWithRole(String role) {
        for (User u : judge.getUsers()) {
            if (role.equals(u.getRoleCode())) return u;
        }
        return null;
    }

    /** Ma cua cac bai tu vi tri from den truoc vi tri to trong lo trinh. */
    private List<String> ids(List<Problem> path, int from, int to) {
        List<String> result = new ArrayList<>();
        for (Problem p : path.subList(from, to)) result.add(p.getId());
        return result;
    }
}
