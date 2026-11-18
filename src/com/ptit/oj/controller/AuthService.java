package com.ptit.oj.controller;

import com.ptit.oj.dao.UserRepository;
import com.ptit.oj.model.Admin;
import com.ptit.oj.model.Credentials;
import com.ptit.oj.model.Student;
import com.ptit.oj.model.Teacher;
import com.ptit.oj.model.User;

import java.util.Locale;
import java.util.UUID;

/**
 * Dang ky / dang nhap / phan quyen.
 *
 * Nguyen tac:
 *  - Nguoi dung KHONG bao gio duoc tu chon vai tro: register() luon tao STUDENT.
 *  - Sai ten dang nhap va sai mat khau tra ve cung mot thong bao, de khong lo
 *    tai khoan nao dang ton tai.
 *  - Khong ham nao trong lop nay tra ve hash hoac salt ra ngoai.
 */
public class AuthService {

    // Gioi han do dai khop voi cot trong bang users (02_schema.sql),
    // de loi "qua dai" duoc bao ro rang thay vi loi SQL.
    private static final int MAX_FULL_NAME = 100;      // full_name    VARCHAR(150)
    private static final int MAX_STUDENT_CODE = 30;    // student_code VARCHAR(30)
    private static final int MAX_CLASS_NAME = 50;      // class_name   VARCHAR(50)

    private UserRepository users;
    private PasswordService passwords;
    private SessionService sessions;

    public AuthService(UserRepository users, PasswordService passwords, SessionService sessions) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    /** Ket qua dang nhap: token cua phien + tai khoan tuong ung. */
    public static class LoginResult {
        private String token;
        private User user;

        LoginResult(String token, User user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() { return token; }
        public User getUser() { return user; }
    }

    /** Loi nghiep vu cua tang xac thuc; giao dien hien nguyen van thong bao nay. */
    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- dang ky

    /** Dang ky tai khoan sinh vien. Vai tro luon la STUDENT, khong nhan tu giao dien. */
    public User register(String username, String rawPassword, String fullName,
                                      String studentCode, String className) {
        String cleanUsername = normalizeOrFail(username);
        passwords.validate(rawPassword);

        String cleanFullName = requireText(fullName, "Họ tên", MAX_FULL_NAME);
        String cleanCode = optionalText(studentCode, "Mã sinh viên", MAX_STUDENT_CODE);
        String cleanClass = optionalText(className, "Lớp", MAX_CLASS_NAME);

        if (users.existsByUsername(cleanUsername)) {
            throw new AuthException("Tên đăng nhập \"" + cleanUsername + "\" đã tồn tại");
        }

        Student student = new Student(newUserId(), cleanUsername, cleanFullName, cleanCode, cleanClass);
        users.saveWithCredentials(student, passwords.hash(rawPassword));
        return student;
    }

    /** Tao tai khoan cho vai tro bat ky - chi dung khi tao tai khoan mau. */
    public User createAccount(User user, String rawPassword) {
        passwords.validate(rawPassword);
        if (users.existsByUsername(user.getUsername())) {
            throw new AuthException("Tên đăng nhập \"" + user.getUsername() + "\" đã tồn tại");
        }
        users.saveWithCredentials(user, passwords.hash(rawPassword));
        return user;
    }

    // -------------------------------------------------------------- dang nhap

    /** Dung ten dang nhap va mat khau thi tao phien dang nhap moi; sai thi nem AuthException. */
    public LoginResult login(String username, String rawPassword) {
        if (username == null || username.trim().isEmpty() || rawPassword == null || rawPassword.isEmpty()) {
            throw new AuthException("Thiếu tên đăng nhập hoặc mật khẩu");
        }
        User user = users.findByUsername(username.trim());
        if (user == null) {
            throw new AuthException("Sai tên đăng nhập hoặc mật khẩu");
        }
        Credentials credentials = users.findCredentials(user.getId());
        if (credentials == null || !passwords.matches(rawPassword, credentials)) {
            throw new AuthException("Sai tên đăng nhập hoặc mật khẩu");
        }
        return new LoginResult(sessions.createToken(user.getId()), user);
    }

    /** Tra ve tai khoan gan voi token, hoac null neu token sai / phien da het han. */
    public User authenticate(String token) {
        String userId = sessions.resolve(token);
        if (userId == null) return null;
        return users.findById(userId);
    }

    /** Dang xuat: huy phien dang nhap. */
    public boolean logout(String token) {
        return sessions.invalidate(token);
    }

    // ------------------------------------------------------- khoi tao he thong

    /**
     * Tao 5 tai khoan mau neu chua co tai khoan nao: quan tri vien, giang vien va
     * ba sinh vien, dung chung mot mat khau mau. Cac lan chay sau khong dat lai
     * mat khau cua ai ca.
     */
    public boolean seedSampleUsersIfEmpty(String rawPassword) {
        if (users.count() > 0) return false;
        createAccount(new Admin(newUserId(), "admin", "Quản trị hệ thống"), rawPassword);
        createAccount(new Teacher(newUserId(), "gvthanh", "Lê Minh Thành", "Khoa học máy tính"), rawPassword);
        createAccount(new Student(newUserId(), "hiennm", "Nguyễn Minh Hiển", "B24DCCN199", "D24CQCN01"), rawPassword);
        createAccount(new Student(newUserId(), "lananh", "Trần Lan Anh", "B24DCCN042", "D24CQCN01"), rawPassword);
        createAccount(new Student(newUserId(), "tuanpv", "Phạm Văn Tuấn", "B24DCCN311", "D24CQCN02"), rawPassword);
        return true;
    }

    // -------------------------------------------------------------- tien ich

    private static String newUserId() {
        return "U-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeOrFail(String username) {
        try {
            return PasswordService.normalizeUsername(username);
        } catch (IllegalArgumentException e) {
            throw new AuthException(e.getMessage());
        }
    }

    private String requireText(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) throw new AuthException(label + " không được để trống");
        if (v.length() > max) throw new AuthException(label + " tối đa " + max + " ký tự");
        return v;
    }

    private String optionalText(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.length() > max) throw new AuthException(label + " tối đa " + max + " ký tự");
        return v;
    }
}
