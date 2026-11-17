package com.ptit.oj.view.history;

import com.ptit.oj.model.Submission;
import com.ptit.oj.view.Ui;
import com.ptit.oj.view.judge.JudgeResultView;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Window;

/**
 * Hop thoai chi tiet mot bai nop: ket qua cham (verdict, so test dung, diem, bang
 * tung test), ngon ngu, nguoi nop va ma nguon chi doc. Dong bang nut × cua cua so.
 */
public class SubmissionDetailView extends JDialog {

    private JudgeResultView resultView = new JudgeResultView();
    private JLabel lblInfo = new JLabel(" ");
    private JTextArea txtSource = new JTextArea(12, 70);

    public SubmissionDetailView(Window owner) {
        super(owner, "Chi tiết bài nộp", ModalityType.APPLICATION_MODAL);

        txtSource.setEditable(false);
        txtSource.setFont(Ui.MONO);
        txtSource.setTabSize(4);
        JScrollPane scrSource = new JScrollPane(txtSource);
        scrSource.setBorder(BorderFactory.createTitledBorder("Mã nguồn"));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(resultView, BorderLayout.CENTER);
        top.add(lblInfo, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(scrSource, BorderLayout.CENTER);
        setContentPane(root);

        setSize(860, 680);
        setLocationRelativeTo(owner);
    }

    /** Dua mot bai nop len hop thoai. */
    public void setDetail(Submission submission) {
        setTitle(submission.getProblem().getId() + " — " + submission.getProblem().getTitle());
        resultView.setResult(submission);
        lblInfo.setText("Ngôn ngữ: " + submission.getLanguage().getName()
                + " · người nộp: @" + submission.getAuthor().getUsername());

        String source = submission.getSourceCode();
        if (source == null) {
            source = "";
        }
        txtSource.setText(source);
        txtSource.setCaretPosition(0);
    }
}
