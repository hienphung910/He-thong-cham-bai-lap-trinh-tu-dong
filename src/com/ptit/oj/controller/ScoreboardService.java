package com.ptit.oj.controller;

import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tinh bang xep hang: moi nguoi lay diem CAO NHAT tren tung bai, roi cong lai.
 * Tang lop service chi lam nghiep vu, khong lam I/O va khong lam cham bai.
 */
public class ScoreboardService {

    private Repository<Submission> submissions;
    private Repository<Problem> problems;

    public ScoreboardService(Repository<Submission> submissions, Repository<Problem> problems) {
        this.submissions = submissions;
        this.problems = problems;
    }

    /** Mot dong trong bang xep hang. */
    public static class Row {
        private User user;
        private Map<String, Double> bestPerProblem;
        private int attempts;
        private int solved;
        private double total;

        Row(User user, Map<String, Double> bestPerProblem, int attempts, int solved, double total) {
            this.user = user;
            this.bestPerProblem = bestPerProblem;
            this.attempts = attempts;
            this.solved = solved;
            this.total = total;
        }

        public User getUser() { return user; }
        public Map<String, Double> getBestPerProblem() { return bestPerProblem; }
        public int getAttempts() { return attempts; }
        public int getSolved() { return solved; }
        public double getTotal() { return total; }
    }

    /**
     * Sai so cho phep khi so sanh "da dat diem toi da chua".
     *
     * Vi sao khong dung 1e-9: diem tung test luu trong MySQL o kieu DECIMAL(12,4),
     * nen mot bai 100 diem chia cho 3 test se thanh 3 x 33.3333 = 99.9999 khi doc
     * lai - lech 0.0001 so voi 100. Voi sai so 1e-9 thi bai da giai tron ven lai bi
     * dem thanh CHUA giai. Moi test lech toi da 0.00005, gioi han 200 test/bai
     * (xem ProblemAdminService.MAX_TESTS) nen 0.01 la nguong an toan va vẫn du chat
     * de khong nham mot bai thieu diem thanh dat diem toi da.
     */
    private static final double SCORE_EPSILON = 0.01;

    public List<Row> buildScoreboard() {
        Map<User, Map<String, Double>> best = new LinkedHashMap<>();
        Map<User, Integer> attempts = new HashMap<>();

        for (Submission s : submissions.findAll()) {
            if (!s.isJudged()) continue;
            User u = s.getAuthor();
            if (!best.containsKey(u)) {             // lan dau gap nguoi nay
                best.put(u, new LinkedHashMap<>());
                attempts.put(u, 0);
            }
            attempts.put(u, attempts.get(u) + 1);

            String pid = s.getProblem().getId();
            double score = s.getResult().getScore();
            Double current = best.get(u).get(pid);
            if (current == null || score > current) {
                best.get(u).put(pid, score);
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<User, Map<String, Double>> e : best.entrySet()) {
            double total = 0;
            int solved = 0;
            for (Map.Entry<String, Double> p : e.getValue().entrySet()) {
                total += p.getValue();
                Problem problem = problems.findById(p.getKey());
                if (problem != null && p.getValue() >= problem.getMaxPoints() - SCORE_EPSILON) solved++;
            }
            rows.add(new Row(e.getKey(), e.getValue(), attempts.get(e.getKey()), solved, total));
        }

        // Tong diem giam dan; bang diem thi it lan nop hon xep truoc; van bang thi theo ten dang nhap.
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                if (a.getTotal() != b.getTotal()) {
                    return Double.compare(b.getTotal(), a.getTotal());
                }
                if (a.getAttempts() != b.getAttempts()) {
                    return Integer.compare(a.getAttempts(), b.getAttempts());
                }
                return a.getUser().getUsername().compareTo(b.getUser().getUsername());
            }
        });
        return rows;
    }
}
