package com.ptit.oj.controller;

import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.Verdict;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phan tich ket qua hoc tap (Module 23-26) va thanh tich (Module 38).
 *
 * Moi con so o day deu TINH LAI tu lich su nop bai, khong luu rieng bang nao:
 * dau vao cua hau het cac ham la danh sach bai nop cua mot hoc sinh.
 */
public class AnalyticsService {

    private final JudgeService judge;

    public AnalyticsService(JudgeService judge) {
        this.judge = judge;
    }

    // ------------------------------------------------------------ kieu ket qua

    /** So lieu cua mot hoc sinh trong mot chu de. */
    public static class TopicStat {
        private final Topic topic;
        private int total;       // so bai cua chu de
        private int solved;      // so bai da AC
        private int attempts;    // so lan nop vao cac bai cua chu de
        private int failed;      // trong do bao nhieu lan khong AC

        TopicStat(Topic topic) { this.topic = topic; }

        public Topic getTopic() { return topic; }
        public int getTotal() { return total; }
        public int getSolved() { return solved; }
        public int getAttempts() { return attempts; }
        public int getFailed() { return failed; }

        /** Diem ky nang 0-100 = ti le bai da giai trong chu de. */
        public int getScore() { return total == 0 ? 0 : solved * 100 / total; }

        public String getLevel() {
            int score = getScore();
            if (score >= 80) return "Thành thạo";
            if (score >= 50) return "Khá";
            if (score >= 20) return "Cơ bản";
            return solved > 0 ? "Mới bắt đầu" : "Chưa học";
        }

        /** Chu de yeu: da nop tu 3 lan tro len ma it nhat mot nua so lan la sai. */
        public boolean isWeak() { return attempts >= 3 && failed * 2 >= attempts; }
    }

    /** Mot bai duoc goi y kem ly do. */
    public static class Recommendation {
        private final Problem problem;
        private final String reason;

        Recommendation(Problem problem, String reason) {
            this.problem = problem;
            this.reason = reason;
        }

        public Problem getProblem() { return problem; }
        public String getReason() { return reason; }
    }

    /** Mot huy hieu va trang thai da dat hay chua. */
    public static class Badge {
        private final String name;
        private final String description;
        private final boolean earned;

        Badge(String name, String description, boolean earned) {
            this.name = name;
            this.description = description;
            this.earned = earned;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isEarned() { return earned; }
    }

    // ------------------------------------------------------- so lieu co ban

    private boolean isAccepted(Submission s) {
        return s.isJudged() && s.getResult().getOverallVerdict().isAccepted();
    }

    /** Ma cac bai da AC it nhat mot lan. */
    public Set<String> solvedIds(List<Submission> submissions) {
        Set<String> solved = new HashSet<>();
        for (Submission s : submissions) {
            if (isAccepted(s)) solved.add(s.getProblem().getId());
        }
        return solved;
    }

    /**
     * Nhu solvedByUser() nhung chi tinh bai KHO CHUNG. Dung cho bang diem thuong va so bai da giai
     * hien cho nguoi khac: giang vien khong the tu tao bai trong kho rieng roi giai de tang thanh tich.
     */
    public Map<String, Set<String>> sharedSolvedByUser() {
        Map<String, Set<String>> result = new HashMap<>();
        for (Submission s : judge.getSubmissionsNewestFirst()) {
            if (!isAccepted(s) || !s.getProblem().isShared()) continue;
            result.computeIfAbsent(s.getAuthor().getId(), k -> new HashSet<>()).add(s.getProblem().getId());
        }
        return result;
    }

    /** userId -> ma cac bai nguoi do da AC. Duyet toan bo lich su dung mot lan. */
    public Map<String, Set<String>> solvedByUser() {
        Map<String, Set<String>> result = new HashMap<>();
        for (Submission s : judge.getSubmissionsNewestFirst()) {
            if (!isAccepted(s)) continue;
            result.computeIfAbsent(s.getAuthor().getId(), k -> new HashSet<>()).add(s.getProblem().getId());
        }
        return result;
    }

    /** Dem so bai nop theo verdict: "AC" -> 12, "WA" -> 5... */
    public Map<String, Integer> verdictCounts(List<Submission> submissions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Verdict v : Verdict.values()) counts.put(v.getCode(), 0);
        for (Submission s : submissions) {
            if (s.isJudged()) counts.merge(s.getResult().getOverallVerdict().getCode(), 1, Integer::sum);
        }
        return counts;
    }

