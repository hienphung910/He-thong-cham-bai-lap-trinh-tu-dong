package com.ptit.oj.controller.api;

import com.ptit.oj.controller.AnalyticsService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 23-26 (phan tich, ky nang, diem yeu, goi y), 33 (bao cao) va 38 (thanh tich):
 *   GET /api/analytics/me                 - ho so hoc tap cua chinh minh
 *   GET /api/analytics/users/{username}   - cua mot hoc sinh bat ky (giang vien, quan tri)
 *   GET /api/dashboard                    - so lieu toan he thong (giang vien, quan tri)
 *   GET /api/gamification/leaderboard     - bang diem thuong
 */
public class AnalyticsApi extends ApiModule {

    private final AnalyticsService analytics;

    public AnalyticsApi(Platform platform) {
        super(platform);
        this.analytics = platform.getAnalytics();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "analytics", "me")) {
            req.send(profileJson(req.user()));
            return true;
        }
        if (req.is("GET", "analytics", "users", "*")) {
            req.staff();
            req.send(profileJson(requireUsername(req.arg(0))));
            return true;
        }
        if (req.is("GET", "dashboard")) {
            User viewer = req.staff();
            req.send(dashboardJson(viewer));
            return true;
        }
        if (req.is("GET", "gamification", "leaderboard")) {
            req.user();
            req.send(leaderboardJson());
            return true;
        }
        return false;
    }

    /** Toan bo so lieu hoc tap cua mot nguoi: 5 trang giao dien cung doc tu day. */
    private String profileJson(User user) {
        List<Submission> submissions = judge.getSubmissionsOf(user);
        Set<String> solved = analytics.solvedIds(submissions);
        Map<String, Integer> verdicts = analytics.verdictCounts(submissions);
        int points = analytics.points(solved);

        List<String> topics = new ArrayList<>();
        for (AnalyticsService.TopicStat t : analytics.topicStats(submissions)) {
            topics.add(Json.obj()
                    .put("id", t.getTopic().getId())
                    .put("name", t.getTopic().getName())
                    .put("group", t.getTopic().getGroup())
                    .put("total", t.getTotal())
                    .put("solved", t.getSolved())
                    .put("attempts", t.getAttempts())
                    .put("failed", t.getFailed())
                    .put("score", t.getScore())
                    .put("level", t.getLevel())
                    .put("weak", t.isWeak())
                    .build());
        }
        List<String> recommendations = new ArrayList<>();
        for (AnalyticsService.Recommendation r : analytics.recommend(submissions)) {
            recommendations.add(Json.obj()
                    .put("problemId", r.getProblem().getId())
                    .put("title", r.getProblem().getTitle())
                    .put("difficulty", r.getProblem().getDifficulty())
                    .put("reason", r.getReason())
                    .build());
        }
        List<String> badges = new ArrayList<>();
        for (AnalyticsService.Badge b : analytics.badges(submissions)) {
            badges.add(Json.obj().put("name", b.getName()).put("description", b.getDescription())
                    .put("earned", b.isEarned()).build());
        }

        return Json.obj()
                .putRaw("user", userJson(user).build())
                .put("totalSubmissions", submissions.size())
                .put("solvedCount", solved.size())
                .put("acRate", submissions.isEmpty() ? 0 : verdicts.get("AC") * 100 / submissions.size())
                .putRaw("verdicts", countsJson(verdicts))
                .putRaw("activity", countsJson(analytics.activityByDay(submissions, 14)))
                .putRaw("topics", Json.array(topics))
                .putRaw("weaknessNotes", stringArray(analytics.weaknessNotes(submissions)))
                .putRaw("recommendations", Json.array(recommendations))
                .put("points", points)
                .put("rankName", analytics.rankName(points))
                .put("streak", analytics.streakDays(submissions))
                .putRaw("badges", Json.array(badges))
                .build();
    }

    /** Doi mot bang dem thanh mang [{key, count}] giu nguyen thu tu. */
    private String countsJson(Map<String, Integer> counts) {
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            items.add(Json.obj().put("key", e.getKey()).put("count", e.getValue().intValue()).build());
        }
        return Json.array(items);
    }

    // ------------------------------------------------- bao cao toan he thong

    private String dashboardJson(User viewer) {
        List<Submission> all = judge.getSubmissionsNewestFirst();
        int students = 0;
        int teachers = 0;
        List<User> users = judge.getUsers();
        for (User u : users) {
            if (isStudent(u)) students++;
            else if (User.ROLE_TEACHER.equals(u.getRoleCode())) teachers++;
        }

        // Dem so lan nop va so lan AC cua tung bai de tim 5 bai duoc nop nhieu nhat.
        Map<String, Integer> attempts = new HashMap<>();
        Map<String, Integer> accepted = new HashMap<>();
        for (Submission s : all) {
            // Chi thong ke bai kho chung: bai kho rieng cua giang vien khac khong lo ma va ten o day.
            if (!s.getProblem().isShared()) continue;
            String id = s.getProblem().getId();
            attempts.merge(id, 1, Integer::sum);
            if (s.isJudged() && s.getResult().getOverallVerdict().isAccepted()) accepted.merge(id, 1, Integer::sum);
        }
        List<String> ids = new ArrayList<>(attempts.keySet());
        Collections.sort(ids, (a, b) -> Integer.compare(attempts.get(b), attempts.get(a)));
        List<String> topProblems = new ArrayList<>();
        for (int i = 0; i < ids.size() && i < 5; i++) {
            String id = ids.get(i);
            topProblems.add(Json.obj().put("id", id)
                    .put("title", judge.findProblem(id).map(Problem::getTitle).orElse(id))
                    .put("attempts", attempts.get(id).intValue())
                    .put("acRate", accepted.getOrDefault(id, 0) * 100 / attempts.get(id))
                    .build());
        }

        return Json.obj()
                .put("users", users.size())
                .put("students", students)
                .put("teachers", teachers)
                .put("problems", judge.getProblems().size())
                .put("submissions", all.size())
                .put("classrooms", platform.getSchool().getClassrooms().size())
                .put("courses", platform.getCourses().getCourses().size())
                .put("contests", platform.getContests().getContests().size())
                // Doanh thu chi quan tri vien xem duoc (giong /api/admin/payments).
                .put("revenue", User.ROLE_ADMIN.equals(viewer.getRoleCode())
                        ? platform.getBilling().totalRevenue() : 0)
                .putRaw("verdicts", countsJson(analytics.verdictCounts(all)))
                .putRaw("activity", countsJson(analytics.activityByDay(all, 14)))
                .putRaw("topProblems", Json.array(topProblems))
                .build();
    }

    // ---------------------------------------------------------- bang diem thuong

    private String leaderboardJson() {
        Map<String, Set<String>> solvedByUser = analytics.sharedSolvedByUser();
        Map<String, Integer> pointsByUser = new HashMap<>();
        List<User> ranked = new ArrayList<>();
        for (User u : judge.getUsers()) {
            Set<String> solved = solvedByUser.get(u.getId());
            if (solved == null) continue;                 // chua giai bai nao thi khong len bang
            pointsByUser.put(u.getId(), analytics.points(solved));
            ranked.add(u);
        }
        Collections.sort(ranked, (a, b) -> Integer.compare(pointsByUser.get(b.getId()), pointsByUser.get(a.getId())));

        List<String> rows = new ArrayList<>();
        int rank = 1;
        for (User u : ranked) {
            int points = pointsByUser.get(u.getId());
            rows.add(Json.obj().put("rank", rank++)
                    .put("username", u.getUsername()).put("fullName", u.getFullName())
                    .put("points", points).put("rankName", analytics.rankName(points))
                    .put("solved", solvedByUser.get(u.getId()).size())
                    .build());
        }
        return Json.array(rows);
    }
}
