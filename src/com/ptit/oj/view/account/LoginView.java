package com.ptit.oj.view.account;

import com.ptit.oj.controller.AuthService;
import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.view.HomeView;
import com.ptit.oj.view.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Cua so dang nhap - cua so dau tien khi mo chuong trinh. */
public class LoginView extends JFrame implements ActionListener {

    private JudgeService service;
    private JTextField txtUsername = new JTextField(18);
    private JPasswordField txtPassword = new JPasswordField(18);
    private JButton btnLogin = new JButton("Đăng nhập");
    private JButton btnRegister = new JButton("Chưa có tài khoản? Đăng ký");

    public LoginView(JudgeService service) {
        super("CodeHub - Đăng nhập");
        this.service = service;

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.add(Ui.title("CodeHub"));
        header.add(new JLabel("Truy cập hệ thống chấm bài"));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Tên đăng nhập"));
        form.add(txtUsername);
        form.add(new JLabel("Mật khẩu"));
        form.add(txtPassword);

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        buttons.add(btnLogin);
        buttons.add(btnRegister);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        btnLogin.addActionListener(this);
        btnRegister.addActionListener(this);
        getRootPane().setDefaultButton(btnLogin);       // nhan Enter cung la dang nhap

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnLogin) {
            handleLogin();
        } else if (e.getSource() == btnRegister) {
            openRegister();
        }
    }

    /** Kiem tra ten dang nhap, mat khau; dung thi mo cua so chinh kem token cua phien vua tao. */
    private void handleLogin() {
        String username = txtUsername.getText();
        String password = new String(txtPassword.getPassword());
        try {
            AuthService.LoginResult result = service.getAuth().login(username, password);
            HomeView homeView = new HomeView(service, result.getUser(), result.getToken());
            homeView.setVisible(true);
            dispose();
        } catch (RuntimeException ex) {
            Ui.error(this, ex);
            // Sai thi xoa mat khau va dua con tro ve o mat khau de nhap lai (ngoai le 8a).
            txtPassword.setText("");
            txtPassword.requestFocusInWindow();
        }
    }

    /** Mo bieu mau dang ky; tao xong thi dien san ten dang nhap vua tao. */
    private void openRegister() {
        RegisterView registerView = new RegisterView(this, service);
        registerView.setVisible(true);          // hop thoai modal: dung o day den khi dong
        String created = registerView.getCreatedUsername();
        if (created != null) {
            txtUsername.setText(created);
            txtPassword.setText("");
            Ui.info(this, "Đã tạo tài khoản @" + created + ". Mời bạn đăng nhập.");
        }
    }
}
