package com.ptit.oj.view.problem;

import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.model.ProblemDraft;
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.User;
import com.ptit.oj.view.Ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Bieu mau "New Problem" cua quan tri vien: thong tin bai tap va danh sach test
 * (test vi du va test an). Moi luat kiem tra du lieu nam o ProblemAdminService.
 */
public class ProblemEditorView extends JDialog implements ActionListener {

    /** Mot test trong bieu mau: loai (vi du / an), ten, input, output mong doi va nut xoa. */
    private static class TestRow {
        boolean sample;
        JTextField txtName = new JTextField(10);
        JTextArea txtInput = new JTextArea(3, 24);
        JTextArea txtOutput = new JTextArea(3, 24);
        JButton btnRemove = new JButton("Xoá");
        JPanel panel = new JPanel(new BorderLayout(0, 2));
    }

    private JudgeService service;
    private User user;

    private JTextField txtId = new JTextField();
    private JTextField txtTitle = new JTextField();
    private JComboBox<String> cboTopic = new JComboBox<>();
    /** Ma chu de cua tung muc trong hop chon; "" = chua phan loai. */
    private List<String> topicIds = new ArrayList<>();
    private JComboBox<String> cboDifficulty = new JComboBox<>(new String[] {"Chưa đánh giá", "Dễ", "Vừa", "Khó"});
    private JTextArea txtStatement = new JTextArea(5, 50);
    private JTextField txtTime = new JTextField("1000");
    private JTextField txtMemory = new JTextField("64");
    private JTextField txtPoints = new JTextField("100");
    private JComboBox<String> cboComparator =
            new JComboBox<>(new String[] {"token", "exact", "lines", "float:1e-6"});

    private JButton btnAddSample = new JButton("+ Test ví dụ");
    private JButton btnAddHidden = new JButton("+ Test ẩn");
    private JPanel pnlTests = new JPanel();
    private List<TestRow> tests = new ArrayList<>();

    private JButton btnCreate = new JButton("Create problem");
    private JButton btnCancel = new JButton("Cancel");

