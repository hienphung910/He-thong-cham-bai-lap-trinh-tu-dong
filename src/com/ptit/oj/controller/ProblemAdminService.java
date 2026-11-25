package com.ptit.oj.controller;

import com.ptit.oj.controller.judge.ComparatorFactory;
import com.ptit.oj.dao.ProblemLoader;
import com.ptit.oj.dao.ProblemWriter;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.ProblemDraft;
import com.ptit.oj.model.TestCase;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Nghiep vu quan tri de bai: kiem tra hop le -> ghi ra o dia -> nap vao bo nho.
 *
 * Hai kho bai:
 *  - KHO CHUNG (Problem.getOwner() rong): chi quan tri vien tao va sua.
 *  - KHO RIENG cua tung giang vien (owner = ten dang nhap): giang vien tu tao, chi sua bai cua minh.
 * Quyen sua lay tu User.canEditProblem(), khong viet if theo vai tro o day.
 *
 * Tang web chi viec doc JSON roi goi createProblem(); moi luat kiem tra nam o day
 * nen du goi tu dau thi du lieu cung duoc bao ve giong het.
 */
public class ProblemAdminService {

    /** Ma bai: chi chu IN HOA, so, gach duoi va gach ngang -> khong the thanh duong dan. */
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Z0-9_-]{1,30}");   // 30 = do rong cot submissions.problem_id
    /** Ten test: chu thuong/hoa, so, gach duoi, gach ngang. Khong co dau cham hay dau gach cheo. */
    private static final Pattern TEST_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private static final long MIN_TIME_LIMIT_MS = 100;
    private static final long MAX_TIME_LIMIT_MS = 60_000;
    private static final int MIN_MEMORY_MB = 8;
    private static final int MAX_MEMORY_MB = 2048;
    private static final double MAX_TOTAL_POINTS = 100_000;
    private static final int MAX_TESTS = 200;
    private static final int MAX_TITLE = 200;
    private static final int MAX_STATEMENT = 200_000;
    private static final int MAX_TEST_CHARS = 1_000_000;

    private final Repository<Problem> problems;
    private final ProblemWriter writer;
    private final ProblemLoader loader;
    /** Hoi JudgeService xem ma chu de co trong lo trinh khong (lo trinh nap sau constructor). */
    private final Predicate<String> topicExists;

    public ProblemAdminService(Repository<Problem> problems, Path problemsDir, Predicate<String> topicExists) {
        this.problems = problems;
        this.writer = new ProblemWriter(problemsDir);
        this.loader = new ProblemLoader(problemsDir);
        this.topicExists = topicExists;
    }

    /** Loi nghiep vu kem ma HTTP goi y (400 sai du lieu, 403 thieu quyen, 409 trung ma bai). */
    public static class ProblemAdminException extends RuntimeException {
        private final int httpStatus;

        public ProblemAdminException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() { return httpStatus; }
    }

    /**
     * Tao bai tap moi. Chi tai khoan co canCreateProblem() == true moi duoc goi
     * (tang web da chan truoc, day la lop chan thu hai - khong tin giao dien).
     * Bai cua quan tri vien vao kho chung; bai cua giang vien vao kho rieng cua giang vien do.
     */
    public synchronized Problem createProblem(User actor, ProblemDraft draft) {
        if (actor == null || !actor.canCreateProblem()) {
            throw new ProblemAdminException(403, "Chỉ giảng viên hoặc quản trị viên mới được tạo bài tập");
        }
        // Chu bai lay tu tai khoan dang dang nhap, khong bao gio lay tu du lieu client gui len.
        boolean admin = User.ROLE_ADMIN.equals(actor.getRoleCode());
        draft.setOwner(admin ? "" : actor.getId());
        validate(draft);

        Path dir;
        try {
            dir = writer.write(draft);
        } catch (IOException e) {
            throw new ProblemAdminException(500, "Không ghi được bài tập ra ổ đĩa: " + e.getMessage());
        }

        // Nap lai tu chinh file vua ghi: bao dam thu muc tren o dia va du lieu
        // trong bo nho luon khop nhau, va bat som neu ghi ra thu bi loi.
        Problem problem = loader.load(dir);
        problems.save(problem);
        return problem;
    }

    /** Doi chu de va do kho cua mot bai DA CO; cac dong khac cua problem.properties giu nguyen. */
    public synchronized Problem updateProblemMeta(User actor, String rawId, String rawTopic, int difficulty) {
        Problem current = findForEdit(actor, rawId);
        String id = current.getId();
        String topic = validateTopic(rawTopic);
        validateDifficulty(difficulty);

        // Gia tri null = xoa dong do khoi file, giong luc tao bai khong chon chu de / do kho.
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("topic", topic.isEmpty() ? null : topic);
        changes.put("difficulty", difficulty > 0 ? String.valueOf(difficulty) : null);
        if (!topic.equals(current.getTopic())) {
            changes.put("order", null);     // sang chu de khac: bo thu tu cu, bai xep theo ma bai
        }
        try {
            // Ghi ra o dia roi nap lai tu chinh file vua ghi, nhu createProblem().
            Problem problem = loader.load(writer.updateProperties(id, changes));
            problems.save(problem);
            return problem;
        } catch (IOException e) {
            throw new ProblemAdminException(500, "Không ghi được bài tập ra ổ đĩa: " + e.getMessage());
        }
    }

    /**
     * Ban cu ghi chu bai bang TEN DANG NHAP trong problem.properties. Chuyen mot lan sang MA TAI KHOAN:
     * bai nao co "owner" khong phai ma cua mot tai khoan dang ton tai ma trung ten dang nhap cua mot
     * tai khoan thi ghi lai bang ma cua tai khoan do. Chu bai khong con ton tai thi de nguyen - bai do
     * chi quan tri vien sua duoc.
     * @return so bai da chuyen
     */
    public synchronized int migrateOwners(com.ptit.oj.dao.UserRepository users) {
        int changed = 0;
        for (Problem p : problems.findAll()) {
            String owner = p.getOwner();
            if (owner.isEmpty() || users.findById(owner).isPresent()) continue;
            java.util.Optional<User> byName = users.findByUsername(owner);
            if (!byName.isPresent()) continue;
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("owner", byName.get().getId());
            try {
                problems.save(loader.load(writer.updateProperties(p.getId(), changes)));
                changed++;
            } catch (IOException | RuntimeException e) {
                System.err.println("Không chuyển được chủ của bài " + p.getId() + ": " + e.getMessage());
            }
        }
        return changed;
    }

    /** Sua ten, de bai va gioi han thoi gian / bo nho cua mot bai DA CO. Test, chu de, chu bai giu nguyen. */
    public synchronized Problem updateProblem(User actor, String rawId, String title, String statement,
                                              long timeLimitMs, int memoryLimitMb) {
        Problem current = findForEdit(actor, rawId);
        String id = current.getId();
        String cleanTitle = requireText(title, "Tên bài", MAX_TITLE);
        String cleanStatement = requireText(statement, "Nội dung đề", MAX_STATEMENT);
        if (timeLimitMs < MIN_TIME_LIMIT_MS || timeLimitMs > MAX_TIME_LIMIT_MS) {
            throw new ProblemAdminException(400,
                    "Giới hạn thời gian phải từ " + MIN_TIME_LIMIT_MS + " đến " + MAX_TIME_LIMIT_MS + " ms");
        }
        if (memoryLimitMb < MIN_MEMORY_MB || memoryLimitMb > MAX_MEMORY_MB) {
            throw new ProblemAdminException(400,
                    "Giới hạn bộ nhớ phải từ " + MIN_MEMORY_MB + " đến " + MAX_MEMORY_MB + " MB");
        }
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("title", cleanTitle);
        changes.put("timeLimitMs", String.valueOf(timeLimitMs));
        changes.put("memoryLimitMb", String.valueOf(memoryLimitMb));
        try {
            writer.writeStatement(id, cleanStatement);
            Problem problem = loader.load(writer.updateProperties(id, changes));
            problems.save(problem);
            return problem;
        } catch (IOException e) {
            throw new ProblemAdminException(500, "Không ghi được bài tập ra ổ đĩa: " + e.getMessage());
        }
    }

    /** Xoa han mot bai (thu muc tren o dia va ban trong bo nho). Nguoi goi da kiem tra bai khong con duoc dung o dau. */
    public synchronized Problem deleteProblem(User actor, String rawId) {
        Problem current = findForEdit(actor, rawId);
        try {
            writer.delete(current.getId());
        } catch (IOException e) {
            throw new ProblemAdminException(500, "Không xoá được bài tập trên ổ đĩa: " + e.getMessage());
        }
        problems.deleteById(current.getId());
        return current;
    }

    // ------------------------------------------------- quan ly bo test (Module 7)

    /** Them mot test vao bai DA CO. Diem moi test duoc chia deu lai khi nap lai bai. */
    public synchronized Problem addTest(User actor, String rawId, ProblemDraft.TestDraft test) {
        Problem current = findForEdit(actor, rawId);
        if (current.getTestCases().size() >= MAX_TESTS) {
            throw new ProblemAdminException(400, "Tối đa " + MAX_TESTS + " test cho một bài");
        }
        // Dua ten cac test dang co vao "seen" de validateTest bat duoc ten trung.
        Set<String> seen = new LinkedHashSet<>();
        for (TestCase tc : current.getTestCases()) seen.add(tc.getId().toLowerCase(Locale.ROOT));
        validateTest(test, seen);

        try {
            writer.writeTest(current.getId(), test.getName().trim(), test.getInput(), test.getOutput());
        } catch (IOException e) {
            throw new ProblemAdminException(500, "Không ghi được test ra ổ đĩa: " + e.getMessage());
        }
        return reload(current.getId());
    }

    /** Xoa mot test cua bai da co; bai phai con lai it nhat mot test. */
    public synchronized Problem deleteTest(User actor, String rawId, String testName) {
        Problem current = findForEdit(actor, rawId);
        boolean exists = false;
        for (TestCase tc : current.getTestCases()) {
            if (tc.getId().equals(testName)) exists = true;
        }
        // Chi xoa ten test co trong danh sach da nap -> khong the truyen duong dan la vao day.
        if (!exists) throw new ProblemAdminException(404, "Bài " + current.getId() + " không có test " + testName);
        if (current.getTestCases().size() == 1) {
            throw new ProblemAdminException(400, "Bài phải còn ít nhất một test");
        }
        try {
            writer.deleteTest(current.getId(), testName);
        } catch (IOException e) {
            throw new ProblemAdminException(500, "Không xoá được test: " + e.getMessage());
        }
        return reload(current.getId());
    }

    /**
     * Tim bai va kiem tra actor co quyen sua bai do khong. Moi ham sua bai (va ham xem test an)
     * deu di qua day, nen giang vien khong the dung vao kho chung hay kho cua nguoi khac.
     */
    public synchronized Problem findForEdit(User actor, String rawId) {
        String id = normalizeId(rawId);
        Problem problem = problems.findById(id)
                .orElseThrow(() -> new ProblemAdminException(404, "Không có bài " + id));
        if (actor == null || !actor.canEditProblem(problem)) {
            throw new ProblemAdminException(403, problem.isShared()
                    ? "Bài " + id + " thuộc kho bài chung, chỉ quản trị viên mới sửa được"
                    : "Bài " + id + " nằm trong kho riêng của giảng viên khác");
        }
        return problem;
    }

    /** Nap lai bai tu o dia de bo nho luon khop voi thu muc tests/. */
    private Problem reload(String id) {
        Problem problem = loader.load(writer.problemDir(id));
        problems.save(problem);
        return problem;
    }

    // ------------------------------------------------------------ kiem tra

    private void validate(ProblemDraft d) {
        String id = normalizeId(d.getId());
        d.setId(id);

        if (problems.findById(id).isPresent() || writer.exists(id)) {
            throw new ProblemAdminException(409, "Mã bài " + id + " đã tồn tại");
        }

        d.setTitle(requireText(d.getTitle(), "Tên bài", MAX_TITLE));
        d.setStatement(requireText(d.getStatement(), "Nội dung đề", MAX_STATEMENT));

        if (d.getTimeLimitMs() < MIN_TIME_LIMIT_MS || d.getTimeLimitMs() > MAX_TIME_LIMIT_MS) {
            throw new ProblemAdminException(400,
                    "Giới hạn thời gian phải từ " + MIN_TIME_LIMIT_MS + " đến " + MAX_TIME_LIMIT_MS + " ms");
        }
        if (d.getMemoryLimitMb() < MIN_MEMORY_MB || d.getMemoryLimitMb() > MAX_MEMORY_MB) {
            throw new ProblemAdminException(400,
                    "Giới hạn bộ nhớ phải từ " + MIN_MEMORY_MB + " đến " + MAX_MEMORY_MB + " MB");
        }
        if (!(d.getTotalPoints() > 0) || d.getTotalPoints() > MAX_TOTAL_POINTS) {
            throw new ProblemAdminException(400, "Tổng điểm phải lớn hơn 0 và tối đa " + (long) MAX_TOTAL_POINTS);
        }

        String comparator = d.getComparator() == null ? "token" : d.getComparator().trim();
        try {
            ComparatorFactory.create(comparator);       // nem neu kieu so sanh khong ho tro
        } catch (IllegalArgumentException e) {
            throw new ProblemAdminException(400, e.getMessage());
        }
        d.setComparator(comparator);

        d.setTopic(validateTopic(d.getTopic()));
        validateDifficulty(d.getDifficulty());

        if (d.getTests().isEmpty()) {
            throw new ProblemAdminException(400, "Bài tập phải có ít nhất một test");
        }
        if (d.getTests().size() > MAX_TESTS) {
            throw new ProblemAdminException(400, "Tối đa " + MAX_TESTS + " test cho một bài");
        }

        Set<String> seen = new LinkedHashSet<>();
        for (ProblemDraft.TestDraft t : d.getTests()) {
            validateTest(t, seen);
        }
    }

    /** Tra ve ma chu de da cat khoang trang; "" = chua phan loai (nhom "Bai khac"). */
    private String validateTopic(String rawTopic) {
        String topic = rawTopic == null ? "" : rawTopic.trim();
        if (!topic.isEmpty() && !topicExists.test(topic)) {
            throw new ProblemAdminException(400, "Chủ đề \"" + topic + "\" không có trong lộ trình (data/topics.txt)");
        }
        return topic;
    }

    private void validateDifficulty(int difficulty) {
        if (difficulty < 0 || difficulty > 3) {
            throw new ProblemAdminException(400, "Độ khó phải là 1 (Dễ), 2 (Vừa), 3 (Khó) hoặc 0 (chưa đánh giá)");
        }
    }

    private void validateTest(ProblemDraft.TestDraft t, Set<String> seen) {
        String name = t.getName() == null ? "" : t.getName().trim();
        if (name.isEmpty()) {
            throw new ProblemAdminException(400, "Tên test không được để trống");
        }
        // Chan truoc cac dang duong dan de thong bao loi noi dung ro rang.
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new ProblemAdminException(400, "Tên test \"" + name + "\" chứa ký tự đường dẫn");
        }
        if (!TEST_NAME_PATTERN.matcher(name).matches()) {
            throw new ProblemAdminException(400,
                    "Tên test \"" + name + "\" chỉ được gồm chữ, số, gạch dưới hoặc gạch ngang");
        }
        // He thong file cua Windows khong phan biet hoa thuong -> "01" va "01" phai coi la trung.
        if (!seen.add(name.toLowerCase(Locale.ROOT))) {
            throw new ProblemAdminException(400, "Tên test \"" + name + "\" bị trùng");
        }

        boolean looksSample = name.toLowerCase(Locale.ROOT).startsWith("sample");
        if (!t.isSample() && looksSample) {
            throw new ProblemAdminException(400,
                    "Test ẩn không được đặt tên bắt đầu bằng \"sample\" (tên này dành cho test ví dụ)");
        }
        if (t.isSample() && !looksSample) {
            throw new ProblemAdminException(400,
                    "Test ví dụ phải có tên bắt đầu bằng \"sample\", ví dụ sample01");
        }

        requireText(t.getInput(), "Input của test " + name, MAX_TEST_CHARS);
        requireText(t.getOutput(), "Output của test " + name, MAX_TEST_CHARS);
    }

    private String normalizeId(String rawId) {
        String id = rawId == null ? "" : rawId.trim().toUpperCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new ProblemAdminException(400, "Thiếu mã bài");
        }
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new ProblemAdminException(400, "Mã bài chứa ký tự đường dẫn không hợp lệ");
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new ProblemAdminException(400,
                    "Mã bài chỉ được gồm chữ in hoa, số, gạch dưới hoặc gạch ngang (tối đa 30 ký tự)");
        }
        return id;
    }

    private String requireText(String value, String label, int max) {
        String v = value == null ? "" : value;
        if (v.trim().isEmpty()) {
            throw new ProblemAdminException(400, label + " không được để trống");
        }
        if (v.length() > max) {
            throw new ProblemAdminException(400, label + " quá dài (tối đa " + max + " ký tự)");
        }
        return v;
    }
}
