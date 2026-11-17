package com.ptit.oj.view.history;

import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.controller.ScoreboardService;
import com.ptit.oj.model.Student;
import com.ptit.oj.model.User;
import com.ptit.oj.view.Ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trang "Xep hang": buc top 3 va bang xep hang, tinh tu toan bo bai nop da cham.
 * Thu tu do ScoreboardService quyet dinh: tong diem giam dan, so lan nop tang dan,
 * ten dang nhap.
 */
public class LeaderboardView extends JPanel {

    private static final Color GOLD = new Color(0xB8860B);
    private static final Color SILVER = new Color(0x757575);
    private static final Color BRONZE = new Color(0xA0522D);

    private JudgeService service;
    /** Buc top 3: toi da ba the dung canh nhau, moi the la mot nguoi dung dau. */
    private JPanel pnlTop3 = new JPanel(new GridLayout(1, 3, 8, 0));
    private JTable table = Ui.table(new String[] {"Hạng", "Sinh viên", "Mã SV", "Đã giải"});
    private JLabel lblEmpty = new JLabel(" ");

    public LeaderboardView(JudgeService service) {
        super(new BorderLayout(0, 6));
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.add(Ui.title("Bảng xếp hạng lớp"), BorderLayout.NORTH);
        header.add(pnlTop3, BorderLayout.CENTER);

        table.getColumnModel().getColumn(0).setMaxWidth(70);
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(lblEmpty, BorderLayout.SOUTH);
    }

    /** Tinh lai bang xep hang, ve lai buc top 3 va dua cac dong len bang. */
    public void load() {
        List<ScoreboardService.Row> ranking = service.buildScoreboard();
        Map<String, Integer> solvedByUser = service.getSolvedCountByUser();

        pnlTop3.removeAll();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            User u = ranking.get(i).getUser();
            int rank = i + 1;

            // Chi sinh vien moi co ma sinh vien.
            String studentCode = "—";
            if (u instanceof Student) {
                studentCode = ((Student) u).getStudentCode();
            }
            int solved = 0;
            if (solvedByUser.containsKey(u.getId())) {
                solved = solvedByUser.get(u.getId());
            }

            if (rank <= 3) {
                pnlTop3.add(createTopCard(rank, u, solved));
            }
            rows.add(new Object[] {rank, u.getFullName() + " (" + u.getUsername() + ")", studentCode, solved});
        }
        Ui.setRows(table, rows);

        // Chua co bai nop nao duoc cham thi khong co buc top 3.
        pnlTop3.setVisible(!ranking.isEmpty());
        pnlTop3.revalidate();
        pnlTop3.repaint();
        if (rows.isEmpty()) {
            lblEmpty.setText("Chưa có dữ liệu.");
        } else {
            lblEmpty.setText(" ");
        }
    }

    /** Tao the cua mot nguoi trong top 3: huy chuong, hang, ho ten, ten dang nhap, so bai da giai. */
    private JPanel createTopCard(int rank, User u, int solved) {
        String medal = "Huy chương đồng";
        Color color = BRONZE;
        if (rank == 1) {
            medal = "Huy chương vàng";
            color = GOLD;
        } else if (rank == 2) {
            medal = "Huy chương bạc";
            color = SILVER;
        }

        JLabel lblMedal = new JLabel(medal + " · Hạng " + rank);
        lblMedal.setForeground(color);
        lblMedal.setFont(lblMedal.getFont().deriveFont(Font.BOLD));

        JPanel card = new JPanel(new GridLayout(0, 1, 0, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        card.add(lblMedal);
        card.add(Ui.title(u.getFullName()));
        card.add(new JLabel("@" + u.getUsername()));
        card.add(new JLabel("Đã giải " + solved + " bài"));
        return card;
    }
}