    public ProblemEditorView(Window owner, JudgeService service, User user) {
        super(owner, "New Problem", ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.user = user;

        topicIds.add("");
        cboTopic.addItem("(Chưa phân loại — hiện ở \"Bài khác\")");
        for (Topic t : service.getTopics()) {
            topicIds.add(t.getId());
            cboTopic.addItem(t.getName());
        }

        // ---- thong tin bai tap: moi dong mot nhan va mot o nhap
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("Mã bài"));
        form.add(txtId);
        form.add(new JLabel("Tên bài"));
        form.add(txtTitle);
        form.add(new JLabel("Chủ đề trong lộ trình"));
        form.add(cboTopic);
        form.add(new JLabel("Độ khó"));
        form.add(cboDifficulty);
        form.add(new JLabel("Thời gian (ms)"));
        form.add(txtTime);
        form.add(new JLabel("Bộ nhớ (MB)"));
        form.add(txtMemory);
        form.add(new JLabel("Tổng điểm"));
        form.add(txtPoints);
        form.add(new JLabel("Kiểu so sánh"));
        form.add(cboComparator);

        txtStatement.setFont(Ui.TEXT);
        txtStatement.setLineWrap(true);
        txtStatement.setWrapStyleWord(true);
        JScrollPane scrStatement = new JScrollPane(txtStatement);
        scrStatement.setBorder(BorderFactory.createTitledBorder("Nội dung đề"));

        JPanel info = new JPanel(new BorderLayout(0, 6));
        info.add(form, BorderLayout.NORTH);
        info.add(scrStatement, BorderLayout.CENTER);

        // ---- danh sach test: ban dau co mot test vi du va mot test an con trong
        JPanel testBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        testBar.add(btnAddSample);
        testBar.add(btnAddHidden);
        pnlTests.setLayout(new BoxLayout(pnlTests, BoxLayout.Y_AXIS));
        JScrollPane scrTests = new JScrollPane(pnlTests);
        scrTests.setPreferredSize(new Dimension(0, 230));
        JPanel testCard = new JPanel(new BorderLayout(0, 4));
        testCard.setBorder(BorderFactory.createTitledBorder("Danh sách test"));
        testCard.add(testBar, BorderLayout.NORTH);
        testCard.add(scrTests, BorderLayout.CENTER);
        addTest(true);
        addTest(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(btnCancel);
        buttons.add(btnCreate);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(info, BorderLayout.NORTH);
        root.add(testCard, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        btnAddSample.addActionListener(this);
        btnAddHidden.addActionListener(this);
        btnCreate.addActionListener(this);
        btnCancel.addActionListener(this);

        setSize(820, 760);
        setLocationRelativeTo(owner);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnAddSample) {
            addTest(true);
        } else if (e.getSource() == btnAddHidden) {
            addTest(false);
        } else if (e.getSource() == btnCreate) {
            doCreate();
        } else if (e.getSource() == btnCancel) {
            dispose();
        } else {
            // Con lai la nut "Xoa" cua mot test: tim xem nut do thuoc test nao.
            for (TestRow row : tests) {
                if (e.getSource() == row.btnRemove) {
                    removeTest(row);
                    break;
                }
            }
        }
    }

    /** Them mot test vao bieu mau; ten goi y la sample01, sample02... hoac 01, 02... */
    private void addTest(boolean sample) {
        int sameKind = 0;
        for (TestRow t : tests) {
            if (t.sample == sample) sameKind++;
        }

        TestRow row = new TestRow();
        row.sample = sample;
        row.txtInput.setFont(Ui.MONO);
        row.txtOutput.setFont(Ui.MONO);
        row.btnRemove.addActionListener(this);

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        if (sample) {
            row.txtName.setText(String.format("sample%02d", sameKind + 1));
            head.add(new JLabel("Test ví dụ"));
        } else {
            row.txtName.setText(String.format("%02d", sameKind + 1));
            head.add(new JLabel("Test ẩn"));
        }
        head.add(row.txtName);
        head.add(row.btnRemove);

        JScrollPane scrInput = new JScrollPane(row.txtInput);
        scrInput.setBorder(BorderFactory.createTitledBorder("Input"));
        JScrollPane scrOutput = new JScrollPane(row.txtOutput);
        scrOutput.setBorder(BorderFactory.createTitledBorder("Output mong đợi"));
        JPanel data = new JPanel(new GridLayout(1, 2, 6, 0));
        data.add(scrInput);
        data.add(scrOutput);

        row.panel.add(head, BorderLayout.NORTH);
        row.panel.add(data, BorderLayout.CENTER);

        tests.add(row);
        pnlTests.add(row.panel);
        pnlTests.revalidate();
    }

    /** Bo mot test khoi bieu mau. */
    private void removeTest(TestRow row) {
        tests.remove(row);
        pnlTests.remove(row.panel);
        pnlTests.revalidate();
        pnlTests.repaint();
    }

    /** Doc cac o nhap thanh ban thao bai tap; o so khong phai la so thi bao loi. */
    private ProblemDraft readForm() {
        ProblemDraft draft = new ProblemDraft();
        draft.setId(txtId.getText());
        draft.setTitle(txtTitle.getText());
        draft.setTopic(topicIds.get(cboTopic.getSelectedIndex()));
        draft.setDifficulty(cboDifficulty.getSelectedIndex());
        draft.setStatement(txtStatement.getText());
        draft.setTimeLimitMs((long) readNumber(txtTime, "Thời gian (ms)"));
        draft.setMemoryLimitMb((int) readNumber(txtMemory, "Bộ nhớ (MB)"));
        draft.setTotalPoints(readNumber(txtPoints, "Tổng điểm"));
        draft.setComparator((String) cboComparator.getSelectedItem());
        for (TestRow t : tests) {
            draft.addTest(new ProblemDraft.TestDraft(t.txtName.getText().trim(),
                    t.txtInput.getText(), t.txtOutput.getText(), t.sample));
        }
        return draft;
    }

    private double readNumber(JTextField field, String label) {
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " phải là một số");
        }
    }

    /** Tao bai tap: ProblemAdminService kiem tra, ghi ra o dia va nap vao bo nho. */
    private void doCreate() {
        try {
            ProblemDraft draft = readForm();
            service.createProblem(user, draft);
            Ui.info(this, "Đã tạo bài tập.");
            dispose();
        } catch (RuntimeException ex) {
            Ui.error(this, ex);
        }
    }
}
