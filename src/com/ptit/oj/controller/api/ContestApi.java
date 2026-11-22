package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.ContestService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    private Json.Obj contestJson(Contest c, User viewer) {
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
                .putRaw("problems", problemsJson(c.getProblemIds()));
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
