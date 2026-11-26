package com.ptit.oj.controller;

import com.ptit.oj.model.Assignment;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bao cao hoc tap cua MOT hoc sinh trong MOT lop, trong mot khoang thoi gian (7 / 30 / 90 ngay...):
 * so lieu chuyen can, ket qua, bai tap giao, ky thi, chu de manh / yeu, va mot doan NHAN XET viet san
 * bang tieng Viet (theo quy tac, khong dung AI) de giao vien sua lai roi gui phu huynh.
 */
public class ReportService {

    private final Platform platform;

    public ReportService(Platform platform) {
        this.platform = platform;
    }

    /** Mot bai tap giao trong bao cao. */
    public static final class AssignmentLine {
        public final Assignment assignment;
        public final int done;
        public final int late;
        public final int total;
        /** DONE (xong dung han) / LATE (xong nhung co bai nop muon) / MISSED (qua han chua xong) / OPEN (chua toi han). */
        public final String state;

        AssignmentLine(Assignment assignment, int done, int late, int total, String state) {
            this.assignment = assignment;
            this.done = done;
            this.late = late;
            this.total = total;
            this.state = state;
        }
    }

    /** Mot ky thi hoc sinh da tham gia. */
    public static final class ContestLine {
        public final Contest contest;
        public final int rank;
        public final int participants;
        public final int solved;

        ContestLine(Contest contest, int rank, int participants, int solved) {
            this.contest = contest;
            this.rank = rank;
            this.participants = participants;
            this.solved = solved;
        }
    }

    public static final class Report {
        public User student;
        public Classroom classroom;
        public int days;
        public LocalDate from;
        public LocalDate to;
        public int submissions;
        public int accepted;
        public int activeDays;
        public int newSolved;
        public int prevNewSolved;
        public int prevSubmissions;
        public int totalSolved;
        public int streak;
        public Map<String, Integer> verdicts = new HashMap<>();
        public List<AssignmentLine> assignments = new ArrayList<>();
        public List<ContestLine> contests = new ArrayList<>();
        public List<String> strongTopics = new ArrayList<>();
        public List<String> weakTopics = new ArrayList<>();
        /** Hang cua hoc sinh trong lop tren tap bai cua lop, va so hoc sinh. 0 neu lop chua co bai. */
        public int classRank;
        public int classSize;
        public int classProblemCount;
        public int classSolved;
        public double classAverageSolved;
        public String comment;

        public int acRate() { return submissions == 0 ? 0 : accepted * 100 / submissions; }
    }

