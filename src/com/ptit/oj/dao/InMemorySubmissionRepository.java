package com.ptit.oj.dao;

import com.ptit.oj.model.Submission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kho lich su nop bai trong bo nho - dung cho che do --no-db.
 * Khong co transaction that vi tat ca nam trong mot cau truc du lieu duy nhat.
 */
public class InMemorySubmissionRepository extends InMemoryRepository<Submission>
        implements SubmissionRepository {

    @Override
    public void saveWithResults(Submission submission) {
        save(submission);
    }

    @Override
    public List<Submission> findAllNewestFirst() {
        List<Submission> all = findAll();
        Collections.reverse(all);
        return all;
    }

    @Override
    public List<Submission> findByUserId(String userId) {
        List<Submission> mine = new ArrayList<>();
        for (Submission s : findAllNewestFirst()) {
            if (s.getAuthor().getId().equals(userId)) mine.add(s);
        }
        return mine;
    }
}
