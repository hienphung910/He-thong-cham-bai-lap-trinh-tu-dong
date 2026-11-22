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
                        SubmissionRepository submissions, LanguageRegistry languages) {
        this.dataDir = dataDir;
        this.problems = problems;
        this.users = users;
        this.submissions = submissions;
        this.languages = languages;
        this.auth = new AuthService(users, new PasswordService(), new SessionService());
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
        MySqlSubmissionRepository submissions =
                new MySqlSubmissionRepository(database, problems, users, languages);
        return new JudgeService(dataDir, problems, users, submissions, languages);
    }

    /** Che do --no-db: day du tinh nang nhung moi thu nam trong RAM, tat may chu la mat. */
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
            if (!p.getTopic().isEmpty() && !findTopic(p.getTopic()).isPresent()) {
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

    public Optional<Topic> findTopic(String id) {
        for (Topic t : topics) {
            if (t.getId().equals(id)) return Optional.of(t);
        }
        return Optional.empty();
    }

    public Optional<Problem> findProblem(String id) {
        return id == null ? Optional.empty() : problems.findById(id.trim().toUpperCase());
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

    public Optional<Language> findLanguage(String name) { return languages.byName(name); }

    /**
     * Nop bai: tao bai nop, cham, roi luu bai nop cung ket qua tung test.
     *
     * synchronized de cham TUAN TU: neu hai bai chay cung luc thi hai tien trinh
     * tranh CPU, thoi gian do duoc sai lech va de bi TLE oan.
     */
    public synchronized JudgeResult submitCode(Problem problem, User author, Language language, String code) {
        String source = TextUtils.stripBom(code == null ? "" : code);
        Submission submission = new Submission(problem, author, language,
                language.defaultFileName(source), source);
        JudgeResult result = judge.judge(submission);
        // Luu SAU khi cham xong: bai nop va ket qua tung test vao CSDL trong
        // cung mot transaction, khong bao gio con ban ghi do dang.
        submissions.saveWithResults(submission);
        return result;
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
}
