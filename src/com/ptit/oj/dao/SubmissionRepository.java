package com.ptit.oj.dao;

import com.ptit.oj.model.Submission;

import java.util.List;

/**
 * Kho lich su nop bai. Ngoai CRUD co ban con co ham luu ca ket qua tung test
 * trong MOT giao dich - de khong bao gio ton tai bai nop thieu mat ket qua test.
 */
public interface SubmissionRepository extends Repository<Submission> {

    /**
     * Luu bai nop cung toan bo TestCaseResult trong mot transaction.
     * Neu bat ky buoc nao that bai thi rollback, khong de lai du lieu do dang.
     */
    void saveWithResults(Submission submission);

    /** Moi nhat truoc. */
    List<Submission> findAllNewestFirst();

    /** Lich su cua rieng mot nguoi, moi nhat truoc. */
    List<Submission> findByUserId(String userId);
}
