package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.ProctorEvent;
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
 * Xep hang theo so bai giai duoc. Bang nhau thi ai giai xong bai cuoi cung cua minh som hon
 * dung truoc. Nop sai khong bi tru diem hay cong phut phat.
 */
public class ContestService {

    private final Repository<Contest> contests = new InMemoryRepository<>();
    private final JudgeService judge;

    public ContestService(JudgeService judge) {
        this.judge = judge;
    }

    /** Mot dong cua bang xep hang. */
    public static class Row {
        private final User user;
        private int solved;
        /** Phut AC cua bai giai duoc gan nhat - chi dung de phan dinh khi hai nguoi cung so bai. */
        private long lastSolvedMinute;
        /** Ma bai -> phut AC (tinh tu luc bat dau thi). */
        private final Map<String, Long> solvedAt = new LinkedHashMap<>();
        /** Ma bai -> so lan nop sai. */
        private final Map<String, Integer> wrong = new LinkedHashMap<>();

        Row(User user) { this.user = user; }

        void record(String problemId, Verdict verdict, long minute) {
            if (solvedAt.containsKey(problemId)) return;          // da AC roi thi cac lan nop sau khong tinh
            if (verdict == Verdict.CE || verdict == Verdict.IE) return;   // loi bien dich khong tinh la mot lan nop sai
            if (verdict.isAccepted()) {
                solvedAt.put(problemId, minute);
                solved++;
                lastSolvedMinute = Math.max(lastSolvedMinute, minute);
            } else {
                wrong.merge(problemId, 1, Integer::sum);
            }
        }

        public User getUser() { return user; }
        public int getSolved() { return solved; }
        public long getLastSolvedMinute() { return lastSolvedMinute; }
        public Map<String, Long> getSolvedAt() { return solvedAt; }
        public Map<String, Integer> getWrong() { return wrong; }
    }

    public synchronized Contest create(User owner, String title, String description, LocalDateTime startAt,
                                       int durationMin, List<String> problemIds, boolean proctored,
                                       String classroomId) {
        if (startAt == null) throw ApiException.badRequest("Thiếu thời gian bắt đầu");
        if (durationMin < 5 || durationMin > 7 * 24 * 60) {
            throw ApiException.badRequest("Thời lượng phải từ 5 phút đến 7 ngày");
        }
        if (problemIds.isEmpty()) throw ApiException.badRequest("Cuộc thi phải có ít nhất một bài");
        Contest contest = new Contest(IdGenerator.next("CON"), Check.text(title, "Tên cuộc thi", 150),
                Check.optional(description, "Mô tả", 2000), startAt, durationMin, problemIds,
                owner.getUsername(), owner.getFullName(), classroomId);
        contest.setProctored(proctored);
        contests.save(contest);
        return contest;
    }

    /** Nguoi tao ky thi bat / tat giam sat, ke ca khi ky thi dang dien ra. */
    public synchronized void setProctored(User actor, Contest contest, boolean on) {
        requireManage(actor, contest);
        contest.setProctored(on);
    }

    // --------------------------------------------------- thi sinh vao thi, nop bai som

    /** Hoc sinh bam "Vao thi": tu day giao dien cua hoc sinh chi con man hinh ky thi. */
    public synchronized void join(Contest contest, User student) {
        if (!User.ROLE_STUDENT.equals(student.getRoleCode())) {
            throw ApiException.badRequest("Chỉ tài khoản học sinh mới vào thi");
        }
        if (!"RUNNING".equals(contest.getStatus())) {
            throw ApiException.badRequest("Kỳ thi chưa bắt đầu hoặc đã kết thúc");
        }
        if (contest.hasFinished(student.getId())) {
            throw ApiException.badRequest("Bạn đã nộp bài và kết thúc kỳ thi này rồi");
        }
        contest.getJoinedAt().putIfAbsent(student.getId(), LocalDateTime.now());
    }

    /** Hoc sinh nop bai som: ket thuc bai thi cua minh truoc gio. Bai nop sau do khong con duoc tinh. */
    public synchronized void finish(Contest contest, User student) {
        if (!contest.hasJoined(student.getId())) throw ApiException.badRequest("Bạn chưa vào kỳ thi này");
        contest.getFinishedAt().putIfAbsent(student.getId(), LocalDateTime.now());
    }

    // ------------------------------------------- sua cuoc thi cua chinh minh

    /** Nguoi tao cuoc thi va quan tri vien moi duoc them / bot bai, xoa cuoc thi. */
    public boolean canManage(User user, Contest contest) {
        return User.ROLE_ADMIN.equals(user.getRoleCode())
                || contest.getOwnerUsername().equals(user.getUsername());
    }

    private void requireManage(User user, Contest contest) {
        if (!canManage(user, contest)) {
            throw ApiException.forbidden("Chỉ người tạo kỳ thi hoặc quản trị viên mới sửa được kỳ thi này");
        }
    }

    /** Them mot bai vao cuoc thi. Bai phai la bai kho chung hoac bai trong kho cua nguoi them. */
    public synchronized void addProblem(User actor, Contest contest, Problem problem) {
        requireManage(actor, contest);
        if (!actor.canUseProblem(problem)) {
            throw ApiException.forbidden("Bài " + problem.getId() + " nằm trong kho riêng của giảng viên khác");
        }
        if (contest.getProblemIds().contains(problem.getId())) {
            throw ApiException.badRequest("Kỳ thi đã có bài " + problem.getId());
        }
        contest.getProblemIds().add(problem.getId());
    }

