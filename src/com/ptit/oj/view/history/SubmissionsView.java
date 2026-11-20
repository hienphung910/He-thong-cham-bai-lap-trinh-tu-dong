package com.ptit.oj.view.history;

import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.User;
import com.ptit.oj.model.Verdict;
import com.ptit.oj.view.Ui;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Bang lich su nop bai, dung cho hai trang:
 *   - "Trang thai": giang vien va quan tri vien thay bai nop cua moi nguoi, sinh vien
 *     chi thay bai cua minh; co o "Tu lam moi";
 *   - "Bai nop": chi bai nop cua nguoi dang dang nhap.
 * Nhan mot dong thi mo hop thoai chi tiet bai nop.
 */
public class SubmissionsView extends JPanel implements ActionListener {

    /** Chu ky tu lam moi (mili giay). */
    private static final int REFRESH_MS = 3000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yy");

    private JudgeService service;
    private User user;
    /** true = trang "Bai nop" (chi bai cua minh), false = trang "Trang thai". */
    private boolean onlyMine;

    private JTable table = Ui.table(
            new String[] {"Thời gian", "Người nộp", "Bài tập", "Ngôn ngữ", "Kết quả", "Thời gian chạy"});
    private JLabel lblSummary = new JLabel(" ");
    private JLabel lblEmpty = new JLabel(" ");
    private JCheckBox chkAuto = new JCheckBox("Tự làm mới");
    /** Dong ho cu REFRESH_MS mili giay lai goi actionPerformed() mot lan. */
    private Timer timer = new Timer(REFRESH_MS, this);
    /** Cac bai nop dang hien trong bang, cung thu tu voi cac dong. */
    private List<Submission> subs = new ArrayList<>();

    public SubmissionsView(JudgeService service, User user, boolean onlyMine) {
        super(new BorderLayout(0, 6));
        this.service = service;
        this.user = user;
        this.onlyMine = onlyMine;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        if (onlyMine) {
            header.add(Ui.title("Bài nộp của tôi"));
            header.add(lblSummary);
        } else {
            header.add(Ui.title("Trạng thái nộp bài"));
            header.add(lblSummary);
            header.add(chkAuto);
        }

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(lblEmpty, BorderLayout.SOUTH);

        chkAuto.addActionListener(this);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < subs.size()) {
                    openDetail(subs.get(row).getId());
                }
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == chkAuto) {
            // Bat / tat o "Tu lam moi" thi chay / dung dong ho.
            if (chkAuto.isSelected()) {
                timer.start();
            } else {
                timer.stop();
            }
        } else if (e.getSource() == timer) {
            // Dong ho bao: chi tai lai khi trang nay dang hien tren man hinh.
            if (isShowing()) {
                load();
            }
        }
    }

    /** Nap lai danh sach bai nop ma nguoi dang dang nhap duoc phep xem, moi nhat truoc. */
    public void load() {
        if (onlyMine) {
            subs = service.getSubmissionsOf(user);
        } else {
            subs = service.getSubmissionsFor(user);
        }

        int accepted = 0;
        List<Object[]> rows = new ArrayList<>();
        for (Submission s : subs) {
            String verdict = "Chờ chấm";
            String runtime = "—";
            if (s.isJudged()) {
                verdict = s.getResult().getOverallVerdict().getCode();
                runtime = formatSeconds(s.getResult().getMaxRuntimeMs());
                if (s.getResult().getOverallVerdict() == Verdict.AC) accepted++;
            }
            rows.add(new Object[] {s.getSubmittedAt().format(TIME), s.getAuthor().getUsername(),
                    s.getProblem().getId() + " – " + s.getProblem().getTitle(),
                    s.getLanguage().getName(), verdict, runtime});
        }
        Ui.setRows(table, rows);
        lblSummary.setText("Tổng: " + subs.size() + "   AC: " + accepted);

        if (!subs.isEmpty()) {
            lblEmpty.setText(" ");
        } else if (onlyMine) {
            lblEmpty.setText("Bạn chưa nộp bài nào.");
        } else {
            lblEmpty.setText("Chưa có bài nộp nào.");
        }
    }

    /** Doi mili giay sang giay voi hai chu so thap phan, vi du 63 ms thanh "0.06s". */
    private String formatSeconds(long ms) {
        return ms / 1000 + "." + String.format("%02d", (ms % 1000) / 10) + "s";
    }

    /** Mo hop thoai chi tiet; JudgeService kiem tra quyen xem truoc khi tra ve bai nop. */
    public void openDetail(String id) {
        try {
            Submission detail = service.getSubmissionDetail(user, id);
            SubmissionDetailView detailView = new SubmissionDetailView(SwingUtilities.getWindowAncestor(this));
            detailView.setDetail(detail);
            detailView.setVisible(true);
        } catch (RuntimeException ex) {
            Ui.error(this, ex);
        }
    }

    /** Dung viec tu lam moi (goi khi dang xuat). */
    public void stopAutoRefresh() {
        timer.stop();
    }
}
