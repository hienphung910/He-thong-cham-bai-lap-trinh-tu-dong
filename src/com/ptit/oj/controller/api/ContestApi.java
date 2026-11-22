package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ContestService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Submission;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 21 (cuoc thi) va 22 (xem lai cuoc thi):
 *   GET  /api/contests                    - danh sach
 *   POST /api/contests                    - giang vien / quan tri tao cuoc thi
 *   GET  /api/contests/{id}?minute=N      - chi tiet + bang xep hang tai phut N (bo trong = hien tai)
 *   GET  /api/contests/{id}/events        - dien bien nop bai, dung cho man hinh xem lai
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
            req.user();
            List<String> items = new ArrayList<>();
            for (Contest c : contests.getContests()) items.add(contestJson(c).build());
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "contests")) {
            req.staff();
            Json.Value body = req.body();
            Contest created = contests.create(body.getString("title"), body.getString("description"),
                    parseTime(body.getString("startAt")), (int) body.getLong("durationMin", 120),
                    problemIds(body, "problemIds"));
            req.send(contestJson(created).build());
            return true;
        }
        if (req.is("DELETE", "contests", "*")) {
            req.staff();
            contests.delete(req.arg(0));
            req.sendOk();
            return true;
        }
        if (req.is("GET", "contests", "*")) {
            req.user();
            Contest c = contests.find(req.arg(0));
            int minute = req.queryInt("minute", c.getDurationMin());
            req.send(contestJson(c)
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

    private Json.Obj contestJson(Contest c) {
        return Json.obj()
                .put("id", c.getId())
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
