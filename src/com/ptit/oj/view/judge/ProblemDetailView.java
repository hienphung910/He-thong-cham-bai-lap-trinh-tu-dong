package com.ptit.oj.view.judge;

import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.controller.judge.Language;
import com.ptit.oj.model.JudgeResult;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.TestCase;
import com.ptit.oj.model.User;
import com.ptit.oj.view.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Giao dien "Lam bai": ben trai la de bai va vi du, ben phai la noi chon ngon ngu,
 * soan ma nguon, nop bai va xem ket qua cham.
 */
public class ProblemDetailView extends JDialog implements ActionListener {

    private JudgeService service;
    private User user;
    private Problem problem;

    private JButton btnBack = new JButton("← Lớp học");
    private JComboBox<String> cboLanguage = new JComboBox<>();
    private JButton btnTemplate = new JButton("Chèn code mẫu");
    private JButton btnSubmit = new JButton("Nộp bài");
    private JLabel lblStatus = new JLabel(" ");
    private JTextArea txtCode = new JTextArea();
    private JudgeResultView resultView = new JudgeResultView();
    /** true neu nguoi dung da nhan "Lam bai tiep". */
    private boolean nextRequested = false;

    public ProblemDetailView(Window owner, JudgeService service, User user, Problem problem,
                             String topicName, Problem nextProblem) {
        super(owner, "Làm bài: " + problem.getId() + " — " + problem.getTitle(), ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.user = user;
        this.problem = problem;

        // ---- thanh tren
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        header.add(btnBack);
        header.add(Ui.title("Làm bài lớp: CodeHub · " + problem.getId()));
        header.add(new JLabel(topicName));
        header.add(new JLabel(Ui.difficulty(problem.getDifficulty())));

        // ---- the de bai: noi dung de va cac test vi du (test an khong bao gio hien ra)
        JTextArea txtStatement = readOnlyArea(problem.getStatement());
        txtStatement.setFont(Ui.TEXT);
        txtStatement.setLineWrap(true);
        txtStatement.setWrapStyleWord(true);

        JPanel samples = new JPanel(new GridLayout(0, 2, 6, 4));
        samples.setBorder(BorderFactory.createTitledBorder("Ví dụ"));
        samples.add(new JLabel("Input"));
        samples.add(new JLabel("Output"));
        boolean hasSample = false;
        for (TestCase tc : problem.getTestCases()) {
            if (!tc.isSample()) continue;
            hasSample = true;
            samples.add(new JScrollPane(readOnlyArea(tc.getInput().trim())));
            samples.add(new JScrollPane(readOnlyArea(tc.getExpectedOutput().trim())));
        }

        JPanel statementCard = new JPanel(new BorderLayout(0, 6));
        statementCard.setBorder(BorderFactory.createTitledBorder("Đề bài: " + problem.getId() + " — "
                + problem.getTitle() + "   (" + formatLimit() + ")"));
        statementCard.add(new JScrollPane(txtStatement), BorderLayout.CENTER);
        if (hasSample) {
            statementCard.add(samples, BorderLayout.SOUTH);
        }

        // ---- the nop bai
        for (Language l : service.getLanguages()) {
            cboLanguage.addItem(l.getName());
        }
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.add(cboLanguage);
        bar.add(btnTemplate);
        bar.add(btnSubmit);
        bar.add(lblStatus);
        txtCode.setFont(Ui.MONO);
        txtCode.setTabSize(4);
        resultView.setNextProblem(nextProblem);
        resultView.setVisible(false);

        JPanel submitCard = new JPanel(new BorderLayout(0, 6));
        submitCard.setBorder(BorderFactory.createTitledBorder("Nộp bài"));
        submitCard.add(bar, BorderLayout.NORTH);
        submitCard.add(new JScrollPane(txtCode), BorderLayout.CENTER);
        submitCard.add(resultView, BorderLayout.SOUTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        body.add(statementCard);
        body.add(submitCard);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        btnBack.addActionListener(this);
        cboLanguage.addActionListener(this);
        btnTemplate.addActionListener(this);
        btnSubmit.addActionListener(this);
        resultView.getBtnNext().addActionListener(this);

        checkLanguage();
        setSize(1180, 740);
        setLocationRelativeTo(owner);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnBack) {
            dispose();
        } else if (e.getSource() == cboLanguage) {
            checkLanguage();
        } else if (e.getSource() == btnTemplate) {
            setCode(templateOf((String) cboLanguage.getSelectedItem()));
        } else if (e.getSource() == btnSubmit) {
            doSubmit();
        } else if (e.getSource() == resultView.getBtnNext()) {
            // Nguoi dung nhan "Lam bai tiep": dong lai de trang Lop hoc mo bai ke tiep.
            nextRequested = true;
            dispose();
        }
    }

    /** Gioi han cua bai, vi du "1s · 128 MB". */
    private String formatLimit() {
        long ms = problem.getTimeLimitMs();
        String seconds;
        if (ms % 1000 == 0) {
            seconds = String.valueOf(ms / 1000);
        } else {
            seconds = String.valueOf(ms / 1000.0);
        }
        return seconds + "s · " + problem.getMemoryLimitMb() + " MB";
    }

    /** Tao o van ban chi doc voi phong chu deu. */
    private JTextArea readOnlyArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(Ui.MONO);
        area.setCaretPosition(0);
        return area;
    }

