package com.ptit.oj.controller;

import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.User;
import com.ptit.oj.model.Verdict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tro ly AI cho hoc sinh va giang vien (Module 27, 28) - BAN MO PHONG.
 *
 * Khong goi mo hinh AI nao: cau tra loi duoc ghep tu cac LUAT viet san, dua tren
 * verdict gan nhat, vai dau hieu trong ma nguon va so lieu cua AnalyticsService.
 * Tro ly chi goi y huong lam, khong bao gio dua loi giai.
 */
public class AssistantService {

    private final JudgeService judge;
    private final AnalyticsService analytics;

    public AssistantService(JudgeService judge, AnalyticsService analytics) {
        this.judge = judge;
        this.analytics = analytics;
    }

    // ------------------------------------------- tro ly cho hoc sinh (Module 28)

    /**
     * Goi y cho mot bai: nhin verdict lan nop gan nhat cua hoc sinh, soi ma nguon
     * (neu co) va tu khoa trong cau hoi.
     */
    public List<String> hint(Problem problem, User student, String code, String question) {
        // Cau hoi co tu khoa nhan ra duoc thi chi tra loi dung cau do, de khung chat
        // khong lap lai nhan xet ve lan nop gan nhat o moi cau tra loi.
        List<String> answers = questionAdvice(problem, question == null ? "" : question);
        if (!answers.isEmpty()) return answers;

        // Khong co cau hoi (hoac hoi cau chua co luat): nhan xet theo lan nop gan nhat va ma nguon.
        List<String> lines = new ArrayList<>();
        Submission last = null;
        for (Submission s : judge.getSubmissionsOf(student)) {      // moi nhat truoc
            if (s.getProblem().getId().equals(problem.getId()) && s.isJudged()) {
                last = s;
                break;
            }
        }
        lines.add(verdictAdvice(problem, last));
        lines.addAll(codeAdvice(code == null ? "" : code, last));
        return lines;
    }

    private String verdictAdvice(Problem problem, Submission last) {
        if (last == null) {
            return "Bạn chưa nộp bài này. Hãy đọc kỹ phần ví dụ, tự tính tay với một input nhỏ rồi mới viết code.";
        }
        Verdict verdict = last.getResult().getOverallVerdict();
        switch (verdict) {
            case AC:
                return "Lần nộp gần nhất đã AC. Bạn có thể thử viết lại gọn hơn hoặc chuyển sang bài tiếp theo.";
            case WA:
                return "Lần nộp gần nhất bị sai kết quả (WA), qua " + last.getResult().getPassedCount() + "/"
                        + last.getResult().getTotalTests() + " test. Hãy thử các trường hợp biên: giá trị nhỏ nhất, "
                        + "lớn nhất, số âm, dãy chỉ có một phần tử, và kiểm tra lại định dạng in ra.";
            case TLE:
                return "Lần nộp gần nhất chạy quá " + problem.getTimeLimitMs() + " ms (TLE). Máy chấm làm được "
                        + "khoảng 10^8 phép tính mỗi giây: hãy đếm số vòng lặp lồng nhau theo giới hạn của đề.";
            case MLE:
                return "Lần nộp gần nhất vượt " + problem.getMemoryLimitMb() + " MB (MLE). Xem lại kích thước mảng "
                        + "và tránh lưu những gì có thể tính ngay khi đọc.";
            case RE:
                return "Lần nộp gần nhất bị lỗi khi chạy (RE). Nguyên nhân hay gặp: truy cập ngoài mảng, chia cho 0, "
                        + "đệ quy quá sâu hoặc đọc thiếu dữ liệu.";
            case CE:
                return "Lần nộp gần nhất không biên dịch được (CE). Dòng đầu của thông báo lỗi: "
                        + firstLine(last.getResult().getGlobalMessage());
            default:
                return "Lần nộp gần nhất gặp lỗi hệ thống, hãy nộp lại.";
        }
    }

    /** Vai dau hieu de thay trong ma nguon. Chi la tim chuoi, khong phan tich cu phap. */
    private List<String> codeAdvice(String code, Submission last) {
        List<String> lines = new ArrayList<>();
        if (code.trim().isEmpty()) return lines;

        boolean tle = last != null && last.getResult().getOverallVerdict() == Verdict.TLE;
        int loops = count(code, "for") + count(code, "while");

        if (code.contains("int ") && !code.contains("long")) {
            lines.add("Code chỉ dùng kiểu int: nếu kết quả có thể vượt 2·10^9 thì cần long (Java) hoặc long long (C++).");
        }
        if (tle && loops >= 2) {
            lines.add("Code có " + loops + " vòng lặp: nếu chúng lồng nhau thì độ phức tạp là O(n²) trở lên, "
                    + "hãy tìm cách bỏ bớt một vòng (sắp xếp, mảng cộng dồn, hai con trỏ...).");
        }
        if (tle && code.contains("Scanner")) {
            lines.add("Scanner đọc chậm khi dữ liệu lớn: thử BufferedReader kết hợp StringTokenizer.");
        }
        if (tle && code.contains("cin") && !code.contains("sync_with_stdio")) {
            lines.add("Thêm ios::sync_with_stdio(false); cin.tie(nullptr); để cin/cout nhanh hơn.");
        }
        if (code.contains("endl") && loops >= 1) {
            lines.add("In bằng endl trong vòng lặp sẽ chậm, hãy dùng '\\n'.");
        }
        if (code.contains("input()") && loops >= 1) {
            lines.add("Trong Python, đọc bằng sys.stdin nhanh hơn gọi input() nhiều lần.");
        }
        return lines;
    }

