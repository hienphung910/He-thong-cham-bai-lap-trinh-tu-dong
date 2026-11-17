package com.ptit.oj.view.account;

import com.ptit.oj.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Menu tai khoan: ho ten, ten dang nhap, vai tro va nut dang xuat. */
public class AccountMenuView extends JDialog implements ActionListener {

    private JButton btnLogout = new JButton("Đăng xuất");
    /** true neu nguoi dung da nhan "Dang xuat". */
    private boolean logoutRequested = false;

    public AccountMenuView(Window owner, User user) {
        super(owner, "Tài khoản", ModalityType.APPLICATION_MODAL);

        JPanel info = new JPanel(new GridLayout(3, 2, 12, 8));
        info.add(new JLabel("Họ và tên"));
        info.add(new JLabel(user.getFullName()));
        info.add(new JLabel("Tên đăng nhập"));
        info.add(new JLabel("@" + user.getUsername()));
        info.add(new JLabel("Vai trò"));
        info.add(new JLabel(user.getRole()));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        root.add(info, BorderLayout.CENTER);
        root.add(btnLogout, BorderLayout.SOUTH);
        setContentPane(root);

        btnLogout.addActionListener(this);

        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnLogout) {
            logoutRequested = true;
            dispose();
        }
    }

    public boolean isLogoutRequested() {
        return logoutRequested;
    }
}
