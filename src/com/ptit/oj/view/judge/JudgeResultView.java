package com.ptit.oj.view.judge;

import com.ptit.oj.model.JudgeResult;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.TestCaseResult;
import com.ptit.oj.model.Verdict;
import com.ptit.oj.view.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Phan ket qua cham cua mot bai nop: verdict tong, so test dung, diem, thong bao
 * chung (vi du loi bien dich) va bang ket qua tung test. Dung o giao dien Lam bai
 * va trong hop thoai chi tiet bai nop.
 */
public class JudgeResultView extends JPanel {

    private static final Color GREEN = new Color(0x2E7D32);
    private static final Color RED = new Color(0xC62828);

    private JLabel lblVerdict = new JLabel(" ");
    private JLabel lblSummary = new JLabel(" ");
    private JLabel lblNext = new JLabel(" ");
    private JButton btnNext = new JButton("Làm bài tiếp →");
    private JPanel pnlNext = new JPanel(new BorderLayout(8, 0));
    private JTextArea txtMessage = new JTextArea(4, 40);
    private JScrollPane scrMessage = new JScrollPane(txtMessage);
    private JTable table = Ui.table(new String[] {"Test", "Kết quả", "Thời gian", "Điểm", "Thông báo"});

    /** Bai ke tiep trong lo trinh; null neu khong co hoac khong can nut "Lam bai tiep". */
    private Problem nextProblem;

    public JudgeResultView() {
        super(new BorderLayout(0, 6));

        lblVerdict.setFont(lblVerdict.getFont().deriveFont(Font.BOLD, 15f));
        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        summary.add(lblVerdict);
        summary.add(lblSummary);

        lblNext.setForeground(GREEN);
        pnlNext.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        pnlNext.add(lblNext, BorderLayout.CENTER);
        pnlNext.add(btnNext, BorderLayout.EAST);
        pnlNext.setVisible(false);

        txtMessage.setEditable(false);
        txtMessage.setFont(Ui.MONO);
        scrMessage.setVisible(false);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(summary, BorderLayout.NORTH);
        top.add(pnlNext, BorderLayout.CENTER);
        top.add(scrMessage, BorderLayout.SOUTH);

        table.getColumnModel().getColumn(4).setPreferredWidth(320);
        JScrollPane scrTable = new JScrollPane(table);
        scrTable.setPreferredSize(new Dimension(0, 230));

        add(top, BorderLayout.NORTH);
        add(scrTable, BorderLayout.CENTER);
    }

    /** Dat bai ke tiep trong lo trinh, de hien khung "Lam bai tiep" khi bai nop dat AC. */
    public void setNextProblem(Problem nextProblem) {
        this.nextProblem = nextProblem;
    }

    /** Nut "Lam bai tiep": giao dien Lam bai dang ky lang nghe nut nay. */
    public JButton getBtnNext() {
        return btnNext;
    }

    /** Hien ket qua cham cua mot bai nop. Khong bao gio hien input / dap an cua test. */
    public void setResult(Submission submission) {
        JudgeResult result = submission.getResult();
        Verdict verdict = result.getOverallVerdict();
        boolean accepted = verdict.isAccepted();

        lblVerdict.setText(verdict.getCode());
        if (accepted) {
            lblVerdict.setForeground(GREEN);
        } else {
            lblVerdict.setForeground(RED);
        }
        lblSummary.setText("Đúng " + result.getPassedCount() + "/" + result.getTotalTests() + " test    "
                + formatScore(result.getScore()) + "/" + formatPoints(submission.getProblem().getMaxPoints())
                + " điểm");

        // Loi chung cua ca bai, vi du nguyen van thong bao cua trinh bien dich khi CE.
        String message = result.getGlobalMessage();
        if (message == null) {
            message = "";
        }
        txtMessage.setText(message);
        txtMessage.setCaretPosition(0);
        scrMessage.setVisible(!message.isEmpty());

        List<Object[]> rows = new ArrayList<>();
        for (TestCaseResult t : result.getTestResults()) {
            rows.add(new Object[] {t.getTestCase().getId(), t.getVerdict().getCode(),
                    t.getRuntimeMs() + " ms", formatPoints(t.getEarnedPoints()), t.getMessage()});
        }
        Ui.setRows(table, rows);

        pnlNext.setVisible(accepted && nextProblem != null);
        if (nextProblem != null) {
            lblNext.setText("Chính xác! Sẵn sàng cho bài tiếp theo: "
                    + nextProblem.getId() + " — " + nextProblem.getTitle());
        }
        revalidate();
        repaint();
    }

    /** Diem cua ca bai, luon co mot chu so thap phan: 100.0, 62.5. */
    private String formatScore(double score) {
        return String.valueOf(Math.round(score * 10) / 10.0);
    }

    /** Viet diem gon: 100.0 thanh "100", 12.5 giu nguyen "12.5". */
    private String formatPoints(double points) {
        double rounded = Math.round(points * 100) / 100.0;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.valueOf(rounded);
    }
}