    public synchronized void removeProblem(User actor, Contest contest, String problemId) {
        requireManage(actor, contest);
        if (!contest.getProblemIds().contains(problemId)) {
            throw ApiException.notFound("Kỳ thi không có bài " + problemId);
        }
        if (contest.getProblemIds().size() == 1) {
            throw ApiException.badRequest("Kỳ thi phải còn ít nhất một bài");
        }
        contest.getProblemIds().remove(problemId);
    }

    /** Cuoc thi moi nhat dung truoc. */
    // ------------------------------------------------------- giam sat ky thi

    /** Moi ky thi giu toi da chung nay su kien, moi thi sinh toi da chung nay - de bo nho khong tang mai. */
    private static final int MAX_PROCTOR_EVENTS_PER_CONTEST = 5000;
    private static final int MAX_PROCTOR_EVENTS_PER_USER = 600;

    /** contestId -> cac su kien giam sat, cu truoc moi sau. */
    private final Map<String, List<ProctorEvent>> proctorEvents = new LinkedHashMap<>();

    /**
     * Ghi mot su kien giam sat do trinh duyet cua thi sinh gui len.
     * Tra ve su kien vua ghi, hoac null neu khong ghi (ky thi khong dang dien ra, nguoi gui la
     * nguoi quan ly ky thi, hoac thi sinh da cham gioi han) - nguoi goi khong can bao loi.
     */
    public synchronized ProctorEvent recordProctorEvent(Contest contest, User user, String type,
                                                        String detail, String problemId) {
        if (!ProctorEvent.isKnownType(type)) throw ApiException.badRequest("Loại sự kiện giám sát không hợp lệ");
        // Chi ghi khi ky thi bat giam sat va thi sinh dang trong bai thi (da vao, chua nop bai som).
        if (!contest.isProctored() || !contest.isSitting(user.getId())) return null;
        if (!User.ROLE_STUDENT.equals(user.getRoleCode())) return null;

        List<ProctorEvent> events = proctorEvents.computeIfAbsent(contest.getId(), k -> new ArrayList<>());
        int mine = 0;
        for (ProctorEvent e : events) {
            if (e.getUserId().equals(user.getId())) mine++;
        }
        if (mine >= MAX_PROCTOR_EVENTS_PER_USER) return null;

        String cleanDetail = detail == null ? "" : detail.trim();
        if (cleanDetail.length() > 200) cleanDetail = cleanDetail.substring(0, 200);
        String cleanProblem = problemId == null ? "" : problemId.trim().toUpperCase();
        if (!contest.getProblemIds().contains(cleanProblem)) cleanProblem = "";

        ProctorEvent event = new ProctorEvent(IdGenerator.next("PRC"), contest.getId(), user.getId(),
                type, cleanDetail, cleanProblem);
        events.add(event);
        if (events.size() > MAX_PROCTOR_EVENTS_PER_CONTEST) events.remove(0);
        return event;
    }

    /** Toan bo su kien giam sat cua mot ky thi, cu truoc moi sau (ban sao, doc an toan). */
    public synchronized List<ProctorEvent> proctorEventsOf(Contest contest) {
        List<ProctorEvent> events = proctorEvents.get(contest.getId());
        return events == null ? new ArrayList<>() : new ArrayList<>(events);
    }

    /** So lan vi pham (khong tinh su kien "quay lai") cua mot thi sinh trong ky thi. */
    public synchronized int violationCount(Contest contest, String userId) {
        int count = 0;
        List<ProctorEvent> events = proctorEvents.get(contest.getId());
        if (events == null) return 0;
        for (ProctorEvent e : events) {
            if (e.getUserId().equals(userId) && e.isViolation()) count++;
        }
        return count;
    }

    public List<Contest> getContests() {
        List<Contest> all = contests.findAll();
        all.sort((a, b) -> b.getStartAt().compareTo(a.getStartAt()));
        return all;
    }

    public Contest find(String id) {
        return contests.findById(id).orElseThrow(() -> ApiException.notFound("Không có cuộc thi " + id));
    }

    /** Xoa moi ky thi cua mot lop (goi khi lop bi xoa). */
    public synchronized void deleteByClassroom(String classroomId) {
        for (Contest c : contests.findAll()) {
            if (classroomId.equals(c.getClassroomId())) {
                contests.deleteById(c.getId());
                proctorEvents.remove(c.getId());
            }
        }
    }

    public synchronized void delete(User actor, String id) {
        requireManage(actor, find(id));
        contests.deleteById(id);
        proctorEvents.remove(id);
    }

    /** Cac bai nop thuoc cuoc thi (dung bai, dung khung gio), cu nhat dung truoc. */
    public List<Submission> submissionsOf(Contest contest) {
        List<Submission> result = new ArrayList<>();
        for (Submission s : judge.getSubmissionsNewestFirst()) {
            LocalDateTime at = s.getSubmittedAt();
            boolean inTime = !at.isBefore(contest.getStartAt()) && at.isBefore(contest.getEndAt());
            // Chi tinh bai cua thi sinh da bam "Vao thi", nop sau luc vao: hoc sinh lop khac (hoac
            // chua vao thi) nop dung bai do o lo trinh chung thi khong len bang xep hang cua ky thi.
            LocalDateTime joined = contest.getJoinedAt().get(s.getAuthor().getId());
            if (joined == null || at.isBefore(joined)) inTime = false;
            // Thi sinh da nop bai som thi cac bai nop sau thoi diem do khong tinh vao ky thi.
            LocalDateTime finished = contest.getFinishedAt().get(s.getAuthor().getId());
            if (finished != null && at.isAfter(finished)) inTime = false;
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
            return Long.compare(a.lastSolvedMinute, b.lastSolvedMinute);
        });
        return sorted;
    }
}
