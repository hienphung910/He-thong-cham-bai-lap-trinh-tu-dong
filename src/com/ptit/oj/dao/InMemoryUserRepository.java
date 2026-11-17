package com.ptit.oj.dao;

import com.ptit.oj.model.Credentials;
import com.ptit.oj.model.User;
import com.ptit.oj.model.Verdict;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Kho tai khoan trong bo nho - dung cho che do --no-db: khong can MySQL,
 * du lieu mat khi tat chuong trinh.
 */
public class InMemoryUserRepository extends InMemoryRepository<User> implements UserRepository {

    private Map<String, Credentials> credentials = new LinkedHashMap<>();
    private SubmissionRepository submissions;   // de tinh solvedCount

    /** Gan sau vi hai kho tham chieu vong nhau (user can dem AC tu submission). */
    public void setSubmissionRepository(SubmissionRepository submissions) {
        this.submissions = submissions;
    }

    @Override
    public User findByUsername(String username) {
        if (username == null) return null;
        String needle = username.trim();
        for (User u : findAll()) {
            if (u.getUsername().equalsIgnoreCase(needle)) return u;
        }
        return null;
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    @Override
    public void saveWithCredentials(User user, Credentials creds) {
        save(user);
        credentials.put(user.getId(), creds);
    }

    @Override
    public Credentials findCredentials(String userId) {
        return credentials.get(userId);
    }

    @Override
    public Map<String, Integer> solvedCountByUser() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (User u : findAll()) {
            result.put(u.getId(), solvedProblemIds(u.getId()).size());
        }
        return result;
    }

    /** Tap ma bai da tung AC - dung Set nen nop AC nhieu lan cung chi tinh mot. */
    private Set<String> solvedProblemIds(String userId) {
        Set<String> solved = new LinkedHashSet<>();
        if (submissions == null || userId == null) return solved;
        for (com.ptit.oj.model.Submission s : submissions.findAll()) {
            if (!userId.equals(s.getAuthor().getId())) continue;
            if (s.isJudged() && s.getResult().getOverallVerdict() == Verdict.AC) {
                solved.add(s.getProblem().getId());
            }
        }
        return solved;
    }

    @Override
    public boolean deleteById(String id) {
        credentials.remove(id);
        return super.deleteById(id);
    }
}
