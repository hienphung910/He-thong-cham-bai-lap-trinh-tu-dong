package com.ptit.oj.controller;

import com.ptit.oj.controller.judge.Judge;
import com.ptit.oj.controller.judge.Language;
import com.ptit.oj.controller.judge.LanguageRegistry;
import com.ptit.oj.dao.CachedSubmissionRepository;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.InMemorySubmissionRepository;
import com.ptit.oj.dao.InMemorySessionStore;
import com.ptit.oj.dao.InMemoryUserRepository;
import com.ptit.oj.dao.MySqlSessionStore;
import com.ptit.oj.dao.SessionStore;
import com.ptit.oj.dao.MySqlDatabaseManager;
import com.ptit.oj.dao.MySqlSchemaInitializer;
import com.ptit.oj.dao.MySqlSubmissionRepository;
import com.ptit.oj.dao.MySqlUserRepository;
import com.ptit.oj.dao.ProblemLoader;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.dao.SubmissionRepository;
import com.ptit.oj.dao.TextUtils;
import com.ptit.oj.dao.TopicLoader;
import com.ptit.oj.dao.UserRepository;
import com.ptit.oj.model.JudgeResult;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.ProblemDraft;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.TestCase;
import com.ptit.oj.model.TestCaseResult;
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.User;
import com.ptit.oj.model.Verdict;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Diem vao nghiep vu dung chung cho ca 4 module: giu du lieu (bai tap, bai nop,
 * nguoi dung) va dieu phoi bo cham. ApiServer chi goi lop nay, khong goi thang
 * xuong cac kho du lieu.
 *
 * Cac kho du lieu duoc TIEM VAO qua constructor chu khong tao san ben trong, nen
 * doi giua MySQL va bo nho (--no-db) chi la chon ham mysql() hay inMemory().
 */
public class JudgeService {

    private final Path dataDir;
    private final Repository<Problem> problems;
    private final UserRepository users;
    private final SubmissionRepository submissions;
    private final LanguageRegistry languages;
    private final Judge judge = new Judge();

    private final AuthService auth;
    private final ProblemAdminService problemAdmin;
    private final ScoreboardService scoreboard;
    /** Lo trinh hoc, nap mot lan trong loadData(); CopyOnWrite vi may chu web doc tu nhieu luong. */
    private final List<Topic> topics = new CopyOnWriteArrayList<>();

    public JudgeService(Path dataDir, Repository<Problem> problems, UserRepository users,
                        SubmissionRepository submissions, LanguageRegistry languages, SessionStore sessionStore) {
        this.dataDir = dataDir;
        this.problems = problems;
        this.users = users;
        this.submissions = submissions;
        this.languages = languages;
        this.auth = new AuthService(users, new PasswordService(), new SessionService(sessionStore));
        this.problemAdmin = new ProblemAdminService(problems, dataDir.resolve("problems"),
                id -> findTopic(id).isPresent());
        this.scoreboard = new ScoreboardService(submissions, problems);
    }

    /**
     * Che do that: tai khoan va lich su nop bai nam trong MySQL, con nguyen sau
     * khi tat chuong trinh. De bai va test van doc tu data/problems/.
     * Bang va view duoc tao neu chua co (goi lai nhieu lan khong mat du lieu cu).
     */
    public static JudgeService mysql(Path dataDir, MySqlDatabaseManager database) {
        new MySqlSchemaInitializer(database).initialize();

        Repository<Problem> problems = new InMemoryRepository<>();   // de bai van doc tu o dia
        LanguageRegistry languages = new LanguageRegistry();
        MySqlUserRepository users = new MySqlUserRepository(database);
        // Bo nho dem truoc MySQL: bang xep hang / thong ke khong phai doc lai ca bang moi lan (xem lop do).
        CachedSubmissionRepository submissions = new CachedSubmissionRepository(
                new MySqlSubmissionRepository(database, problems, users, languages));
        // Phien dang nhap cung nam trong MySQL: deploy lai may chu khong dang xuat ai.
        JudgeService service = new JudgeService(dataDir, problems, users, submissions, languages, new MySqlSessionStore(database));
        service.database = database;
        service.auth.onUserDeleted(submissions::evictUser);
        service.auth.onUserChanged(submissions::refreshUser);
        return service;
    }

