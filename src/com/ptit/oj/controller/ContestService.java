package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.User;
import com.ptit.oj.model.Verdict;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuoc thi lap trinh va xem lai dien bien (Module 21, 22).
 *
 * Bang xep hang KHONG luu rieng: moi lan can thi tinh lai tu cac bai nop nam
 * trong khung gio thi. Nho vay "xem lai" (replay) chi la tinh bang xep hang
 * tai mot phut bat ky trong qua khu.
 *
 * Luat tinh diem kieu ICPC: xep theo so bai giai duoc, bang nhau thi ai it phut
 * phat hon dung truoc. Phut phat cua mot bai = phut AC + 20 phut cho moi lan nop sai truoc do.
 */
public class ContestService {

    private static final int WRONG_PENALTY_MIN = 20;

    private final Repository<Contest> contests = new InMemoryRepository<>();
    private final JudgeService judge;

    public ContestService(JudgeService judge) {
        this.judge = judge;
    }

    /** Mot dong cua bang xep hang. */
    public static class Row {
        private final User user;
        private int solved;
        private long penalty;
        /** Ma bai -> phut AC (tinh tu luc bat dau thi). */
        private final Map<String, Long> solvedAt = new LinkedHashMap<>();
        /** Ma bai -> so lan nop sai. */
        private final Map<String, Integer> wrong = new LinkedHashMap<>();

        Row(User user) { this.user = user; }

        void record(String problemId, Verdict verdict, long minute) {
            if (solvedAt.containsKey(problemId)) return;          // da AC roi thi cac lan nop sau khong tinh
            if (verdict == Verdict.CE || verdict == Verdict.IE) return;   // loi bien dich khong bi phat
            if (verdict.isAccepted()) {
                solvedAt.put(problemId, minute);
                solved++;
                penalty += minute + (long) WRONG_PENALTY_MIN * wrong.getOrDefault(problemId, 0);
            } else {
                wrong.merge(problemId, 1, Integer::sum);
            }
        }

        public User getUser() { return user; }
        public int getSolved() { return solved; }
        public long getPenalty() { return penalty; }
        public Map<String, Long> getSolvedAt() { return solvedAt; }
        public Map<String, Integer> getWrong() { return wrong; }
    }

    public Contest create(String title, String description, LocalDateTime startAt,
                          int durationMin, List<String> problemIds) {
        if (startAt == null) throw ApiException.badRequest("Thiếu thời gian bắt đầu");
        if (durationMin < 5 || durationMin > 7 * 24 * 60) {
            throw ApiException.badRequest("Thời lượng phải từ 5 phút đến 7 ngày");
        }
        if (problemIds.isEmpty()) throw ApiException.badRequest("Cuộc thi phải có ít nhất một bài");
        Contest contest = new Contest(IdGenerator.next("CON"), Check.text(title, "Tên cuộc thi", 150),
                Check.optional(description, "Mô tả", 2000), startAt, durationMin, problemIds);
        contests.save(contest);
        return contest;
    }

    /** Cuoc thi moi nhat dung truoc. */
    public List<Contest> getContests() {
        List<Contest> all = contests.findAll();
        all.sort((a, b) -> b.getStartAt().compareTo(a.getStartAt()));
        return all;
    }

    public Contest find(String id) {
        return contests.findById(id).orElseThrow(() -> ApiException.notFound("Không có cuộc thi " + id));
    }

    public void delete(String id) {
        contests.deleteById(id);
    }

    /** Cac bai nop thuoc cuoc thi (dung bai, dung khung gio), cu nhat dung truoc. */
    public List<Submission> submissionsOf(Contest contest) {
        List<Submission> result = new ArrayList<>();
        for (Submission s : judge.getSubmissionsNewestFirst()) {
            LocalDateTime at = s.getSubmittedAt();
            boolean inTime = !at.isBefore(contest.getStartAt()) && at.isBefore(contest.getEndAt());
            if (inTime && s.isJudged() && contest.getProblemIds().contains(s.getProblem().getId())) {
                result.add(s);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /** Phut thu may cua cuoc thi thi bai nay duoc nop. */
    public long minuteOf(Contest contest, Submission s) {
        return Duration.between(contest.getStartAt(), s.getSubmittedAt()).toMinutes();
    }

    /**
     * Bang xep hang tai phut uptoMinute cua cuoc thi.
     * Truyen durationMin de lay bang cuoi cung; truyen so nho hon de xem lai qua khu.
     */
    public List<Row> standings(Contest contest, long uptoMinute) {
        Map<String, Row> rows = new LinkedHashMap<>();
        for (Submission s : submissionsOf(contest)) {
            long minute = minuteOf(contest, s);
            if (minute > uptoMinute) break;       // danh sach da xep theo thoi gian
            Row row = rows.get(s.getAuthor().getId());
            if (row == null) {
                row = new Row(s.getAuthor());
                rows.put(s.getAuthor().getId(), row);
            }
            row.record(s.getProblem().getId(), s.getResult().getOverallVerdict(), minute);
        }

        List<Row> sorted = new ArrayList<>(rows.values());
        sorted.sort((a, b) -> {
            if (a.solved != b.solved) return Integer.compare(b.solved, a.solved);
            return Long.compare(a.penalty, b.penalty);
        });
        return sorted;
    }
}
