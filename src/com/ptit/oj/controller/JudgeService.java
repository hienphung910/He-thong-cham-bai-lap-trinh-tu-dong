package com.ptit.oj.controller;

import com.ptit.oj.controller.judge.Judge;
import com.ptit.oj.controller.judge.Language;
import com.ptit.oj.controller.judge.LanguageRegistry;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.InMemorySubmissionRepository;
import com.ptit.oj.dao.InMemoryUserRepository;
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
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.User;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Diem vao nghiep vu dung chung cho ca 4 module: giu du lieu (bai tap, bai nop,
 * nguoi dung) va dieu phoi bo cham. Cac lop giao dien chi goi lop nay, khong goi
 * thang xuong cac kho du lieu.
 *
 * Cac kho du lieu duoc TIEM VAO qua constructor chu khong tao san ben trong, nen
 * doi giua MySQL va bo nho (--no-db) chi la chon ham mysql() hay inMemory().
 */
public class JudgeService {

    private Path dataDir;
    private Repository<Problem> problems;
    private UserRepository users;
    private SubmissionRepository submissions;
    private LanguageRegistry languages;
    private Judge judge = new Judge();
    /** Gioi han do dai ma nguon cua mot bai nop (512 KB). */
    private static final int MAX_SOURCE_CHARS = 512 * 1024;

    private AuthService auth;
    private ProblemAdminService problemAdmin;
    private ScoreboardService scoreboard;
    /** Lo trinh hoc, nap mot lan trong loadData(). */
    private List<Topic> topics = new ArrayList<>();

    public JudgeService(Path dataDir, Repository<Problem> problems, UserRepository users,
                        SubmissionRepository submissions, LanguageRegistry languages) {
        this.dataDir = dataDir;
        this.problems = problems;
        this.users = users;
        this.submissions = submissions;
        this.languages = languages;
        this.auth = new AuthService(users, new PasswordService(), new SessionService());
        this.problemAdmin = new ProblemAdminService(problems, dataDir.resolve("problems"), topics);
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
        MySqlSubmissionRepository submissions =
                new MySqlSubmissionRepository(database, problems, users, languages);
        return new JudgeService(dataDir, problems, users, submissions, languages);
    }

    /** Che do --no-db: day du tinh nang nhung moi thu nam trong RAM, tat chuong trinh la mat. */
    public static JudgeService inMemory(Path dataDir) {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemorySubmissionRepository submissions = new InMemorySubmissionRepository();
        users.setSubmissionRepository(submissions);
        return new JudgeService(dataDir, new InMemoryRepository<>(), users, submissions, new LanguageRegistry());
    }

    /** Nap lo trinh hoc va de bai tu o dia. */
    public void loadData() {
        topics.clear();
        topics.addAll(new TopicLoader(dataDir.resolve("topics.txt")).loadAll());
        for (Problem p : new ProblemLoader(dataDir.resolve("problems")).loadAll()) {
            problems.save(p);
            if (!p.getTopic().isEmpty() && findTopic(p.getTopic()) == null) {
                System.out.println("Cảnh báo: bài " + p.getId() + " gắn chủ đề \"" + p.getTopic()
                        + "\" không có trong data/topics.txt - sẽ hiện ở nhóm \"Bài khác\".");
            }
        }
    }

    // ------------------------------------------------------- tai khoan (Module 1)

    public AuthService getAuth() { return auth; }

    public List<User> getUsers() { return users.findAll(); }

    // ------------------------------------------- bai tap va lo trinh hoc (Module 2)

    public List<Problem> getProblems() { return problems.findAll(); }

    /** Cac chu de theo dung thu tu hoc. */
    public List<Topic> getTopics() { return Collections.unmodifiableList(topics); }

    /** Tim chu de theo ma; tra ve null neu khong co trong lo trinh. */
    public Topic findTopic(String id) {
        for (Topic t : topics) {
            if (t.getId().equals(id)) return t;
        }
        return null;
    }

    /** Tim bai tap theo ma (khong phan biet hoa thuong); tra ve null neu khong co. */
    public Problem findProblem(String id) {
        if (id == null) return null;
        return problems.findById(id.trim().toUpperCase());
    }