    /** Che do --no-db: day du tinh nang nhung moi thu nam trong RAM, tat may chu la mat. */
    public static JudgeService inMemory(Path dataDir) {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemorySubmissionRepository submissions = new InMemorySubmissionRepository();
        users.setSubmissionRepository(submissions);
        return new JudgeService(dataDir, new InMemoryRepository<>(), users, submissions, new LanguageRegistry(),
                new InMemorySessionStore());
    }

    /** Nap lo trinh hoc va de bai tu o dia. */
    public void loadData() {
        topics.clear();
        topics.addAll(new TopicLoader(dataDir.resolve("topics.txt")).loadAll());
        for (Problem p : new ProblemLoader(dataDir.resolve("problems")).loadAll()) {
            problems.save(p);
            if (!p.getTopic().isEmpty() && !findTopic(p.getTopic()).isPresent()) {
                System.out.println("Cảnh báo: bài " + p.getId() + " gắn chủ đề \"" + p.getTopic()
                        + "\" không có trong data/topics.txt - sẽ hiện ở nhóm \"Bài khác\".");
            }
        }
    }

    // ------------------------------------------------------- tai khoan (Module 1)

    public AuthService getAuth() { return auth; }

    /** Ket noi MySQL khi chay voi CSDL; null o che do --no-db. */
    private MySqlDatabaseManager database;

    public MySqlDatabaseManager getDatabase() { return database; }

    /** Thu muc du lieu (data/): de bai, chu de, va cac file may chu tu luu nhu minh chung dang ky. */
    public Path getDataDir() { return dataDir; }

    public List<User> getUsers() { return users.findAll(); }

    public Optional<User> findUserByUsername(String username) {
        return username == null ? Optional.empty() : users.findByUsername(username.trim());
    }

    // ------------------------------------------- bai tap va lo trinh hoc (Module 2)

    public List<Problem> getProblems() { return problems.findAll(); }

    /** Chi cac bai cua kho chung - lo trinh hoc, thong ke va goi y bai khong tinh bai kho rieng. */
    public List<Problem> getSharedProblems() {
        List<Problem> shared = new ArrayList<>();
        for (Problem p : problems.findAll()) {
            if (p.isShared()) shared.add(p);
        }
        return shared;
    }

    /** Cac chu de theo dung thu tu hoc. */
    public List<Topic> getTopics() { return Collections.unmodifiableList(topics); }

    public Optional<Topic> findTopic(String id) {
        for (Topic t : topics) {
            if (t.getId().equals(id)) return Optional.of(t);
        }
        return Optional.empty();
    }

    public Optional<Problem> findProblem(String id) {
        return id == null ? Optional.empty() : problems.findById(id.trim().toUpperCase());
    }

    /** Tao bai tap moi: quan tri vien tao vao kho chung, giang vien tao vao kho rieng (kiem tra trong ProblemAdminService). */
    public Problem createProblem(User actor, ProblemDraft draft) {
        return problemAdmin.createProblem(actor, draft);
    }

    /** Chuyen chu bai kho rieng tu ten dang nhap (ban cu) sang ma tai khoan; goi mot lan luc khoi dong. */
    public int migrateProblemOwners() {
        return problemAdmin.migrateOwners(users);
    }

    /** Sua ten, de bai, gioi han cua bai da co - quan tri vien, hoac giang vien voi bai cua minh. */
    public Problem updateProblem(User actor, String problemId, String title, String statement,
                                 long timeLimitMs, int memoryLimitMb) {
        return problemAdmin.updateProblem(actor, problemId, title, statement, timeLimitMs, memoryLimitMb);
    }