    public Report build(Classroom classroom, User student, int days) {
        AnalyticsService analytics = platform.getAnalytics();
        Report r = new Report();
        r.student = student;
        r.classroom = classroom;
        r.days = days;
        r.to = LocalDate.now();
        r.from = r.to.minusDays(days - 1L);
        LocalDateTime start = r.from.atStartOfDay();
        LocalDateTime prevStart = start.minusDays(days);

        List<Submission> all = platform.getJudge().getSubmissionsOf(student);
        Set<String> solvedBefore = new HashSet<>();
        Set<String> solvedBeforePrev = new HashSet<>();
        Set<String> solvedInPeriod = new LinkedHashSet<>();
        Set<String> solvedInPrev = new HashSet<>();
        Set<LocalDate> active = new HashSet<>();
        List<Submission> inPeriod = new ArrayList<>();
        for (Submission s : all) {
            if (!s.isJudged()) continue;
            LocalDateTime at = s.getSubmittedAt();
            boolean ac = s.getResult().getOverallVerdict().isAccepted();
            String pid = s.getProblem().getId();
            if (ac && at.isBefore(start)) solvedBefore.add(pid);
            if (ac && at.isBefore(prevStart)) solvedBeforePrev.add(pid);
            if (!at.isBefore(start)) {
                inPeriod.add(s);
                r.submissions++;
                if (ac) { r.accepted++; solvedInPeriod.add(pid); }
                active.add(at.toLocalDate());
                r.verdicts.merge(s.getResult().getOverallVerdict().getCode(), 1, Integer::sum);
            } else if (!at.isBefore(prevStart)) {
                r.prevSubmissions++;
                if (ac) solvedInPrev.add(pid);
            }
        }
        solvedInPeriod.removeAll(solvedBefore);
        solvedInPrev.removeAll(solvedBeforePrev);
        r.newSolved = solvedInPeriod.size();
        r.prevNewSolved = solvedInPrev.size();
        r.activeDays = active.size();
        r.totalSolved = analytics.solvedIds(all).size();
        r.streak = analytics.streakDays(all);

        // Bai tap giao cua lop lien quan toi ky bao cao: giao trong ky, het han trong ky, hoac dang mo.
        Map<String, List<LocalDateTime>> acceptedTimes = analytics.acceptedTimesByUser()
                .getOrDefault(student.getId(), Collections.<String, List<LocalDateTime>>emptyMap());
        LocalDateTime now = LocalDateTime.now();
        for (Assignment a : platform.getAssignments().ofClassrooms(Collections.singletonList(classroom))) {
            boolean relevant = !a.getCreatedAt().isBefore(start) || !a.getDueAt().isBefore(start);
            if (!relevant || a.getCreatedAt().isAfter(now)) continue;
            int done = 0;
            int late = 0;
            for (String pid : a.getProblemIds()) {
                String st = a.statusOf(acceptedTimes.get(pid));
                if ("DONE".equals(st)) done++;
                else if ("LATE".equals(st)) late++;
            }
            int total = a.getProblemIds().size();
            String state;
            if (done == total) state = "DONE";
            else if (done + late == total) state = "LATE";
            else if (a.isOverdue()) state = "MISSED";
            else state = "OPEN";
            r.assignments.add(new AssignmentLine(a, done, late, total, state));
        }

        // Ky thi cua lop trong ky ma hoc sinh co vao thi.
        ContestService contests = platform.getContests();
        for (Contest k : contests.getContests()) {
            if (!classroom.getId().equals(k.getClassroomId())) continue;
            if (k.getStartAt().isAfter(now) || k.getEndAt().isBefore(start)) continue;
            if (!k.hasJoined(student.getId())) continue;
            List<ContestService.Row> rows = contests.standings(k, k.getDurationMin());
            int rank = 0;
            int solved = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getUser().getId().equals(student.getId())) {
                    rank = i + 1;
                    solved = rows.get(i).getSolved();
                }
            }
            r.contests.add(new ContestLine(k, rank, Math.max(rows.size(), k.getJoinedAt().size()), solved));
        }

        // Chu de manh / can co gang (tren kho bai chung, toan bo qua trinh).
        // Manh: giai duoc it nhat 2 bai cua chu de va phan lon lan nop deu dung. Lay 3 chu de giai nhieu nhat.
        List<AnalyticsService.TopicStat> byTopic = new ArrayList<>(analytics.topicStats(all));
        byTopic.sort((a, b) -> Integer.compare(b.getSolved(), a.getSolved()));
        for (AnalyticsService.TopicStat t : byTopic) {
            boolean mostlyRight = t.getAttempts() > 0 && (t.getAttempts() - t.getFailed()) * 10 >= t.getAttempts() * 6;
            if (t.getSolved() >= 2 && mostlyRight && r.strongTopics.size() < 3) r.strongTopics.add(t.getTopic().getName());
        }
        for (AnalyticsService.TopicStat t : analytics.topicStats(all)) {
            if ((t.isWeak() || (t.getAttempts() >= 3 && t.getSolved() == 0)) && r.weakTopics.size() < 3) {
                r.weakTopics.add(t.getTopic().getName());
            }
        }

        // So voi ca lop tren tap bai cua lop (bai luyen tap + bai tap giao).
        Set<String> classProblems = new LinkedHashSet<>(classroom.getPracticeProblemIds());
        for (Assignment a : platform.getAssignments().ofClassrooms(Collections.singletonList(classroom))) {
            classProblems.addAll(a.getProblemIds());
        }
        r.classProblemCount = classProblems.size();
        if (!classProblems.isEmpty()) {
            List<User> members = new ArrayList<>();
            for (User u : platform.getJudge().getUsers()) {
                if (classroom.getStudentIds().contains(u.getId())) members.add(u);
            }
            List<ScoreboardService.ClassRow> rows = platform.getJudge().buildClassScoreboard(members, classProblems);
            r.classSize = rows.size();
            int sum = 0;
            for (int i = 0; i < rows.size(); i++) {
                sum += rows.get(i).getAccepted();
                if (rows.get(i).getUser().getId().equals(student.getId())) {
                    r.classRank = i + 1;
                    r.classSolved = rows.get(i).getAccepted();
                }
            }
            r.classAverageSolved = rows.isEmpty() ? 0 : (double) sum / rows.size();
        }

        r.comment = comment(r);
        return r;
    }

    // ------------------------------------------------------------ nhan xet

    /** Ten goi trong nhan xet: chu cuoi cua ho ten ("Nguyễn Minh Hiển" -> "Hiển"). */
    private static String givenName(User u) {
        String[] parts = u.getFullName().trim().split("\\s+");
        return parts[parts.length - 1];
    }

    /** Doan nhan xet viet san cho phu huynh: chuyen can, ket qua, bai tap giao, diem manh / yeu, de xuat. */
    static String comment(Report r) {
        String em = "Em " + givenName(r.student);
        String period = r.days == 7 ? "tuần qua" : r.days == 30 ? "tháng qua" : r.days + " ngày qua";
        List<String> s = new ArrayList<>();

        // 1. Chuyen can
        int pct = r.activeDays * 100 / Math.max(1, r.days);
        if (r.activeDays == 0) {
            s.add(em + " chưa luyện tập trên hệ thống trong " + period + ".");
        } else if (pct >= 60) {
            s.add(em + " học rất đều đặn, luyện tập " + r.activeDays + "/" + r.days + " ngày trong " + period + ".");
        } else if (pct >= 25) {
            s.add(em + " luyện tập khá đều, có " + r.activeDays + " ngày làm bài trong " + period + ".");
        } else {
            s.add(em + " luyện tập chưa đều, chỉ có " + r.activeDays + " ngày làm bài trong " + period + ".");
        }

        // 2. Ket qua va xu huong
        if (r.submissions > 0) {
            String result = "Em đã giải được " + r.newSolved + " bài mới qua " + r.submissions
                    + " lần nộp bài (tỉ lệ đúng " + r.acRate() + "%)";
            if (r.prevSubmissions > 0 || r.prevNewSolved > 0) {
                if (r.newSolved > r.prevNewSolved) result += ", tiến bộ hơn kỳ trước (" + r.prevNewSolved + " bài)";
                else if (r.newSolved < r.prevNewSolved) result += ", ít hơn kỳ trước (" + r.prevNewSolved + " bài)";
                else result += ", tương đương kỳ trước";
            }
            s.add(result + ".");
        }

        // 3. Bai tap giao
        int done = 0;
        int late = 0;
        List<String> missed = new ArrayList<>();
        int closed = 0;
        for (AssignmentLine a : r.assignments) {
            if ("OPEN".equals(a.state)) continue;
            closed++;
            if ("DONE".equals(a.state)) done++;
            else if ("LATE".equals(a.state)) late++;
            else missed.add("\"" + a.assignment.getTitle() + "\"");
        }
        if (closed > 0) {
            if (done == closed) {
                s.add("Em hoàn thành đầy đủ và đúng hạn " + closed + "/" + closed + " bài tập được giao.");
            } else {
                String line = "Về bài tập được giao, em hoàn thành đúng hạn " + done + "/" + closed + " bài";
                if (late > 0) line += ", nộp muộn " + late + " bài";
                if (!missed.isEmpty()) line += ", chưa hoàn thành " + String.join(", ", missed);
                s.add(line + ".");
            }
        }
        long open = r.assignments.stream().filter(a -> "OPEN".equals(a.state) && a.done < a.total).count();
        if (open > 0) s.add("Hiện em còn " + open + " bài tập đang trong hạn cần hoàn thành.");

        // 4. Ky thi
        for (ContestLine c : r.contests) {
            if (c.rank > 0) {
                s.add("Trong kỳ thi \"" + c.contest.getTitle() + "\", em giải được " + c.solved + "/"
                        + c.contest.getProblemIds().size() + " bài, xếp hạng " + c.rank + "/" + c.participants + ".");
            }
        }

        // 5. So voi lop
        if (r.classRank > 0 && r.classSize >= 3) {
            if (r.classRank <= Math.max(1, r.classSize / 4)) {
                s.add("Em thuộc nhóm dẫn đầu lớp (hạng " + r.classRank + "/" + r.classSize + ").");
            } else if (r.classSolved + 0.5 < r.classAverageSolved) {
                s.add("Số bài em đã làm được (" + r.classSolved + ") đang thấp hơn mức trung bình của lớp ("
                        + Math.round(r.classAverageSolved) + " bài).");
            }
        }

        // 6. Diem manh / can co gang, loi hay gap
        if (!r.strongTopics.isEmpty()) s.add("Em làm tốt các phần: " + String.join(", ", r.strongTopics) + ".");
        if (!r.weakTopics.isEmpty()) s.add("Em cần luyện thêm các phần: " + String.join(", ", r.weakTopics) + ".");
        if (r.submissions >= 5) {
            int wa = r.verdicts.getOrDefault("WA", 0) * 100 / r.submissions;
            int ce = r.verdicts.getOrDefault("CE", 0) * 100 / r.submissions;
            int tle = r.verdicts.getOrDefault("TLE", 0) * 100 / r.submissions;
            if (wa >= 40) s.add("Em hay nộp bài khi chưa kiểm tra kỹ các trường hợp đặc biệt, nên tự thử thêm ví dụ trước khi nộp.");
            else if (ce >= 20) s.add("Em còn hay mắc lỗi cú pháp, nên chạy thử chương trình trước khi nộp.");
            else if (tle >= 20) s.add("Một số bài em làm đúng nhưng chạy còn chậm, cần rèn thêm cách giải hiệu quả hơn.");
        }

        // 7. De xuat
        if (r.activeDays == 0 || pct < 25) {
            s.add("Gia đình nên nhắc em dành khoảng 20-30 phút mỗi ngày để luyện tập đều đặn.");
        } else if (!missed.isEmpty() || open > 0) {
            s.add("Em nên chú ý hoàn thành bài tập trước hạn nộp.");
        } else {
            s.add("Em đang có kết quả tốt, hãy tiếp tục duy trì.");
        }
        return String.join(" ", s);
    }
}
