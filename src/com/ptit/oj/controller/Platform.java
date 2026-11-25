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
    private final EvidenceStore evidence;
    private final ContestService contests;
    private final AnalyticsService analytics;
    private final AssistantService assistant;
    private final PlaygroundService playground;

    public Platform(JudgeService judge) {
        this.judge = judge;
        this.evidence = new EvidenceStore(judge.getDataDir().resolve("evidence"));
        this.contests = new ContestService(judge, school);
        this.analytics = new AnalyticsService(judge);
        this.assistant = new AssistantService(judge, analytics);
        this.playground = new PlaygroundService(judge);
    }

    /** Nguoi nay mo duoc nhung bai nao - xem quy tac o ProblemAccess. */
    public ProblemAccess accessFor(com.ptit.oj.model.User viewer) {
        return ProblemAccess.of(this, viewer);
    }

    public JudgeService getJudge() { return judge; }
    public SchoolService getSchool() { return school; }
    public CourseService getCourses() { return courses; }
    public AssignmentService getAssignments() { return assignments; }
    public CommunityService getCommunity() { return community; }
    public MeetingService getMeetings() { return meetings; }
    public BillingService getBilling() { return billing; }
    public SystemService getSystem() { return system; }
    public EvidenceStore getEvidence() { return evidence; }
    public ContestService getContests() { return contests; }
    public AnalyticsService getAnalytics() { return analytics; }
    public AssistantService getAssistant() { return assistant; }
    public PlaygroundService getPlayground() { return playground; }
}