    /**
     * Xoa bai. Bai da co nguoi nop thi KHONG xoa: lich su nop bai va bang xep hang dang tro toi no.
     */
    public Problem deleteProblem(User actor, String problemId) {
        Problem problem = problemAdmin.findForEdit(actor, problemId);
        for (Submission s : submissions.findAllNewestFirst()) {
            if (s.getProblem().getId().equals(problem.getId())) {
                throw new ProblemAdminService.ProblemAdminException(409,
                        "Bài " + problem.getId() + " đã có bài nộp nên không xoá được");
            }
        }
        return problemAdmin.deleteProblem(actor, problemId);
    }

    /** Doi chu de va do kho cua bai da co - quan tri vien, hoac giang vien voi bai cua minh. */
    public Problem updateProblemMeta(User actor, String problemId, String topic, int difficulty) {
        return problemAdmin.updateProblemMeta(actor, problemId, topic, difficulty);
    }

    /** Them mot test vao bai da co - quan tri vien, hoac giang vien voi bai cua minh (Module 7). */
    public Problem addTest(User actor, String problemId, ProblemDraft.TestDraft test) {
        return problemAdmin.addTest(actor, problemId, test);
    }

    /** Xoa mot test cua bai da co - quan tri vien, hoac giang vien voi bai cua minh (Module 7). */
    public Problem deleteTest(User actor, String problemId, String testName) {
        return problemAdmin.deleteTest(actor, problemId, testName);
    }

    /** Lay bai de sua / xem test an; nem 403 neu actor khong co quyen sua bai nay. */
    public Problem findProblemForEdit(User actor, String problemId) {
        return problemAdmin.findForEdit(actor, problemId);
    }

    // --------------------------------------------- nop bai va cham tu dong (Module 3)

    public List<Language> getLanguages() { return languages.all(); }

    public Optional<Language> findLanguage(String name) { return languages.byName(name); }

    /**
     * Nop bai: tao bai nop, cham, roi luu bai nop cung ket qua tung test.
     *
     * Cham trong mot "o cham" (JudgeSlots): cung luc chi co so bai bang so nhan CPU / so tai khoan sandbox,
     * de cac bai khong tranh CPU (TLE oan) va khong dung vao tien trinh cua nhau.
     */
    public JudgeResult submitCode(Problem problem, User author, Language language, String code) {
        String source = TextUtils.stripBom(code == null ? "" : code);
        // Tao bai nop TRUOC khi vao hang cho cham: gio nop la luc may chu nhan bai, khong phai luc
        // den luot cham. Neu khong, bai nop sat gio khi hang cho dai se bi tinh la nop sau gio thi.
        Submission submission = new Submission(problem, author, language,
                language.defaultFileName(source), source);
        return judgeAndSave(submission);
    }

    private JudgeResult judgeAndSave(Submission submission) {
        JudgeResult result = judgeInSlot(submission);
        // Luu SAU khi cham xong: bai nop va ket qua tung test vao CSDL trong
        // cung mot transaction, khong bao gio con ban ghi do dang.
        submissions.saveWithResults(submission);
        return result;
    }

    // ----------------------------------------------------- hang doi cham bat dong bo

