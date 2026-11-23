package com.ptit.oj.controller;

import com.ptit.oj.dao.UserRepository;
import com.ptit.oj.model.Admin;
import com.ptit.oj.model.Credentials;
import com.ptit.oj.model.Student;
import com.ptit.oj.model.Teacher;
import com.ptit.oj.model.User;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Dang ky / dang nhap / phan quyen.
 *
 * Nguyen tac:
 *  - Khi tu dang ky, client chi duoc chon STUDENT hoac TEACHER. Tai khoan ADMIN
 *    khong bao gio tao duoc qua register(), chi quan tri vien tao (createByAdmin).
 *  - Sai username va sai mat khau tra ve cung mot thong bao, de khong lo
 *    tai khoan nao dang ton tai (user enumeration).
 *  - Khong ham nao trong lop nay tra ve hash hoac salt ra ngoai.
 */
public class AuthService {

    /** Bien moi truong de dat mat khau admin lan dau. */
    public static final String ADMIN_PASSWORD_ENV = "OJ_ADMIN_PASSWORD";
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    private static final int MAX_FULL_NAME = 100;
    private static final int MAX_CODE = 40;
    /** Khop voi cot users.student_code VARCHAR(30). */
    private static final int MAX_STUDENT_CODE = 30;

    private final UserRepository users;
    private final PasswordService passwords;
    private final SessionService sessions;

    public AuthService(UserRepository users, PasswordService passwords, SessionService sessions) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    /** Ket qua dang nhap: token + tai khoan tuong ung. */
    public static final class LoginResult {
        private final String token;
        private final User user;

        LoginResult(String token, User user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() { return token; }
        public User getUser() { return user; }
    }

    /** Loi nghiep vu cua tang xac thuc, kem goi y ma HTTP phu hop. */
    public static class AuthException extends RuntimeException {
        private final int httpStatus;

        public AuthException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() { return httpStatus; }
    }

    // ---------------------------------------------------------------- dang ky

    /**
     * Tu dang ky tai khoan. role chi nhan STUDENT hoac TEACHER (de trong thi coi la STUDENT);
     * gui ADMIN hay gia tri la deu bi tu choi.
     * Hoc sinh dung studentCode + className, giang vien dung department.
     */
    public synchronized User register(String role, String username, String rawPassword, String fullName,
                                      String studentCode, String className, String department) {
        String cleanUsername = normalizeOrFail(username);
        passwords.validate(rawPassword);
        String cleanFullName = requireText(fullName, "Họ tên", MAX_FULL_NAME);

        // Tao dung lop con theo vai tro duoc chon.
        User user;
        if (role == null || role.isEmpty() || User.ROLE_STUDENT.equals(role)) {
            String cleanCode = optionalText(studentCode, "Mã sinh viên", MAX_STUDENT_CODE);
            String cleanClass = optionalText(className, "Lớp", MAX_CODE);
            user = new Student(newUserId(), cleanUsername, cleanFullName, cleanCode, cleanClass);
        } else if (User.ROLE_TEACHER.equals(role)) {
            String cleanDepartment = optionalText(department, "Bộ môn", MAX_CODE);
            user = new Teacher(newUserId(), cleanUsername, cleanFullName, cleanDepartment);
        } else {
            throw new AuthException(400, "Chỉ được tự đăng ký vai trò Học sinh hoặc Giảng viên");
        }

        if (users.existsByUsername(cleanUsername)) {
            throw new AuthException(409, "Tên đăng nhập \"" + cleanUsername + "\" đã tồn tại");
        }
        users.saveWithCredentials(user, passwords.hash(rawPassword));
        return user;
    }

    /** Tao tai khoan cho vai tro bat ky - chi dung tu phia may chu (seed, bootstrap). */
    public synchronized User createAccount(User user, String rawPassword) {
        passwords.validate(rawPassword);
        if (users.existsByUsername(user.getUsername())) {
            throw new AuthException(409, "Tên đăng nhập \"" + user.getUsername() + "\" đã tồn tại");
        }
        users.saveWithCredentials(user, passwords.hash(rawPassword));
        return user;
    }

    /**
     * Quan tri vien tao tai khoan voi vai tro bat ky (Module 1, 35).
     * extra la "lop" doi voi hoc sinh, "bo mon" doi voi giang vien.
     */
    public synchronized User createByAdmin(String role, String username, String rawPassword,
                                           String fullName, String extra) {
        String cleanUsername = normalizeOrFail(username);
        String cleanFullName = requireText(fullName, "Họ tên", MAX_FULL_NAME);
        String cleanExtra = optionalText(extra, "Lớp / bộ môn", MAX_CODE);

        User user;
        if (User.ROLE_STUDENT.equals(role)) {
            user = new Student(newUserId(), cleanUsername, cleanFullName, "", cleanExtra);
        } else if (User.ROLE_TEACHER.equals(role)) {
            user = new Teacher(newUserId(), cleanUsername, cleanFullName, cleanExtra);
        } else if (User.ROLE_ADMIN.equals(role)) {
            user = new Admin(newUserId(), cleanUsername, cleanFullName);
        } else {
            throw new AuthException(400, "Vai trò phải là STUDENT, TEACHER hoặc ADMIN");
        }
        return createAccount(user, rawPassword);
    }