    private List<String> questionAdvice(Problem problem, String question) {
        List<String> lines = new ArrayList<>();
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("ý tưởng") || q.contains("gợi ý") || q.contains("hướng") || q.contains("bắt đầu")) {
            Topic topic = judge.findTopic(problem.getTopic()).orElse(null);
            if (topic != null) {
                lines.add("Bài thuộc chủ đề \"" + topic.getName() + "\": " + topic.getDescription()
                        + ". Hãy nghĩ xem kỹ thuật của chủ đề này áp dụng vào đề như thế nào.");
            } else {
                lines.add("Hãy viết ra các bước giải bằng lời trước, mỗi bước một dòng, rồi mới chuyển thành code.");
            }
        }
        if (q.contains("phức tạp") || q.contains("chậm") || q.contains("tle")) {
            lines.add("Bảng ước lượng: n ≤ 10^3 chạy được O(n²); n ≤ 10^5 cần O(n log n); n ≤ 10^7 cần O(n).");
        }
        if (q.contains("test") || q.contains("sai") || q.contains("wa")) {
            lines.add("Vào trang IDE, dán code và tự nhập các input biên để xem kết quả; "
                    + "hoặc dùng Stress test để so với một lời giải vét cạn.");
        }
        if (q.contains("lời giải") || q.contains("đáp án") || q.contains("code mẫu")) {
            lines.add("Trợ lý không đưa lời giải hay code mẫu, chỉ gợi ý hướng làm để bạn tự hoàn thành.");
        }
        return lines;
    }

    // ----------------------------------------- tro ly cho giang vien (Module 27)

    /** Nhan xet tu dong ve mot hoc sinh - dung cho giang vien va bao cao gui phu huynh. */
    public List<String> studentComment(User student) {
        List<Submission> submissions = judge.getSubmissionsOf(student);
        Set<String> solved = analytics.solvedIds(submissions);
        List<String> lines = new ArrayList<>();
        lines.add(student.getFullName() + " đã nộp " + submissions.size() + " lần, giải được "
                + solved.size() + " bài, đạt " + analytics.points(solved) + " điểm thưởng (hạng "
                + analytics.rankName(analytics.points(solved)) + ").");

        List<String> strong = new ArrayList<>();
        List<String> weak = new ArrayList<>();
        for (AnalyticsService.TopicStat stat : analytics.topicStats(submissions)) {
            if (stat.getScore() >= 50) strong.add(stat.getTopic().getName());
            if (stat.isWeak()) weak.add(stat.getTopic().getName());
        }
        if (!strong.isEmpty()) lines.add("Làm tốt các chủ đề: " + String.join(", ", strong) + ".");
        if (!weak.isEmpty()) lines.add("Cần luyện thêm: " + String.join(", ", weak) + ".");
        lines.addAll(analytics.weaknessNotes(submissions));

        int streak = analytics.streakDays(submissions);
        lines.add(streak >= 3 ? "Em đang giữ chuỗi " + streak + " ngày học liên tiếp, rất đáng khen."
                : "Nên duy trì thói quen làm bài đều mỗi ngày.");
        return lines;
    }

    /** Tom tat tinh hinh mot lop va de xuat viec nen lam cho giang vien. */
    public List<String> classSummary(Classroom classroom, List<User> students) {
        List<String> lines = new ArrayList<>();
        if (students.isEmpty()) {
            lines.add("Lớp chưa có học sinh nào.");
            return lines;
        }

        Map<String, Set<String>> solvedByUser = analytics.solvedByUser();
        int totalSolved = 0;
        for (User s : students) totalSolved += solvedCount(solvedByUser, s);
        double average = (double) totalSolved / students.size();
        lines.add(classroom.getName() + " có " + students.size() + " học sinh, trung bình mỗi em giải được "
                + String.format(Locale.ROOT, "%.1f", average) + " bài.");

        List<String> behind = new ArrayList<>();
        for (User s : students) {
            if (solvedCount(solvedByUser, s) < average / 2) behind.add(s.getFullName());
        }
        if (!behind.isEmpty()) {
            lines.add("Học sinh đang chậm hơn nhiều so với lớp (dưới nửa mức trung bình): "
                    + String.join(", ", behind) + ". Nên nhắc riêng hoặc giao bài mức Dễ.");
        }

        // Bai bi nop sai nhieu nhat trong lop -> nen chua tren lop.
        Map<String, Integer> failedByProblem = new HashMap<>();
        for (User s : students) {
            for (Submission sub : judge.getSubmissionsOf(s)) {
                if (sub.isJudged() && !sub.getResult().getOverallVerdict().isAccepted()) {
                    failedByProblem.merge(sub.getProblem().getId(), 1, Integer::sum);
                }
            }
        }
        String hardest = null;
        for (Map.Entry<String, Integer> e : failedByProblem.entrySet()) {
            if (hardest == null || e.getValue() > failedByProblem.get(hardest)) hardest = e.getKey();
        }
        if (hardest != null) {
            lines.add("Bài bị nộp sai nhiều nhất: " + hardest + " (" + failedByProblem.get(hardest)
                    + " lần). Nên chữa bài này trong buổi học tới.");
        } else {
            lines.add("Chưa có bài nào bị nộp sai trong lớp.");
        }
        return lines;
    }

    private int solvedCount(Map<String, Set<String>> solvedByUser, User user) {
        Set<String> solved = solvedByUser.get(user.getId());
        return solved == null ? 0 : solved.size();
    }

    private int count(String text, String word) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(word, from)) >= 0) {
            count++;
            from += word.length();
        }
        return count;
    }

    private String firstLine(String text) {
        for (String line : text.split("\r?\n")) {
            if (!line.trim().isEmpty()) return line.trim();
        }
        return "(không có thông báo)";
    }
}