    /** Khung chuong trinh mau cua tung ngon ngu (doc hai so, in ra tong). */
    private String templateOf(String language) {
        if ("Java".equals(language)) {
            return "import java.util.Scanner;\n\n"
                    + "public class Solution {\n"
                    + "    public static void main(String[] args) {\n"
                    + "        Scanner sc = new Scanner(System.in);\n"
                    + "        long a = sc.nextLong();\n"
                    + "        long b = sc.nextLong();\n"
                    + "        System.out.println(a + b);\n"
                    + "    }\n"
                    + "}\n";
        }
        if ("C++".equals(language)) {
            return "#include <iostream>\n\n"
                    + "int main() {\n"
                    + "    long long a, b;\n"
                    + "    std::cin >> a >> b;\n"
                    + "    std::cout << a + b << std::endl;\n"
                    + "    return 0;\n"
                    + "}\n";
        }
        if ("Python".equals(language)) {
            return "import sys\n\n"
                    + "data = sys.stdin.read().split()\n"
                    + "print(int(data[0]) + int(data[1]))\n";
        }
        if ("JavaScript".equals(language)) {
            return "const data = require(\"fs\").readFileSync(0, \"utf8\").trim().split(/\\s+/);\n"
                    + "console.log((BigInt(data[0]) + BigInt(data[1])).toString());\n";
        }
        return "";
    }

    /** Ngon ngu dang chon trong hop chon; null neu he thong khong co ngon ngu nao. */
    private Language currentLanguage() {
        return service.findLanguage((String) cboLanguage.getSelectedItem());
    }

    /** Ngon ngu chua cai trinh bien dich tren may thi khong cho nop. */
    private void checkLanguage() {
        Language language = currentLanguage();
        if (language == null) {
            btnSubmit.setEnabled(false);
            lblStatus.setText(" ");
        } else if (language.isAvailable()) {
            btnSubmit.setEnabled(true);
            lblStatus.setText(" ");
        } else {
            btnSubmit.setEnabled(false);
            lblStatus.setText("Máy này chưa cài toolchain cho " + language.getName() + ".");
        }
    }

    /** Thay toan bo ma nguon trong o soan thao (dung khi chen code mau). */
    public void setCode(String code) {
        txtCode.setText(code);
        txtCode.setCaretPosition(0);
    }

    /**
     * Nop bai: kiem tra da chon ngon ngu va ma nguon khong rong, bao "Dang cham",
     * goi JudgeService cham bai roi hien ket qua tung test. Trong luc cham cua so
     * dung cho (con tro chuot hinh dong ho cat).
     */
    public void doSubmit() {
        Language language = currentLanguage();
        String code = txtCode.getText();
        if (language == null) {
            Ui.error(this, "Chưa chọn ngôn ngữ.");
            return;
        }
        if (code.trim().isEmpty()) {
            Ui.error(this, "Mã nguồn đang rỗng.");
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Đang chấm...");
        lblStatus.setText("Đang biên dịch và chấm bài...");
        resultView.setVisible(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        // Ve lai cua so ngay bay gio de nguoi dung thay dong "Dang cham" truoc khi bat dau cham.
        validate();
        getRootPane().paintImmediately(0, 0, getRootPane().getWidth(), getRootPane().getHeight());

        try {
            JudgeResult result = service.submitCode(problem, user, language, code);
            resultView.setResult(result.getSubmission());
            resultView.setVisible(true);
        } catch (RuntimeException ex) {
            Ui.error(this, ex);
        }

        setCursor(Cursor.getDefaultCursor());
        btnSubmit.setEnabled(true);
        btnSubmit.setText("Nộp bài");
        lblStatus.setText(" ");
    }

    public boolean isNextRequested() {
        return nextRequested;
    }
}