    /** Doi mat khau cua chinh minh: phai nhap dung mat khau hien tai. */
    public synchronized void changePassword(User user, String oldPassword, String newPassword, String currentToken) {
        Optional<Credentials> credentials = users.findCredentials(user.getId());
        if (oldPassword == null || !credentials.isPresent() || !passwords.matches(oldPassword, credentials.get())) {
            throw new AuthException(400, "Mật khẩu hiện tại không đúng");
        }
        passwords.validate(newPassword);
        users.saveWithCredentials(user, passwords.hash(newPassword));
        // Dang xuat moi thiet bi khac: ai dang giu token cu khong dung tiep duoc.
        sessions.invalidateUser(user.getId(), currentToken);
    }

    // -------------------------------------------------------------- dang nhap

    private Credentials dummy;

    private synchronized Credentials dummyCredentials() {
        if (dummy == null) dummy = passwords.hash("mat-khau-gia-de-can-bang-thoi-gian");
        return dummy;
    }

    public LoginResult login(String username, String rawPassword) {
        if (username == null || username.trim().isEmpty() || rawPassword == null || rawPassword.isEmpty()) {
            throw new AuthException(400, "Thiếu tên đăng nhập hoặc mật khẩu");
        }
        // Ten dang nhap hop le dai toi da 32 ky tu: ten dai hon chac chan khong ton tai.
        Optional<User> found = username.trim().length() > 64
                ? Optional.empty() : users.findByUsername(username.trim());
        if (!found.isPresent()) {
            // Van bam mat khau mot lan de thoi gian tra loi khong tiet lo ten nao co that.
            passwords.matches(rawPassword, dummyCredentials());
            throw new AuthException(401, "Sai tên đăng nhập hoặc mật khẩu");
        }
        User user = found.get();
        Optional<Credentials> credentials = users.findCredentials(user.getId());
        if (!credentials.isPresent() || !passwords.matches(rawPassword, credentials.get())) {
            throw new AuthException(401, "Sai tên đăng nhập hoặc mật khẩu");
        }
        return new LoginResult(sessions.createToken(user.getId()), user);
    }

    /** Tra ve tai khoan gan voi token, hoac rong neu token sai / het han. */
    public Optional<User> authenticate(String token) {
        return sessions.resolve(token).flatMap(users::findById);
    }

    public boolean logout(String token) {
        return sessions.invalidate(token);
    }

    // ------------------------------------------------------- khoi tao he thong

    /** Ket qua tao tai khoan admin lan dau. */
    public static final class BootstrapResult {
        private final User admin;
        private final String generatedPassword;   // null neu lay tu bien moi truong
        private final boolean created;

        BootstrapResult(User admin, String generatedPassword, boolean created) {
            this.admin = admin;
            this.generatedPassword = generatedPassword;
            this.created = created;
        }

        public User getAdmin() { return admin; }
        public boolean isCreated() { return created; }
        /** Chi khac null dung mot lan, ngay sau khi tao tai khoan. */
        public String getGeneratedPassword() { return generatedPassword; }
    }

    /**
     * Tao tai khoan ADMIN neu CSDL chua co admin nao.
     *
     * Mat khau lay tu bien moi truong OJ_ADMIN_PASSWORD; neu khong dat thi sinh
     * ngau nhien va tra ve DUY NHAT lan nay de nguoi goi in ra terminal. CSDL chi
     * luu hash. Cac lan chay sau khong bao gio dat lai mat khau admin.
     */
    public synchronized BootstrapResult bootstrapAdmin() {
        for (User u : users.findAll()) {
            if (User.ROLE_ADMIN.equals(u.getRoleCode())) {
                return new BootstrapResult(u, null, false);
            }
        }

        String fromEnv = com.ptit.oj.dao.DatabaseConfig.setting(ADMIN_PASSWORD_ENV);
        boolean generated = fromEnv == null || fromEnv.trim().isEmpty();
        String password = generated ? passwords.generateRandomPassword() : fromEnv;
        try {
            passwords.validate(password);
        } catch (IllegalArgumentException e) {
            throw new AuthException(400, "Biến môi trường " + ADMIN_PASSWORD_ENV + " không hợp lệ: " + e.getMessage());
        }

        String username = DEFAULT_ADMIN_USERNAME;
        if (users.existsByUsername(username)) {
            // Da co tai khoan ten "admin" nhung khong phai vai tro ADMIN -> doi ten khac.
            username = "admin_" + UUID.randomUUID().toString().substring(0, 6);
        }
        Admin admin = new Admin(newUserId(), username, "Quản trị hệ thống");
        users.saveWithCredentials(admin, passwords.hash(password));
        return new BootstrapResult(admin, generated ? password : null, true);
    }

    /** Tao tai khoan mau neu CSDL con trong - giu lai bo du lieu demo cua bai tap lon. */
    public synchronized boolean seedSampleUsersIfEmpty(String rawPassword) {
        if (users.count() > 0) return false;
        createAccount(new Student(newUserId(), "hiennm", "Nguyễn Minh Hiển", "B24DCCN199", "D24CQCN01"), rawPassword);
        createAccount(new Student(newUserId(), "lananh", "Trần Lan Anh", "B24DCCN042", "D24CQCN01"), rawPassword);
        createAccount(new Student(newUserId(), "tuanpv", "Phạm Văn Tuấn", "B24DCCN311", "D24CQCN02"), rawPassword);
        createAccount(new Teacher(newUserId(), "gvthanh", "Lê Minh Thành", "Khoa học máy tính"), rawPassword);
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
            throw new AuthException(400, e.getMessage());
        }
    }

    private String requireText(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) throw new AuthException(400, label + " không được để trống");
        if (v.length() > max) throw new AuthException(400, label + " tối đa " + max + " ký tự");
        return v;
    }

    private String optionalText(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.length() > max) throw new AuthException(400, label + " tối đa " + max + " ký tự");
        return v;
    }
}
