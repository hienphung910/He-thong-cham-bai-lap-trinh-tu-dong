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
 * Du lieu cua cac service nay song trong RAM (InMemoryRepository). Khi chay voi CSDL that, StateStore
 * chup chung xuong data/state vai giay mot lan va nap lai luc khoi dong, nen khoi dong lai khong mat.
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
    private final SystemService system;
    private final RateLimiter limiter = new RateLimiter();
    /** Gui email qua Resend; tat neu chua cau hinh (xem ResendMailer). */
    private final ResendMailer mailer = new ResendMailer();
    /** Ma dat lai mat khau dang cho (quen mat khau). */
    private final PasswordResetService resets = new PasswordResetService();
    /** Email khoi phuc dang cho nguoi dung xac minh. */
    private final EmailVerificationService verifications = new EmailVerificationService();
    private final AccountMailer accountMailer = new AccountMailer(mailer, verifications, limiter);
    /** Noi luu du lieu cac module mo rong xuong o dia; null neu khong bat (che do --no-db). */
    private volatile StateStore stateStore;
    private final SubmissionGate gate = new SubmissionGate();
    private final EvidenceStore evidence;
    private final ContestService contests;
    private final AnalyticsService analytics;
    private final AssistantService assistant;
    private final PlaygroundService playground;

    public Platform(JudgeService judge) {
        this.judge = judge;
        this.evidence = new EvidenceStore(judge.getDataDir().resolve("evidence"));
        this.system = new SystemService(judge.getDataDir().resolve("audit").resolve("audit.log"));
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
    public StateStore getStateStore() { return stateStore; }
    public void setStateStore(StateStore stateStore) { this.stateStore = stateStore; }

    /** Bao rang du lieu vua thay doi, de StateStore ghi xuong o dia (khong lam gi neu khong bat luu). */
    public void markChanged() {
        StateStore store = stateStore;
        if (store != null) store.markDirty();
    }
    /** Dem tan suat (dang nhap sai, dang ky, chat...) dung chung cho moi module. */
    public RateLimiter getLimiter() { return limiter; }
    public ResendMailer getMailer() { return mailer; }
    public PasswordResetService getResets() { return resets; }
    public EmailVerificationService getVerifications() { return verifications; }
    public AccountMailer getAccountMailer() { return accountMailer; }

    /** Tai khoan vua doi mat khau / email, bi khoa hoac bi xoa: moi lien ket gui qua thu truoc do mat hieu luc. */
    public void revokeMailLinks(String userId) {
        resets.revokeUser(userId);
        verifications.revokeUser(userId);
    }
    /** Cong vao cua bo cham: moi nguoi mot bai mot luc, ca he thong co tran so bai dang cho. */
    public SubmissionGate getGate() { return gate; }
    public ContestService getContests() { return contests; }
    public AnalyticsService getAnalytics() { return analytics; }
    public AssistantService getAssistant() { return assistant; }
    public PlaygroundService getPlayground() { return playground; }
}
