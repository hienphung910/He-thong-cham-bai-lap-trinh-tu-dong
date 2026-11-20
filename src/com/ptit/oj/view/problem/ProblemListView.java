package com.ptit.oj.view.problem;

import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.User;
import com.ptit.oj.model.Verdict;
import com.ptit.oj.view.Ui;
import com.ptit.oj.view.judge.ProblemDetailView;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trang "Lop hoc": ben trai la lo trinh hoc (lop, tien do, cac chang), ben phai la
 * bang bai tap cua chang dang chon. Nhan mot dong thi mo giao dien Lam bai.
 */
public class ProblemListView extends JPanel implements ActionListener, ListSelectionListener {

    private JudgeService service;
    private User user;

    private JComboBox<String> cboGroup = new JComboBox<>();
    private JLabel lblProgress = new JLabel(" ");
    private JButton btnNext = new JButton();
    private DefaultListModel<String> topicModel = new DefaultListModel<>();
    private JList<String> lstTopics = new JList<>(topicModel);
    private JLabel lblTitle = Ui.title(" ");
    private JLabel lblDescription = new JLabel(" ");
    private JTextField txtSearch = new JTextField(18);
    private JButton btnSearch = new JButton("Tìm");
    private JComboBox<String> cboDifficulty = new JComboBox<>(new String[] {"Mọi mức", "Dễ", "Vừa", "Khó"});
    private JTable table = Ui.table(new String[] {"TT", "Mã", "Tên đề", "Chủ đề", "Độ khó", "Trạng thái"});

    /** Cac chang cua lop dang xem, theo thu tu hoc. */
    private List<Topic> groupTopics = new ArrayList<>();
    /** Khoa cua tung dong trong danh sach chang: "" = tat ca, ma chu de, hoac OTHER_TOPIC. */
    private List<String> topicKeys = new ArrayList<>();
    /** Bai cua lop dang xem, xep theo lo trinh. */
    private List<Problem> ordered = new ArrayList<>();
    /** Bai dang hien trong bang sau khi loc. */
    private List<Problem> filtered = new ArrayList<>();
    /** Ma cac bai nguoi dung da giai (AC) va cac bai da nop nhung chua dung. */
    private Set<String> solved = new HashSet<>();
    private Set<String> attempted = new HashSet<>();
    /** Bai dau tien chua giai theo lo trinh. */
    private Problem nextToLearn;
    /** true khi giao dien dang tu cap nhat, de su kien chon khong goi long nhau. */
    private boolean updating = false;

