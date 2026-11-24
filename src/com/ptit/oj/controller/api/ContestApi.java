package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.ContestService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.ProctorEvent;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module 21 (cuoc thi) va 22 (xem lai cuoc thi):
 *   GET  /api/contests                    - danh sach
 *   POST /api/contests                    - giang vien / quan tri tao cuoc thi
 *   GET  /api/contests/{id}?minute=N      - chi tiet + bang xep hang tai phut N (bo trong = hien tai)
 *   GET  /api/contests/{id}/events        - dien bien nop bai, dung cho man hinh xem lai
 * Chi nguoi tao cuoc thi (hoac quan tri vien) duoc sua cuoc thi cua minh:
 *   POST   /api/contests/{id}/problems          - them mot bai (kho chung hoac kho cua minh)
 *   DELETE /api/contests/{id}/problems/{maBai}  - bo mot bai
 *   DELETE /api/contests/{id}                   - xoa cuoc thi
 */
public class ContestApi extends ApiModule {

    private final ContestService contests;

    public ContestApi(Platform platform) {
        super(platform);
        this.contests = platform.getContests();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "contests")) {
            User user = req.user();
            List<String> items = new ArrayList<>();
            for (Contest c : contests.getContests()) items.add(contestJson(c, user).build());
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "contests")) {
            User user = req.staff();
            Json.Value body = req.body();
            Contest created = contests.create(user, body.getString("title"), body.getString("description"),
                    parseTime(body.getString("startAt")), (int) body.getLong("durationMin", 120),
                    problemIds(body, "problemIds", user));
            req.send(contestJson(created, user).build());
            return true;
        }
        if (req.is("DELETE", "contests", "*")) {
            contests.delete(req.staff(), req.arg(0));
            req.sendOk();
            return true;
        }
        if (req.is("POST", "contests", "*", "problems")) {
            User user = req.staff();
            Contest c = contests.find(req.arg(0));
            String problemId = req.body().getString("problemId", "");
            Problem problem = judge.findProblem(problemId)
                    .orElseThrow(() -> ApiException.badRequest("Không có bài " + problemId));
            contests.addProblem(user, c, problem);
            req.send(contestJson(c, user).build());
            return true;
        }
        if (req.is("DELETE", "contests", "*", "problems", "*")) {
            User user = req.staff();
            Contest c = contests.find(req.arg(0));
            contests.removeProblem(user, c, req.arg(1).toUpperCase());
            req.send(contestJson(c, user).build());
            return true;
        }
        if (req.is("GET", "contests", "*")) {
            User user = req.user();
            Contest c = contests.find(req.arg(0));
            int minute = req.queryInt("minute", c.getDurationMin());
            req.send(contestJson(c, user)
                    .put("minute", minute)
                    .putRaw("standings", standingsJson(c, minute))
                    .build());
            return true;
        }
        // Trinh duyet cua thi sinh bao mot su kien giam sat (roi tab, sao chep, dan...).
        if (req.is("POST", "contests", "*", "proctor")) {
            User user = req.user();
            Contest c = contests.find(req.arg(0));
            Json.Value body = req.body();
            ProctorEvent event = contests.recordProctorEvent(c, user, body.getString("type"),
                    body.getString("detail", ""), body.getString("problemId", ""));
            if (event != null && event.isViolation()) notifyOwner(c, user);
            req.sendOk();
            return true;
        }
        // Nguoi tao ky thi (va quan tri vien) xem nhat ky giam sat.
        if (req.is("GET", "contests", "*", "proctor")) {
            User user = req.user();
            Contest c = contests.find(req.arg(0));
            if (!contests.canManage(user, c)) {
                throw ApiException.forbidden("Chỉ người tạo kỳ thi hoặc quản trị viên mới xem được nhật ký giám sát");
            }
            req.send(proctorJson(c));
            return true;
        }
        if (req.is("GET", "contests", "*", "events")) {
            req.user();
            Contest c = contests.find(req.arg(0));
            List<String> events = new ArrayList<>();
            for (Submission s : contests.submissionsOf(c)) {
                events.add(Json.obj()
                        .put("minute", contests.minuteOf(c, s))
                        .put("username", s.getAuthor().getUsername())
                        .put("fullName", s.getAuthor().getFullName())
                        .put("problemId", s.getProblem().getId())
                        .put("verdict", s.getResult().getOverallVerdict().getCode())
                        .build());
            }
            req.send(Json.array(events));
            return true;
        }
        return false;
    }

    /**
     * Bao cho nguoi tao ky thi khi mot thi sinh co vi pham: lan dau tien, roi cu moi 10 lan mot lan,
     * de khong lam ngap hop thong bao. Chi tiet tung su kien xem o trang ky thi.
     */
    private void notifyOwner(Contest c, User student) {
        int count = contests.violationCount(c, student.getId());
        if (count != 1 && count % 10 != 0) return;
        User owner = judge.getUsers().stream()
                .filter(u -> u.getUsername().equals(c.getOwnerUsername())).findFirst().orElse(null);
        if (owner == null) return;
        platform.getCommunity().notifyUser(owner.getId(), "Giám sát kỳ thi: " + student.getFullName(),
                student.getFullName() + " (@" + student.getUsername() + ") có " + count
                        + " lần rời tab / sao chép / dán trong kỳ thi " + c.getTitle()
                        + ". Mở kỳ thi để xem nhật ký giám sát.",
                "Contests");
    }

    /** Nhat ky giam sat: bang tong hop theo thi sinh (nhieu vi pham nhat truoc) va 300 su kien gan nhat. */
    private String proctorJson(Contest c) {
        Map<String, User> users = usersById();
        List<ProctorEvent> all = contests.proctorEventsOf(c);

        Map<String, Map<String, Integer>> countsByUser = new LinkedHashMap<>();
        Map<String, ProctorEvent> lastByUser = new LinkedHashMap<>();
        for (ProctorEvent e : all) {
            countsByUser.computeIfAbsent(e.getUserId(), k -> new LinkedHashMap<>()).merge(e.getType(), 1, Integer::sum);
            lastByUser.put(e.getUserId(), e);
        }
        List<String> userIds = new ArrayList<>(countsByUser.keySet());
        userIds.sort((a, b) -> Integer.compare(contests.violationCount(c, b), contests.violationCount(c, a)));

        List<String> students = new ArrayList<>();
        for (String userId : userIds) {
            User u = users.get(userId);
            if (u == null) continue;
            Map<String, Integer> counts = countsByUser.get(userId);
            students.add(Json.obj()
                    .put("username", u.getUsername())
                    .put("fullName", u.getFullName())
                    .put("violations", contests.violationCount(c, userId))
                    .put("tabHidden", counts.getOrDefault(ProctorEvent.TAB_HIDDEN, 0).intValue())
                    .put("windowBlur", counts.getOrDefault(ProctorEvent.WINDOW_BLUR, 0).intValue())
                    .put("copy", counts.getOrDefault(ProctorEvent.COPY, 0) + counts.getOrDefault(ProctorEvent.CUT, 0))
                    .put("paste", counts.getOrDefault(ProctorEvent.PASTE, 0).intValue())
                    .put("pageClosed", counts.getOrDefault(ProctorEvent.PAGE_CLOSED, 0).intValue())
                    .put("lastAt", time(lastByUser.get(userId).getAt()))
                    .build());
        }

        List<String> events = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && events.size() < 300; i--) {
            ProctorEvent e = all.get(i);
            User u = users.get(e.getUserId());
            events.add(Json.obj()
                    .put("id", e.getId())
                    .put("at", time(e.getAt()))
                    .put("username", u == null ? "" : u.getUsername())
                    .put("fullName", u == null ? "(đã xoá)" : u.getFullName())
                    .put("type", e.getType())
                    .put("detail", e.getDetail())
                    .put("problemId", e.getProblemId())
                    .build());
        }
        return Json.obj()
                .put("total", all.size())
                .putRaw("students", Json.array(students))
                .putRaw("events", Json.array(events))
                .build();
    }

    private Json.Obj contestJson(Contest c, User viewer) {
        // Truoc gio thi chi nguoi quan ly ky thi thay danh sach bai; nguoi khac chi biet so bai.
        boolean hidden = "UPCOMING".equals(c.getStatus()) && !contests.canManage(viewer, c);
        return Json.obj()
                .put("id", c.getId())
                .put("ownerName", c.getOwnerName())
                .put("canManage", contests.canManage(viewer, c))
                .put("title", c.getTitle())
                .put("description", c.getDescription())
                .put("startAt", time(c.getStartAt()))
                .put("endAt", time(c.getEndAt()))
                .put("durationMin", c.getDurationMin())
                .put("status", c.getStatus())
                .put("problemCount", c.getProblemIds().size())
                .put("problemsHidden", hidden)
                .putRaw("problems", hidden ? "[]" : problemsJson(c.getProblemIds()));
    }

    private String standingsJson(Contest c, int minute) {
        List<String> rows = new ArrayList<>();
        int rank = 1;
        for (ContestService.Row row : contests.standings(c, minute)) {
            List<String> cells = new ArrayList<>();
            for (String problemId : c.getProblemIds()) {
                Json.Obj cell = Json.obj().put("problemId", problemId)
                        .put("wrong", row.getWrong().getOrDefault(problemId, 0));
                Long solvedAt = row.getSolvedAt().get(problemId);
                if (solvedAt == null) cell.putNull("solvedAt");
                else cell.put("solvedAt", solvedAt.longValue());
                cells.add(cell.build());
            }
            rows.add(Json.obj()
                    .put("rank", rank++)
                    .put("username", row.getUser().getUsername())
                    .put("fullName", row.getUser().getFullName())
                    .put("solved", row.getSolved())
                    .put("penalty", row.getPenalty())
                    .putRaw("cells", Json.array(cells))
                    .build());
        }
        return Json.array(rows);
    }
}