    /**
     * Luong lay bai tu hang doi de cham (FIFO). Moi luong con phai cho mot o cham (JudgeSlots) moi chay, nen
     * so bai chay that su cung luc van bang so o; so luong chi can du de giu hang doi chay deu.
     */
    private final java.util.concurrent.ExecutorService judgeQueue = java.util.concurrent.Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "judge-queue");
        t.setDaemon(true);
        return t;
    });
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();

    /** So bai dang cho hoac dang cham. */
    public int queueLength() { return queued.get(); }

    /**
     * Nhan bai va tra ve NGAY bai nop o trang thai "Chờ chấm"; viec cham chay o luong rieng. Nguoi nop khong
     * giu ket noi HTTP trong luc cho (truoc day moi bai cho giu mot luong cua may chu web), giao dien tu hoi lai
     * ket qua. Gio nop la luc may chu nhan bai, khong phai luc den luot cham. onDone chay khi cham xong (ke ca
     * loi) - vi du mo cong cho nguoi nay nop bai tiep.
     */
    public Submission enqueueSubmission(Problem problem, User author, Language language, String code,
                                        java.util.function.Consumer<Submission> onDone) {
        String source = TextUtils.stripBom(code == null ? "" : code);
        Submission submission = new Submission(problem, author, language, language.defaultFileName(source), source);
        submissions.putPending(submission);
        queued.incrementAndGet();
        judgeQueue.execute(() -> {
            try {
                judgeAndSave(submission);
            } catch (RuntimeException e) {
                System.err.println("Lỗi khi chấm bài " + submission.getId() + ": " + e);
                // Khong de bai treo mai o "Chờ chấm": ghi loi he thong (IE) de nguoi nop biet ma nop lai.
                JudgeResult failed = new JudgeResult(submission);
                failed.markGlobal(Verdict.IE, "Lỗi máy chủ khi chấm bài, hãy nộp lại.");
                submission.setResult(failed);
                try { submissions.saveWithResults(submission); } catch (RuntimeException ignored) { submissions.putPending(submission); }
            } finally {
                queued.decrementAndGet();
                if (onDone != null) onDone.accept(submission);
            }
        });
        return submission;
    }

    /** Cham mot bai trong mot o cham (cho neu dang het o). */
    private JudgeResult judgeInSlot(Submission submission) {
        try {
            return com.ptit.oj.controller.judge.JudgeSlots.run(() -> judge.judge(submission));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Không chấm được bài " + submission.getId(), e);
        }
    }

    /**
     * Tao mot bai nop MAU ma khong chay bo cham (chi DemoData goi, o che do --no-db),
     * de cac trang thong ke, cuoc thi, thanh tich co du lieu ngay khi khoi dong.
     * accepted = true: moi test AC; false: moi test WA.
     */
    public void addSampleSubmission(Problem problem, User author, Language language,
                                    boolean accepted, LocalDateTime submittedAt) {
        Submission submission = new Submission(Submission.newId(), problem, author, language,
                language.defaultFileName(""), submittedAt);
        submission.setSourceCode("// Bài nộp mẫu sinh lúc khởi động (chế độ --no-db), không phải bài nộp thật.");
        JudgeResult result = new JudgeResult(submission);
        for (TestCase test : problem.getTestCases()) {
            result.addTestResult(new TestCaseResult(test, accepted ? Verdict.AC : Verdict.WA, 15,
                    accepted ? test.getPoints() : 0, ""));
        }
        result.setGlobalMessage("BÀI NỘP MẪU: sinh lúc khởi động ở chế độ --no-db, không qua bộ chấm.");
        submission.setResult(result);
        submissions.saveWithResults(submission);
    }

    // ------------------------------- lich su nop bai va bang xep hang (Module 4)

    /** Bai nop cua moi nguoi, moi nhat truoc. */
    public List<Submission> getSubmissionsNewestFirst() { return submissions.findAllNewestFirst(); }

    /** Lich su cua rieng mot nguoi - de hoc sinh chi thay bai cua chinh minh. */
    public List<Submission> getSubmissionsOf(User user) {
        return user == null ? new ArrayList<>() : submissions.findByUserId(user.getId());
    }

    public Optional<Submission> findSubmission(String id) {
        return id == null ? Optional.empty() : submissions.findById(id.trim().toUpperCase());
    }

    public Map<String, Integer> getSolvedCountByUser() { return users.solvedCountByUser(); }

    public List<ScoreboardService.Row> buildScoreboard() { return scoreboard.buildScoreboard(); }

    /** Bang xep hang cua mot lop: chi cac bai cua lop, moi thanh vien deu co mat (xem ScoreboardService.buildFor). */
    public List<ScoreboardService.ClassRow> buildClassScoreboard(List<User> members, java.util.Set<String> problemIds) {
        return scoreboard.buildFor(members, problemIds);
    }
}
