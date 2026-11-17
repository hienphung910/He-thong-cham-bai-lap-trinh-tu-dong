package com.ptit.oj.view.problem;

import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.User;
import com.ptit.oj.view.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/** Hop thoai "Sua bai trong lo trinh": doi chu de va do kho cua mot bai da co. */
public class ProblemMetaEditorView extends JDialog implements ActionListener {

    private JudgeService service;
    private User user;
    private Problem problem;

    private JComboBox<String> cboTopic = new JComboBox<>();
    /** Ma chu de cua tung muc trong hop chon; "" = bai khac (chua phan loai). */
    private List<String> topicIds = new ArrayList<>();
    private JComboBox<String> cboDifficulty = new JComboBox<>(new String[] {"Chưa đánh giá", "Dễ", "Vừa", "Khó"});
    private JButton btnSave = new JButton("Lưu thay đổi");
    private JButton btnCancel = new JButton("Huỷ");

    public ProblemMetaEditorView(Window owner, JudgeService service, User user, Problem problem) {
        super(owner, "Sửa bài trong lộ trình", ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.user = user;
        this.problem = problem;

        topicIds.add("");
        cboTopic.addItem("Bài khác (chưa phân loại)");
        for (Topic t : service.getTopics()) {
            topicIds.add(t.getId());
            cboTopic.addItem(t.getName());
        }
        // Chon san chu de va do kho hien tai cua bai.
        int topicIndex = topicIds.indexOf(problem.getTopic());
        if (topicIndex < 0) topicIndex = 0;
        cboTopic.setSelectedIndex(topicIndex);
        if (problem.getDifficulty() >= 1 && problem.getDifficulty() <= 3) {
            cboDifficulty.setSelectedIndex(problem.getDifficulty());
        }

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Chủ đề"));
        form.add(cboTopic);
        form.add(new JLabel("Độ khó"));
        form.add(cboDifficulty);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(btnCancel);
        buttons.add(btnSave);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        root.add(Ui.title(problem.getId() + " · " + problem.getTitle()), BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        btnSave.addActionListener(this);
        btnCancel.addActionListener(this);

        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnSave) {
            save();
        } else if (e.getSource() == btnCancel) {
            dispose();
        }
    }

    /** Luu chu de va do kho moi; loi (vi du chu de khong co trong lo trinh) thi bao va o lai hop thoai. */
    private void save() {
        String topicId = topicIds.get(cboTopic.getSelectedIndex());
        int difficulty = cboDifficulty.getSelectedIndex();
        try {
            service.updateProblemMeta(user, problem.getId(), topicId, difficulty);
            dispose();
        } catch (RuntimeException ex) {
            Ui.error(this, ex);
        }
    }
}