    /** So bai nop moi ngay trong "days" ngay gan nhat (ke ca hom nay), ngay cu dung truoc. */
    public Map<String, Integer> activityByDay(List<Submission> submissions, int days) {
        Map<String, Integer> perDay = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) perDay.put(today.minusDays(i).toString(), 0);
        for (Submission s : submissions) {
            String day = s.getSubmittedAt().toLocalDate().toString();
            if (perDay.containsKey(day)) perDay.merge(day, 1, Integer::sum);
        }
        return perDay;
    }

    /** So ngay lien tiep gan day co nop bai (tinh nguoc tu hom nay hoac hom qua). */
    public int streakDays(List<Submission> submissions) {
        Set<LocalDate> activeDays = new HashSet<>();
        for (Submission s : submissions) activeDays.add(s.getSubmittedAt().toLocalDate());

        LocalDate day = LocalDate.now();
        if (!activeDays.contains(day)) day = day.minusDays(1);   // hom nay chua nop thi chuoi van con
        int streak = 0;
        while (activeDays.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    // ----------------------------------------- ky nang theo chu de (Module 24)

    /** Thong ke theo tung chu de cua lo trinh, dung thu tu hoc. */
    public List<TopicStat> topicStats(List<Submission> submissions) {
        Map<String, TopicStat> byTopic = new LinkedHashMap<>();
        for (Topic t : judge.getTopics()) byTopic.put(t.getId(), new TopicStat(t));

        Set<String> solved = solvedIds(submissions);
        for (Problem p : judge.getSharedProblems()) {      // chi tinh kho chung
            TopicStat stat = byTopic.get(p.getTopic());
            if (stat == null) continue;                 // bai chua gan chu de
            stat.total++;
            if (solved.contains(p.getId())) stat.solved++;
        }
        for (Submission s : submissions) {
            if (!s.getProblem().isShared()) continue;       // bai kho rieng khong tinh vao ky nang theo chu de
            TopicStat stat = byTopic.get(s.getProblem().getTopic());
            if (stat == null) continue;
            stat.attempts++;
            if (!isAccepted(s)) stat.failed++;
        }
        return new ArrayList<>(byTopic.values());
    }

    // ------------------------------------------- diem yeu (Module 25)

    /** Nhan xet ve kieu loi hay gap, suy ra tu ti le cac verdict. */
    public List<String> weaknessNotes(List<Submission> submissions) {
        List<String> notes = new ArrayList<>();
        int total = submissions.size();
        if (total < 5) {
            notes.add("Chưa đủ dữ liệu (cần ít nhất 5 bài nộp) để nhận xét về kiểu lỗi.");
            return notes;
        }
        Map<String, Integer> v = verdictCounts(submissions);
        if (v.get("WA") * 100 / total >= 40) {
            notes.add("Sai kết quả (WA) chiếm " + v.get("WA") * 100 / total
                    + "% số bài nộp: nên tự thử các test biên (n = 1, số âm, số rất lớn) trước khi nộp.");
        }
        if (v.get("TLE") * 100 / total >= 15) {
            notes.add("Hay bị quá thời gian (TLE): hãy ước lượng độ phức tạp theo giới hạn đề bài trước khi code.");
        }
        if (v.get("RE") * 100 / total >= 15) {
            notes.add("Hay gặp lỗi khi chạy (RE): kiểm tra chỉ số mảng, phép chia cho 0 và kích thước mảng.");
        }
        if (v.get("CE") * 100 / total >= 15) {
            notes.add("Lỗi biên dịch (CE) nhiều: hãy chạy thử trên máy hoặc ở trang IDE trước khi nộp.");
        }
        if (v.get("MLE") * 100 / total >= 10) {
            notes.add("Hay vượt giới hạn bộ nhớ (MLE): xem lại kích thước mảng và kiểu dữ liệu.");
        }
        if (notes.isEmpty()) notes.add("Chưa thấy kiểu lỗi nào nổi bật. Tiếp tục giữ phong độ!");
        return notes;
    }

    // --------------------------------------- goi y bai tap (Module 26)

    /** Cac bai theo dung thu tu lo trinh: chu de truoc, roi thu tu trong chu de, roi ma bai. */
    public List<Problem> problemsInPathOrder() {
        Map<String, Integer> topicIndex = new HashMap<>();
        List<Topic> topics = judge.getTopics();
        for (int i = 0; i < topics.size(); i++) topicIndex.put(topics.get(i).getId(), i);

        List<Problem> ordered = judge.getSharedProblems();     // lo trinh chi gom bai kho chung
        ordered.sort((a, b) -> {
            int ta = topicIndex.getOrDefault(a.getTopic(), 9999);
            int tb = topicIndex.getOrDefault(b.getTopic(), 9999);
            if (ta != tb) return Integer.compare(ta, tb);
            int oa = a.getOrder() == 0 ? 9999 : a.getOrder();
            int ob = b.getOrder() == 0 ? 9999 : b.getOrder();
            if (oa != ob) return Integer.compare(oa, ob);
            return a.getId().compareTo(b.getId());
        });
        return ordered;
    }

    /**
     * Goi y toi da 6 bai: uu tien bai chua lam cua cac chu de dang yeu (moi chu de 2 bai),
     * con lai la cac bai chua lam ke tiep theo lo trinh.
     */
    public List<Recommendation> recommend(List<Submission> submissions) {
        Set<String> solved = solvedIds(submissions);
        List<Problem> ordered = problemsInPathOrder();
        List<Recommendation> result = new ArrayList<>();
        Set<String> picked = new HashSet<>();

        for (TopicStat stat : topicStats(submissions)) {
            if (!stat.isWeak()) continue;
            int taken = 0;
            for (Problem p : ordered) {
                if (taken == 2 || result.size() == 4) break;
                if (!p.getTopic().equals(stat.getTopic().getId()) || solved.contains(p.getId())) continue;
                result.add(new Recommendation(p, "Củng cố chủ đề đang yếu: " + stat.getTopic().getName()));
                picked.add(p.getId());
                taken++;
            }
        }
        for (Problem p : ordered) {
            if (result.size() == 6) break;
            if (solved.contains(p.getId()) || picked.contains(p.getId())) continue;
            result.add(new Recommendation(p, "Bài kế tiếp trong lộ trình"));
        }
        return result;
    }

    // --------------------------------------------- thanh tich (Module 38)

    /** Diem thuong: moi bai da giai duoc 10 / 20 / 40 diem theo do kho De / Vua / Kho. */
    public int points(Set<String> solvedIds) {
        int points = 0;
        for (Problem p : judge.getSharedProblems()) {      // bai kho rieng khong cong diem thuong
            if (!solvedIds.contains(p.getId())) continue;
            if (p.getDifficulty() == 3) points += 40;
            else if (p.getDifficulty() == 2) points += 20;
            else points += 10;
        }
        return points;
    }

    public String rankName(int points) {
        if (points >= 4000) return "Kim cương";
        if (points >= 1500) return "Vàng";
        if (points >= 500) return "Bạc";
        if (points >= 100) return "Đồng";
        return "Tập sự";
    }

    public List<Badge> badges(List<Submission> submissions) {
        // Huy hieu theo so bai chi tinh bai kho chung, cung quy tac voi bang xep hang.
        Set<String> solved = new HashSet<>();
        for (Submission s : submissions) {
            if (isAccepted(s) && s.getProblem().isShared()) solved.add(s.getProblem().getId());
        }
        Set<String> acLanguages = new HashSet<>();
        Set<String> triedBefore = new HashSet<>();
        boolean firstTry = false;
        boolean hardSolved = false;
        // Duyet tu bai nop cu nhat de biet lan nop dau tien cua moi bai co AC khong.
        for (int i = submissions.size() - 1; i >= 0; i--) {
            Submission s = submissions.get(i);
            String problemId = s.getProblem().getId();
            if (isAccepted(s)) {
                acLanguages.add(s.getLanguage().getName());
                if (!triedBefore.contains(problemId)) firstTry = true;
                if (s.getProblem().getDifficulty() == 3) hardSolved = true;
            }
            triedBefore.add(problemId);
        }
        boolean topicDone = false;
        for (TopicStat stat : topicStats(submissions)) {
            if (stat.getTotal() > 0 && stat.getSolved() == stat.getTotal()) topicDone = true;
        }

        List<Badge> badges = new ArrayList<>();
        badges.add(new Badge("Bước đầu tiên", "Giải được bài đầu tiên", solved.size() >= 1));
        badges.add(new Badge("Mười bài", "Giải được 10 bài khác nhau", solved.size() >= 10));
        badges.add(new Badge("Năm mươi bài", "Giải được 50 bài khác nhau", solved.size() >= 50));
        badges.add(new Badge("Một phát ăn ngay", "AC ngay ở lần nộp đầu tiên của một bài", firstTry));
        badges.add(new Badge("Chinh phục bài khó", "Giải được một bài mức Khó", hardSolved));
        badges.add(new Badge("Đa ngôn ngữ", "AC bằng ít nhất 2 ngôn ngữ lập trình", acLanguages.size() >= 2));
        badges.add(new Badge("Chăm chỉ", "Nộp bài 3 ngày liên tiếp", streakDays(submissions) >= 3));
        badges.add(new Badge("Trọn vẹn chủ đề", "Giải hết mọi bài của một chủ đề", topicDone));
        return badges;
    }
}
