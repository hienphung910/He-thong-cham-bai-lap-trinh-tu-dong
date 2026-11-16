package com.ptit.oj.judge.core;

import com.ptit.oj.judge.model.JudgeResult;
import com.ptit.oj.judge.model.Submission;
import com.ptit.oj.judge.model.TestCaseResult;

/**
 * OBSERVER PATTERN: theo doi tien trinh cham bai.
 * Judge khong biet ai dang lang nghe -> co the gan console log, ghi file, gui thong bao...
 * Tat ca method deu la default => lop con chi ghi de nhung gi minh can.
 */
public interface JudgeListener {

    default void onJudgeStarted(Submission submission) { }

    default void onCompiled(Submission submission, boolean success, String message) { }

    default void onTestCaseFinished(Submission submission, int index, int total, TestCaseResult result) { }

    default void onJudgeFinished(Submission submission, JudgeResult result) { }
}