    public ProblemListView(JudgeService service, User user) {
        super(new BorderLayout(8, 0));
        this.service = service;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Moi lop (cot thu 4 trong topics.txt) co lo trinh rieng, lop xuat hien truoc dung truoc.
        List<String> groups = new ArrayList<>();
        for (Topic t : service.getTopics()) {
            if (!groups.contains(t.getGroup())) {
                groups.add(t.getGroup());
                cboGroup.addItem(t.getGroup());
            }
        }
        cboGroup.setVisible(groups.size() > 1);
        cboDifficulty.setPrototypeDisplayValue("Mọi mức    ");

        // ---- thanh ben: lo trinh hoc
        JPanel pathHeader = new JPanel(new GridLayout(0, 1, 0, 4));
        pathHeader.add(Ui.title("Lộ trình học"));
        pathHeader.add(cboGroup);
        pathHeader.add(lblProgress);
        pathHeader.add(btnNext);
        lstTopics.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel side = new JPanel(new BorderLayout(0, 6));
        side.add(pathHeader, BorderLayout.NORTH);
        side.add(new JScrollPane(lstTopics), BorderLayout.CENTER);
        side.setPreferredSize(new Dimension(300, 0));

        // ---- danh sach bai
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filter.add(new JLabel("Tìm mã / tên bài"));
        filter.add(txtSearch);
        filter.add(btnSearch);
        filter.add(new JLabel("Độ khó"));
        filter.add(cboDifficulty);
        JPanel listHeader = new JPanel(new GridLayout(0, 1, 0, 2));
        listHeader.add(lblTitle);
        listHeader.add(lblDescription);
        listHeader.add(filter);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setMaxWidth(80);
        table.getColumnModel().getColumn(4).setMaxWidth(80);
        table.getColumnModel().getColumn(5).setMaxWidth(90);
        JPanel main = new JPanel(new BorderLayout(0, 6));
        main.add(listHeader, BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);

        add(side, BorderLayout.WEST);
        add(main, BorderLayout.CENTER);

        // ---- su kien
        cboGroup.addActionListener(this);
        cboDifficulty.addActionListener(this);
        btnSearch.addActionListener(this);
        txtSearch.addActionListener(this);          // nhan Enter trong o tim kiem
        btnNext.addActionListener(this);
        lstTopics.addListSelectionListener(this);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < filtered.size()) {
                    setSelectedId(filtered.get(row).getId());
                }
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (updating) return;
        if (e.getSource() == cboGroup) {
            // Sang lop khac thi quay ve "Tat ca bai tap".
            updating = true;
            lstTopics.clearSelection();
            updating = false;
            refresh();
        } else if (e.getSource() == btnNext) {
            if (nextToLearn != null) {
                setSelectedId(nextToLearn.getId());
            }
        } else {
            // doi do kho, nhan nut Tim hoac nhan Enter trong o tim kiem
            applyFilter();
        }
    }

    /** Nguoi dung chon mot chang trong danh sach ben trai. */
    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (!updating && !e.getValueIsAdjusting()) {
            applyFilter();
        }
    }

    /** Nap lai trang thai da giai cua nguoi dung roi ve lai lo trinh va bang bai tap. */
    public void load() {
        solved.clear();
        attempted.clear();
        for (Submission s : service.getSubmissionsOf(user)) {
            String problemId = s.getProblem().getId();
            if (s.isJudged() && s.getResult().getOverallVerdict() == Verdict.AC) {
                solved.add(problemId);
            } else {
                attempted.add(problemId);
            }
        }
        refresh();
    }

    /** Lop dang chon trong hop chon; "" neu lo trinh khong chia lop. */
    private String currentGroup() {
        if (cboGroup.getSelectedItem() == null) return "";
        return (String) cboGroup.getSelectedItem();
    }

    /** Tinh lai cac chang, tien do va bai "hoc tiep" cua lop dang chon. */
    private void refresh() {
        List<Topic> topics = service.getTopics();
        String group = currentGroup();
        String firstGroup = "";
        if (!topics.isEmpty()) {
            firstGroup = topics.get(0).getGroup();
        }

        groupTopics = new ArrayList<>();
        Set<String> known = new HashSet<>();        // ma cua moi chu de trong lo trinh
        Set<String> inGroup = new HashSet<>();      // ma cac chu de cua lop dang xem
        for (Topic t : topics) {
            known.add(t.getId());
            if (t.getGroup().equals(group)) {
                groupTopics.add(t);
                inGroup.add(t.getId());
            }
        }
        // Chi lay bai cua lop dang xem; bai chua phan loai ("Bai khac") xep vao lop dau tien.
        List<Problem> mine = new ArrayList<>();
        for (Problem p : service.getProblems()) {
            boolean belongs;
            if (known.contains(p.getTopic())) {
                belongs = inGroup.contains(p.getTopic());
            } else {
                belongs = group.equals(firstGroup);
            }
            if (belongs) mine.add(p);
        }
        ordered = LearningPath.orderByPath(mine, groupTopics);

        // Tien do tung chang: khoa chang -> tong so bai / so bai da giai.
        Map<String, Integer> totalByTopic = new HashMap<>();
        Map<String, Integer> solvedByTopic = new HashMap<>();
        int solvedAll = 0;
        nextToLearn = null;
        for (Problem p : ordered) {
            String key = LearningPath.topicKey(p, groupTopics);
            if (!totalByTopic.containsKey(key)) {
                totalByTopic.put(key, 0);
                solvedByTopic.put(key, 0);
            }
            totalByTopic.put(key, totalByTopic.get(key) + 1);
            if (solved.contains(p.getId())) {
                solvedByTopic.put(key, solvedByTopic.get(key) + 1);
                solvedAll++;
            } else if (nextToLearn == null) {
                nextToLearn = p;
            }
        }

        // Ve lai danh sach chang, giu nguyen chang dang chon.
        String selectedKey = currentTopicKey();
        updating = true;
        topicModel.clear();
        topicKeys.clear();
        addTopicRow("", "Tất cả bài tập", solvedAll, ordered.size());
        for (int i = 0; i < groupTopics.size(); i++) {
            Topic t = groupTopics.get(i);
            int total = 0;
            int done = 0;
            if (totalByTopic.containsKey(t.getId())) {
                total = totalByTopic.get(t.getId());
                done = solvedByTopic.get(t.getId());
            }
            addTopicRow(t.getId(), "Chặng " + (i + 1) + ": " + t.getName(), done, total);
        }
        if (totalByTopic.containsKey(LearningPath.OTHER_TOPIC)) {
            addTopicRow(LearningPath.OTHER_TOPIC, "Bài khác",
                    solvedByTopic.get(LearningPath.OTHER_TOPIC), totalByTopic.get(LearningPath.OTHER_TOPIC));
        }
        int index = topicKeys.indexOf(selectedKey);
        if (index < 0) index = 0;
        lstTopics.setSelectedIndex(index);
        updating = false;

        lblProgress.setText("Đã giải " + solvedAll + "/" + ordered.size() + " bài");
        btnNext.setVisible(nextToLearn != null);
        if (nextToLearn != null) {
            String prefix = "HỌC TIẾP";
            if (solvedAll == 0) prefix = "BẮT ĐẦU HỌC";
            btnNext.setText(prefix + " → " + nextToLearn.getId() + " " + nextToLearn.getTitle());
        }
        applyFilter();
    }

    private void addTopicRow(String key, String label, int done, int total) {
        topicKeys.add(key);
        topicModel.addElement(label + "  (" + done + "/" + total + ")");
    }

    /** Khoa cua chang dang chon; "" neu dang chon "Tat ca bai tap". */
    private String currentTopicKey() {
        int index = lstTopics.getSelectedIndex();
        if (index >= 0 && index < topicKeys.size()) {
            return topicKeys.get(index);
        }
        return "";
    }

    /** Loc bai theo chang dang chon, do kho va tu khoa roi dua len bang. */
    private void applyFilter() {
        String key = currentTopicKey();
        int difficulty = cboDifficulty.getSelectedIndex();          // 0 = moi muc
        String keyword = txtSearch.getText().trim().toLowerCase();

        filtered = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (Problem p : ordered) {
            if (!key.isEmpty() && !LearningPath.topicKey(p, groupTopics).equals(key)) continue;
            if (difficulty != 0 && p.getDifficulty() != difficulty) continue;
            if (!keyword.isEmpty() && !p.getTitle().toLowerCase().contains(keyword)
                    && !p.getId().toLowerCase().contains(keyword)) continue;
            filtered.add(p);

            String status = "";
            if (solved.contains(p.getId())) {
                status = "Đã giải";
            } else if (attempted.contains(p.getId())) {
                status = "Đã thử";
            }
            rows.add(new Object[] {filtered.size(), p.getId(), p.getTitle(),
                    LearningPath.topicName(p, groupTopics), Ui.difficulty(p.getDifficulty()), status});
        }
        Ui.setRows(table, rows);

        // Tieu de va mo ta cua chang dang xem.
        int index = topicKeys.indexOf(key);
        Topic current = null;
        if (index >= 1 && index <= groupTopics.size()) {
            current = groupTopics.get(index - 1);
        }
        if (current != null) {
            lblTitle.setText("Chặng " + index + ": " + current.getName());
            if (current.getDescription().isEmpty()) {
                lblDescription.setText(" ");
            } else {
                lblDescription.setText(current.getDescription());
            }
        } else {
            if (key.equals(LearningPath.OTHER_TOPIC)) {
                lblTitle.setText("Bài khác");
            } else if (currentGroup().isEmpty()) {
                lblTitle.setText("Lớp học của tôi");
            } else {
                lblTitle.setText("Lớp " + currentGroup());
            }
            lblDescription.setText(" ");
        }
    }

    /**
     * Mo giao dien Lam bai cua bai co ma id. Khi nguoi dung nhan "Lam bai tiep" thi
     * mo luon bai ke tiep: trong danh sach dang loc neu bai nam trong do, khong thi
     * theo lo trinh chung.
     */
    public void setSelectedId(String id) {
        String currentId = id;
        while (currentId != null) {
            Problem selected = null;
            for (Problem p : ordered) {
                if (p.getId().equals(currentId)) selected = p;
            }
            if (selected == null) return;

            List<Problem> list = ordered;
            if (filtered.contains(selected)) list = filtered;
            int index = list.indexOf(selected);
            Problem next = null;
            if (index + 1 < list.size()) next = list.get(index + 1);

            ProblemDetailView detail = new ProblemDetailView(SwingUtilities.getWindowAncestor(this), service, user,
                    selected, LearningPath.topicName(selected, groupTopics), next);
            detail.setVisible(true);        // hop thoai modal: dung o day den khi nguoi dung dong
            load();                         // cap nhat lai tien do sau khi nop bai

            if (detail.isNextRequested() && next != null) {
                currentId = next.getId();
            } else {
                currentId = null;
            }
        }
    }
}
