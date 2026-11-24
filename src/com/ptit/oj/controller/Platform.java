package com.ptit.oj.controller;

import com.ptit.oj.model.Assignment;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Course;
import com.ptit.oj.model.Lesson;

import java.util.HashSet;
import java.util.Set;

/**
 * Noi tao va giu tat ca service cua cac module mo rong (lop hoc, khoa hoc, phong hoc,
 * cuoc thi, phan tich, cong dong...). ApiServer chi can giu mot doi tuong Platform.
 *
 * Du lieu cua cac service nay nam trong RAM (InMemoryRepository): tat may chu la mat.
 * Tai khoan va lich su nop bai van do JudgeService luu (MySQL hoac RAM) nhu cu.
 */
public class Platform {

    private final JudgeService judge;
    private final SchoolService school = new SchoolService();
    private final CourseService courses = new CourseService();
    private final AssignmentService assignments = new AssignmentService();
    private final CommunityService community = new CommunityService();
    private final MeetingService meetings = new MeetingService(community);
    private final BillingService billing = new BillingService();
    private final SystemService system = new SystemService();
    private final ContestService contests;
    private final AnalyticsService analytics;
    private final AssistantService assistant;
    private final PlaygroundService playground;

    public Platform(JudgeService judge) {
        this.judge = judge;
        this.contests = new ContestService(judge);
        this.analytics = new AnalyticsService(judge);
        this.assistant = new AssistantService(judge, analytics);
        this.playground = new PlaygroundService(judge);
    }

    /**
     * Ma cac bai da duoc dua vao mot ky thi, bai tap giao hoac bai hoc.
     * Bai kho rieng binh thuong chi chu bai thay; da nam trong danh sach nay thi moi nguoi mo duoc de lam.
     */
    public Set<String> publishedProblemIds() {
        Set<String> ids = new HashSet<>();
        // Ky thi CHUA bat dau thi chua cong bo bai: neu khong, thi sinh xem duoc de truoc gio thi.
        for (Contest c : contests.getContests()) {
            if (!"UPCOMING".equals(c.getStatus())) ids.addAll(c.getProblemIds());
        }
        for (Assignment a : assignments.getAll()) ids.addAll(a.getProblemIds());
        // Bai luyen tap giang vien them rieng cho lop cung coi la da cong bo de hoc sinh mo duoc.
        for (com.ptit.oj.model.Classroom c : school.getClassrooms()) ids.addAll(c.getPracticeProblemIds());
        for (Course c : courses.getCourses()) {
            for (Lesson l : c.getLessons()) ids.addAll(l.getProblemIds());
        }
        return ids;
    }

    public JudgeService getJudge() { return judge; }
    public SchoolService getSchool() { return school; }
    public CourseService getCourses() { return courses; }
    public AssignmentService getAssignments() { return assignments; }
    public CommunityService getCommunity() { return community; }
    public MeetingService getMeetings() { return meetings; }
    public BillingService getBilling() { return billing; }
    public SystemService getSystem() { return system; }
    public ContestService getContests() { return contests; }
    public AnalyticsService getAnalytics() { return analytics; }
    public AssistantService getAssistant() { return assistant; }
    public PlaygroundService getPlayground() { return playground; }
}
