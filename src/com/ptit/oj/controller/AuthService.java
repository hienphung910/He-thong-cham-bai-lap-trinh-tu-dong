package com.ptit.oj.controller;

import com.ptit.oj.dao.UserRepository;
import com.ptit.oj.model.Admin;
import com.ptit.oj.model.Credentials;
import com.ptit.oj.model.Student;
import com.ptit.oj.model.Teacher;
import com.ptit.oj.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dang ky / dang nhap / phan quyen.
 *
 * Nguyen tac:
 *  - Khi tu dang ky, client chi duoc chon STUDENT hoac TEACHER. Tai khoan ADMIN
 *    khong bao gio tao duoc qua register(), chi quan tri vien tao (createByAdmin).
 *  - Hoc sinh tu dang ky la dung duoc ngay. Giang vien tu dang ky thi tai khoan o trang thai
 *    cho duyet: khong dang nhap duoc cho toi khi quan tri vien duyet (approve) hoac tu choi (reject).
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
    /**
     * So tai khoan giang vien cho duyet toi da cung luc. Dang ky la cong khai va moi don co the kem
     * mot file minh chung, nen can mot tran de khong ai lam day o dia bang cach dang ky hang loat.
     */
    private static final int MAX_PENDING = 100;
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
     * Tai khoan giang vien tra ve o trang thai cho duyet (isApproved() == false).
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
            if (!users.supportsApproval()) {
                throw new AuthException(503, "Hệ thống chưa nhận được giảng viên tự đăng ký. Hãy liên hệ quản trị viên để được tạo tài khoản");
            }
            if (pendingUsers().size() >= MAX_PENDING) {
                throw new AuthException(503, "Đang có quá nhiều đăng ký giảng viên chờ duyệt. Hãy thử lại sau");
            }
            String cleanDepartment = optionalText(department, "Bộ môn", MAX_CODE);
            user = new Teacher(newUserId(), cleanUsername, cleanFullName, cleanDepartment);
            user.setApproved(false);
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

    // ------------------------------------------- duyet giang vien tu dang ky

    /** Cac tai khoan dang cho quan tri vien duyet, cu nhat truoc. */
    public List<User> pendingUsers() {
        List<User> pending = new ArrayList<>();
        for (User u : users.findAll()) {
            if (!u.isApproved()) pending.add(u);
        }
        return pending;
    }

    /** Quan tri vien duyet: tu luc nay tai khoan dang nhap duoc. */
    public synchronized User approve(String userId) {
        User user = requirePending(userId);
        user.setApproved(true);
        users.save(user);
        userCache.remove(userId);
        return user;
    }

    /** Quan tri vien tu choi: xoa han tai khoan dang cho duyet, ten dang nhap dung lai duoc. */
    public synchronized User reject(String userId) {
        User user = requirePending(userId);
        users.deleteById(userId);
        sessions.invalidateUser(userId, null);
        userCache.remove(userId);
        return user;
    }

    /** Chi tai khoan DANG CHO DUYET moi duyet / tu choi duoc: hai ham tren khong xoa duoc tai khoan dang dung. */
    private User requirePending(String userId) {
        Optional<User> found = userId == null ? Optional.<User>empty() : users.findById(userId);
        if (!found.isPresent()) throw new AuthException(404, "Không có tài khoản này");
        if (found.get().isApproved()) {
            throw new AuthException(409, "Tài khoản @" + found.get().getUsername() + " không ở trạng thái chờ duyệt");
        }
        return found.get();
    }

    // ------------------------------------------ quan tri vien quan ly tai khoan

    /**
     * Khoa tai khoan: bi dang xuat ngay va khong dang nhap lai duoc. Tai khoan quay ve trang thai
     * "chua duoc phep" giong luc cho duyet; mo khoa bang approve().
     */
    public synchronized User lock(User actor, String userId) {
        User target = requireOther(actor, userId);
        if (!target.isApproved()) {
            throw new AuthException(409, "Tài khoản @" + target.getUsername() + " đã bị khoá hoặc đang chờ duyệt");
        }
        if (!users.supportsApproval()) {
            throw new AuthException(503, "Hệ thống chưa khoá được tài khoản (bảng users thiếu cột approved)");
        }
        requireNotLastAdmin(target);
        target.setApproved(false);
        users.save(target);
        dropSessions(userId);
        return target;
    }

    /** Xoa han tai khoan dang dung. Trong MySQL, bai nop cua ho bi xoa theo (ON DELETE CASCADE). */
    public synchronized User delete(User actor, String userId) {
        User target = requireOther(actor, userId);
        requireNotLastAdmin(target);
        users.deleteById(userId);
        dropSessions(userId);
        return target;
    }

    /** Doi vai tro (STUDENT / TEACHER / ADMIN), giu nguyen ten dang nhap va mat khau. */
    public synchronized User changeRole(User actor, String userId, String role) {
        User target = requireOther(actor, userId);
        if (target.getRoleCode().equals(role)) return target;
        User changed;
        if (User.ROLE_STUDENT.equals(role)) {
            changed = new Student(target.getId(), target.getUsername(), target.getFullName(), "", "");
        } else if (User.ROLE_TEACHER.equals(role)) {
            changed = new Teacher(target.getId(), target.getUsername(), target.getFullName(), "");
        } else if (User.ROLE_ADMIN.equals(role)) {
            changed = new Admin(target.getId(), target.getUsername(), target.getFullName());
        } else {
            throw new AuthException(400, "Vai trò phải là STUDENT, TEACHER hoặc ADMIN");
        }
        requireNotLastAdmin(target);
        Optional<Credentials> credentials = users.findCredentials(userId);
        if (!credentials.isPresent()) throw new AuthException(409, "Tài khoản này chưa có mật khẩu, không đổi vai trò được");
        changed.setApproved(target.isApproved());
        users.saveWithCredentials(changed, credentials.get());
        // Phien cu con giu vai tro cu trong bo nho dem: buoc dang nhap lai de nhan vai tro moi.
        dropSessions(userId);
        return changed;
    }

    /** Tim tai khoan theo ma; rong neu khong co. */
    public Optional<User> findById(String userId) {
        return userId == null ? Optional.<User>empty() : users.findById(userId);
    }

    /** Tai khoan dich cua mot thao tac quan tri: phai ton tai va khong phai chinh nguoi dang thao tac. */
    private User requireOther(User actor, String userId) {
        Optional<User> found = findById(userId);
        if (!found.isPresent()) throw new AuthException(404, "Không có tài khoản này");
        if (found.get().getId().equals(actor.getId())) {
            throw new AuthException(400, "Không tự khoá, xoá hay đổi vai trò tài khoản của chính mình được");
        }
        return found.get();
    }

    /** He thong luon phai con it nhat mot quan tri vien dang dung duoc. */
    private void requireNotLastAdmin(User target) {
        if (!User.ROLE_ADMIN.equals(target.getRoleCode()) || !target.isApproved()) return;
        int admins = 0;
        for (User u : users.findAll()) {
            if (User.ROLE_ADMIN.equals(u.getRoleCode()) && u.isApproved()) admins++;
        }
        if (admins <= 1) throw new AuthException(409, "Đây là quản trị viên duy nhất còn dùng được, không khoá, xoá hay hạ quyền được");
    }

    private void dropSessions(String userId) {
        sessions.invalidateUser(userId, null);
        userCache.remove(userId);
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
        // Kiem tra SAU mat khau: chi nguoi biet dung mat khau moi duoc biet tai khoan dang cho duyet.
        if (!user.isApproved()) {
            throw new AuthException(403, "Tài khoản giảng viên của bạn đang chờ quản trị viên duyệt");
        }
        return new LoginResult(sessions.createToken(user.getId()), user);
    }

    /** Tra ve tai khoan gan voi token, hoac rong neu token sai / het han. */
    public Optional<User> authenticate(String token) {
        Optional<String> userId = sessions.resolve(token);
        if (!userId.isPresent()) return Optional.empty();
        long now = System.currentTimeMillis();
        CachedUser cached = userCache.get(userId.get());
        if (cached != null && cached.until > now) return Optional.of(cached.user);
        Optional<User> found = users.findById(userId.get());
        if (found.isPresent() && !found.get().isApproved()) return Optional.empty();
        if (found.isPresent()) userCache.put(userId.get(), new CachedUser(found.get(), now + USER_CACHE_MS));
        else userCache.remove(userId.get());
        return found;
    }

    /**
     * Moi request deu phai doi token thanh tai khoan. Trinh duyet hoi thong bao vai giay mot lan,
     * nen neu lan nao cung tra CSDL thi voi MySQL o xa (moi lan mot ket noi moi) se rat ton.
     * Giu tai khoan vua tra trong 60 giay: thong tin tai khoan (ten, vai tro) gan nhu khong doi,
     * con token het han hay dang xuat van co hieu luc ngay vi SessionService duoc hoi truoc.
     */
    private static final long USER_CACHE_MS = 60_000;
    private final Map<String, CachedUser> userCache = new ConcurrentHashMap<>();

    private static final class CachedUser {
        private final User user;
        private final long until;

        CachedUser(User user, long until) {
            this.user = user;
            this.until = until;
        }
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