    /** Tao bai tap moi - chi tai khoan ADMIN goi duoc (kiem tra trong ProblemAdminService). */
    public Problem createProblem(User actor, ProblemDraft draft) {
        return problemAdmin.createProblem(actor, draft);
    }

    /** Doi chu de va do kho cua bai da co - chi ADMIN. */
    public Problem updateProblemMeta(User actor, String problemId, String topic, int difficulty) {
        return problemAdmin.updateProblemMeta(actor, problemId, topic, difficulty);
    }

    // --------------------------------------------- nop bai va cham tu dong (Module 3)

    public List<Language> getLanguages() { return languages.all(); }

    /** Tim ngon ngu theo ten; tra ve null neu he thong khong ho tro. */
    public Language findLanguage(String name) { return languages.byName(name); }

    /**
     * Nop bai: kiem tra du lieu, tao bai nop, cham, roi luu bai nop cung ket qua tung test.
     *
     * Moi lan chi cham mot bai: neu hai bai chay cung luc thi hai tien trinh tranh
     * CPU, thoi gian do duoc sai lech va de bi TLE oan.
     */
    public JudgeResult submitCode(Problem problem, User author, Language language, String code) {
        if (!language.isAvailable()) {
            throw new IllegalArgumentException("Máy này chưa cài toolchain cho " + language.getName());
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã nguồn rỗng");
        }
        if (code.length() > MAX_SOURCE_CHARS) {
            throw new IllegalArgumentException("Mã nguồn lớn hơn 512 KB");
        }
        String source = TextUtils.stripBom(code);
        Submission submission = new Submission(problem, author, language,
                language.defaultFileName(source), source);
        JudgeResult result = judge.judge(submission);
        // Luu SAU khi cham xong: bai nop va ket qua tung test vao CSDL trong
        // cung mot transaction, khong bao gio con ban ghi do dang.
        submissions.saveWithResults(submission);
        return result;
    }

    // ------------------------------- lich su nop bai va bang xep hang (Module 4)

    /** Giang vien va quan tri vien xem duoc bai nop cua moi nguoi; sinh vien chi xem cua minh. */
    public boolean canSeeEveryone(User user) {
        return User.ROLE_ADMIN.equals(user.getRoleCode()) || User.ROLE_TEACHER.equals(user.getRoleCode());
    }

    /** Lich su nop bai ma nguoi xem duoc phep thay, moi nhat truoc. */
    public List<Submission> getSubmissionsFor(User viewer) {
        if (canSeeEveryone(viewer)) {
            return getSubmissionsNewestFirst();
        }
        return getSubmissionsOf(viewer);
    }

    /** Chi tiet mot bai nop. Chan viec xem ma nguon cua nguoi khac khi khong du quyen. */
    public Submission getSubmissionDetail(User viewer, String id) {
        Submission s = findSubmission(id);
        if (s == null) {
            throw new IllegalArgumentException("Không có mã nộp " + id);
        }
        if (!canSeeEveryone(viewer) && !s.getAuthor().getId().equals(viewer.getId())) {
            throw new IllegalArgumentException("Bạn chỉ được xem bài nộp của chính mình");
        }
        return s;
    }

    /** Bai nop cua moi nguoi, moi nhat truoc. */
    public List<Submission> getSubmissionsNewestFirst() { return submissions.findAllNewestFirst(); }

    /** Lich su cua rieng mot nguoi, moi nhat truoc - sinh vien chi thay bai cua chinh minh. */
    public List<Submission> getSubmissionsOf(User user) {
        if (user == null) return new ArrayList<>();
        return submissions.findByUserId(user.getId());
    }

    /** Tim bai nop theo ma; tra ve null neu khong co. */
    public Submission findSubmission(String id) {
        if (id == null) return null;
        return submissions.findById(id.trim().toUpperCase());
    }

    public Map<String, Integer> getSolvedCountByUser() { return users.solvedCountByUser(); }

    public List<ScoreboardService.Row> buildScoreboard() { return scoreboard.buildScoreboard(); }
}
