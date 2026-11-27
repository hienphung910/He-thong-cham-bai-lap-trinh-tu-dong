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
 *    khong bao gio tao duoc qua register(): tai khoan quan tri dau tien do bootstrapAdmin() tao,
 *    muon them thi quan tri vien doi vai tro cua mot tai khoan da co (changeRole).
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

    /**
     * Nhap sai MAT KHAU HIEN TAI khi doi mat khau / doi email. Tach rieng de tang API dem so lan sai
     * va tam khoa (nguoi cam duoc mot phien dang nhap khong duoc do mat khau khong gioi han).
     */
    public static class WrongPasswordException extends AuthException {
        public WrongPasswordException() {
            super(400, "Mật khẩu hiện tại không đúng");
        }
    }

    // ---------------------------------------------------------------- dang ky

    /**
     * Tu dang ky tai khoan. role chi nhan STUDENT hoac TEACHER (de trong thi coi la STUDENT);
     * gui ADMIN hay gia tri la deu bi tu choi.
     * Hoc sinh dung studentCode + className, giang vien dung department.
     * Tai khoan giang vien tra ve o trang thai cho duyet (isApproved() == false).
     */
    public synchronized User register(String role, String username, String rawPassword, String fullName,
                                      String studentCode, String className, String department, String email,
                                      boolean saveEmail) {
        String cleanUsername = normalizeOrFail(username);
        // Ten de bi nham la tai khoan cua he thong: khong cho tu dang ky (tai khoan quan tri do bootstrapAdmin tao).
        if (RESERVED_USERNAMES.contains(cleanUsername.toLowerCase(Locale.ROOT))) {
            throw new AuthException(400, "Tên đăng nhập \"" + cleanUsername + "\" không dùng được, hãy chọn tên khác");
        }
        passwords.validateChosen(rawPassword, cleanUsername);
        String cleanFullName = requireText(fullName, "Họ tên", MAX_FULL_NAME);
        if (cleanFullName.length() < 2 || !FULL_NAME.matcher(cleanFullName).matches()) {
            throw new AuthException(400, "Họ tên chỉ gồm chữ cái và khoảng trắng, ít nhất 2 ký tự");
        }
        // Email bat buoc khi dang ky: do la duong duy nhat de tu lay lai mat khau. Kho khong luu duoc
        // email (CSDL cu thieu cot) thi bo qua, khong chan viec dang ky.
        String cleanEmail = users.supportsEmail() ? normalizeEmail(email) : "";

        // Tao dung lop con theo vai tro duoc chon.
        User user;
        if (role == null || role.isEmpty() || User.ROLE_STUDENT.equals(role)) {
            String cleanCode = optionalText(studentCode, "Mã sinh viên", MAX_STUDENT_CODE);
            if (!cleanCode.isEmpty() && !STUDENT_CODE.matcher(cleanCode).matches()) {
                throw new AuthException(400, "Mã sinh viên chỉ gồm chữ không dấu, số, dấu chấm, gạch ngang hoặc gạch dưới");
            }
            String cleanClass = optionalText(className, "Lớp", MAX_CODE);
            requirePlainLabel(cleanClass, "Lớp");
            user = new Student(newUserId(), cleanUsername, cleanFullName, cleanCode, cleanClass);
        } else if (User.ROLE_TEACHER.equals(role)) {
            if (!users.supportsApproval()) {
                throw new AuthException(503, "Hệ thống chưa nhận được giảng viên tự đăng ký. Hãy liên hệ quản trị viên để được tạo tài khoản");
            }
            if (pendingUsers().size() >= MAX_PENDING) {
                throw new AuthException(503, "Đang có quá nhiều đăng ký giảng viên chờ duyệt. Hãy thử lại sau");
            }
            String cleanDepartment = optionalText(department, "Bộ môn", MAX_CODE);
            requirePlainLabel(cleanDepartment, "Bộ môn");
            user = new Teacher(newUserId(), cleanUsername, cleanFullName, cleanDepartment);
            user.setApproved(false);
        } else {
            throw new AuthException(400, "Chỉ được tự đăng ký vai trò Học sinh hoặc Giảng viên");
        }

        if (users.existsByUsername(cleanUsername)) {
            throw new AuthException(409, "Tên đăng nhập \"" + cleanUsername + "\" đã tồn tại");
        }
        if (!cleanEmail.isEmpty() && users.findByEmail(cleanEmail).isPresent()) {
            throw new AuthException(409, "Email này đã được dùng cho một tài khoản khác");
        }
        // saveEmail = false: email con phai xac minh qua thu (EmailVerificationService) roi moi duoc ghi
        // vao tai khoan bang confirmEmail().
        if (saveEmail) user.setEmail(cleanEmail);
        users.saveWithCredentials(user, passwords.hash(rawPassword));
        return user;
    }

    /** Email da chuan hoa de gui thu xac minh; "" neu kho khong luu duoc email (khi do bo qua viec xac minh). */
    public String emailToVerify(String email) {
        return users.supportsEmail() ? normalizeEmail(email) : "";
    }

    // ------------------------------------------------- email va dat lai mat khau

    /** Do dai toi da cua email; khop voi cot users.email VARCHAR(190). */
    private static final int MAX_EMAIL = 190;
    /** Mot ten, mot @, mot ten mien co dau cham; khong khoang trang. Du de bat loi go nham. */
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile(
            "^[A-Za-z0-9_%+-]+(\\.[A-Za-z0-9_%+-]+)*@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?"
          + "(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$");

    /** Ten tu dang ky khong duoc dung, vi de bi nham la tai khoan cua he thong. */
    private static final java.util.Set<String> RESERVED_USERNAMES = new java.util.HashSet<>(java.util.Arrays.asList(
            "admin", "administrator", "root", "system", "support", "codehub", "quantri", "quantrivien", "moderator"));
    /** Ho ten: chu cai (ca tieng Viet co dau), khoang trang, dau cham, nhay don, gach ngang; bat dau bang chu cai. */
    private static final java.util.regex.Pattern FULL_NAME =
            java.util.regex.Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$");
    private static final java.util.regex.Pattern STUDENT_CODE = java.util.regex.Pattern.compile("^[A-Za-z0-9._-]+$");
    /** Ten lop / bo mon: chu, so, khoang trang va vai dau thong dung. */
    private static final java.util.regex.Pattern PLAIN_LABEL =
            java.util.regex.Pattern.compile("^[\\p{L}\\p{M}\\p{N} .,&/_-]*$");

    private static void requirePlainLabel(String value, String label) {
        if (!PLAIN_LABEL.matcher(value).matches()) {
            throw new AuthException(400, label + " chỉ gồm chữ, số, khoảng trắng và các dấu . , & / - _");
        }
    }

    /** Dua email ve dang chu thuong, nem 400 neu thieu hoac sai dang. */
    public static String normalizeEmail(String email) {
        String clean = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) throw new AuthException(400, "Email không được để trống");
        if (clean.length() > MAX_EMAIL) throw new AuthException(400, "Email tối đa " + MAX_EMAIL + " ký tự");
        int at = clean.indexOf('@');
        if (at < 1 || at > 64 || !EMAIL.matcher(clean).matches()) {
            throw new AuthException(400, "Email không hợp lệ (ví dụ đúng: ten@example.com)");
        }
        return clean;
    }

    /** Doi email khoi phuc cua chinh minh: phai nhap dung mat khau hien tai, vi ai giu email la lay duoc tai khoan. */
    public synchronized User changeEmail(User user, String password, String email) {
        return confirmEmail(user.getId(), checkNewEmail(user, password, email));
    }

    /**
     * Buoc 1 cua viec doi email: kiem tra mat khau hien tai va email moi, CHUA ghi gi.
     * @return email da chuan hoa, de gui thu xac minh (hoac ghi ngay bang confirmEmail neu khong gui duoc thu)
     */
    public synchronized String checkNewEmail(User user, String password, String email) {
        if (!users.supportsEmail()) {
            throw new AuthException(503, "Hệ thống chưa lưu được email (bảng users thiếu cột email)");
        }
        Optional<Credentials> credentials = users.findCredentials(user.getId());
        if (password == null || !credentials.isPresent() || !passwords.matches(password, credentials.get())) {
            throw new WrongPasswordException();
        }
        String cleanEmail = normalizeEmail(email);
        Optional<User> owner = users.findByEmail(cleanEmail);
        if (owner.isPresent() && !owner.get().getId().equals(user.getId())) {
            throw new AuthException(409, "Email này đã được dùng cho một tài khoản khác");
        }
        if (owner.isPresent()) throw new AuthException(400, "Đây đã là email khôi phục của bạn");
        return cleanEmail;
    }

    /**
     * Buoc 2: ghi email vao tai khoan - sau khi nguoi dung bam lien ket xac minh, hoac ngay sau buoc 1
     * neu may chu khong gui duoc thu. Kiem tra trung lan nua vi tu luc khai toi luc xac minh co the
     * tai khoan khac da lay dia chi nay.
     */
    public synchronized User confirmEmail(String userId, String cleanEmail) {
        Optional<User> found = findById(userId);
        Optional<Credentials> credentials = users.findCredentials(userId);
        if (!found.isPresent() || !credentials.isPresent()) {
            throw new AuthException(400, "Liên kết xác minh không hợp lệ hoặc đã hết hạn");
        }
        Optional<User> owner = users.findByEmail(cleanEmail);
        if (owner.isPresent() && !owner.get().getId().equals(userId)) {
            throw new AuthException(409, "Email này đã được dùng cho một tài khoản khác");
        }
        User fresh = found.get();
        fresh.setEmail(cleanEmail);
        users.saveWithCredentials(fresh, credentials.get());
        userCache.remove(userId);
        return fresh;
    }

    /**
     * Tai khoan se nhan thu dat lai mat khau: tim theo ten dang nhap, hoac theo email neu chuoi co "@".
     * Rong neu khong co, chua khai email, hoac dang bi khoa / cho duyet. Nguoi goi KHONG duoc de lo
     * ket qua nay ra ngoai (luon tra loi nhu nhau).
     */
    public Optional<User> findForReset(String account) {
        String key = account == null ? "" : account.trim();
        if (key.isEmpty() || key.length() > MAX_EMAIL) return Optional.empty();
        Optional<User> found = key.indexOf('@') >= 0 ? users.findByEmail(key) : users.findByUsername(key);
        if (!found.isPresent() || found.get().getEmail().isEmpty() || !found.get().isApproved()) return Optional.empty();
        return found;
    }

    /** Kiem tra mat khau moi co dat yeu cau khong (nem IllegalArgumentException neu khong) ma chua doi gi. */
    public void checkPassword(String rawPassword, String userId) {
        Optional<User> owner = findById(userId);
        passwords.validateChosen(rawPassword, owner.isPresent() ? owner.get().getUsername() : null);
    }

    /**
     * Dat mat khau moi sau khi nguoi dung chung minh minh giu email (xem PasswordResetService).
     * Moi phien dang nhap cu bi huy: ai dang giu token cu khong dung tiep duoc.
     */
    public synchronized User resetPassword(String userId, String newPassword) {
        Optional<User> found = findById(userId);
        passwords.validateChosen(newPassword, found.isPresent() ? found.get().getUsername() : null);
        if (!found.isPresent() || !found.get().isApproved()) {
            throw new AuthException(400, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        users.saveWithCredentials(found.get(), passwords.hash(newPassword));
        dropSessions(userId);
        return found.get();
    }

    // ------------------------------------- tai khoan hoc sinh do giao vien tao

    /**
     * Giao vien tao tai khoan hoc sinh cho lop pho thong: hoc sinh khong can email, giao vien giu va dat
     * lai mat khau. Ten dang nhap sinh tu ho ten ("Nguyễn Văn An" -> "annv", trung thi "annv2"...).
     */
    public synchronized User createManagedStudent(String fullName, String className, String rawPassword) {
        String cleanFullName = requireText(fullName, "Họ tên", MAX_FULL_NAME).replaceAll("\\s+", " ");
        if (cleanFullName.length() < 2 || !FULL_NAME.matcher(cleanFullName).matches()) {
            throw new AuthException(400, "Họ tên \"" + cleanFullName + "\" chỉ được gồm chữ cái và khoảng trắng");
        }
        String cleanClass = optionalText(className, "Lớp", MAX_CODE);
        requirePlainLabel(cleanClass, "Lớp");
        String username = suggestUsername(cleanFullName);
        passwords.validateChosen(rawPassword, username);
        User user = new Student(newUserId(), username, cleanFullName, "", cleanClass);
        users.saveWithCredentials(user, passwords.hash(rawPassword));
        return user;
    }

    /** Loi cua mot ho ten khi tao tai khoan hang loat (null neu dung): chu cai va khoang trang, it nhat 2 tu. */
    public String checkStudentName(String fullName) {
        String clean = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        if (clean.length() > MAX_FULL_NAME) return "họ tên tối đa " + MAX_FULL_NAME + " ký tự";
        if (!FULL_NAME.matcher(clean).matches()) return "chỉ được gồm chữ cái và khoảng trắng";
        if (clean.split(" ").length < 2) return "cần ghi đủ họ và tên";
        return null;
    }

    /** Ten dang nhap con trong tu ho ten: ten + chu cai dau cua ho va dem, khong dau, them so neu trung. */
    String suggestUsername(String fullName) {
        String folded = java.text.Normalizer.normalize(fullName, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        String[] parts = folded.replaceAll("[^a-z ]", " ").trim().split("\\s+");
        StringBuilder base = new StringBuilder(parts[parts.length - 1]);
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) base.append(parts[i].charAt(0));
        }
        while (base.length() < 3) base.append('x');
        String stem = base.length() > 24 ? base.substring(0, 24) : base.toString();
        for (int n = 1; n < 100000; n++) {
            String candidate = n == 1 ? stem : stem + n;
            if (!RESERVED_USERNAMES.contains(candidate) && !users.existsByUsername(candidate)) return candidate;
        }
        throw new AuthException(409, "Không sinh được tên đăng nhập cho " + fullName);
    }

    /** Dat mat khau moi cho mot tai khoan (giao vien dat lai cho hoc sinh minh quan ly); dang xuat moi phien. */
    public synchronized void setPasswordFor(String userId, String rawPassword) {
        User user = findById(userId).orElseThrow(() -> new AuthException(404, "Không có tài khoản này"));
        passwords.validateChosen(rawPassword, user.getUsername());
        users.saveWithCredentials(user, passwords.hash(rawPassword));
        dropSessions(userId);
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
        for (java.util.function.Consumer<String> l : deleteListeners) l.accept(userId);
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
        for (java.util.function.Consumer<String> l : deleteListeners) l.accept(userId);
        return target;
    }

    /** Lang nghe tai khoan bi xoa / doi vai tro - bo nho dem bai nop (CachedSubmissionRepository) can biet. */
    private final List<java.util.function.Consumer<String>> deleteListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<java.util.function.Consumer<String>> changeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void onUserDeleted(java.util.function.Consumer<String> listener) { deleteListeners.add(listener); }
    public void onUserChanged(java.util.function.Consumer<String> listener) { changeListeners.add(listener); }

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
        changed.setEmail(target.getEmail());
        users.saveWithCredentials(changed, credentials.get());
        // Phien cu con giu vai tro cu trong bo nho dem: buoc dang nhap lai de nhan vai tro moi.
        dropSessions(userId);
        for (java.util.function.Consumer<String> l : changeListeners) l.accept(userId);
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
            throw new WrongPasswordException();
        }
        passwords.validateChosen(newPassword, user.getUsername());
        if (newPassword.equals(oldPassword)) {
            throw new AuthException(400, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
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

    /** remember = "Ghi nho dang nhap": phien song 30 ngay ke tu lan dung gan nhat thay vi 12 gio. */
    public LoginResult login(String username, String rawPassword, boolean remember) {
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
            // Hoc sinh va quan tri vien khong qua buoc duyet: chua duoc phep nghia la da bi khoa.
            // Giang vien thi co the dang cho duyet hoac da bi khoa (xem AccountApi.statusOf).
            throw new AuthException(403, User.ROLE_TEACHER.equals(user.getRoleCode())
                    ? "Tài khoản giảng viên của bạn đang chờ quản trị viên duyệt hoặc đã bị khoá"
                    : "Tài khoản của bạn đang bị khoá. Hãy liên hệ quản trị viên");
        }
        return new LoginResult(sessions.createToken(user.getId(), remember), user);
    }

    /** Dang xuat moi thiet bi khac cua tai khoan nay, giu lai phien dang dung. */
    public void logoutOthers(User user, String currentToken) {
        sessions.invalidateUser(user.getId(), currentToken);
        userCache.remove(user.getId());
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

    /** Mat khau ngau nhien du manh - dung cho tai khoan mau cua che do chay thu. */
    public String newRandomPassword() {
        return passwords.generateRandomPassword();
    }

    /** Tao tai khoan mau neu CSDL con trong - giu lai bo du lieu demo cua bai tap lon. */
    public synchronized boolean seedSampleUsersIfEmpty(String rawPassword) {
        if (users.count() > 0) return false;
        // Ma cua tai khoan mau la CO DINH (khong ngau nhien): o che do --no-db tai khoan duoc tao lai moi
        // lan khoi dong, ma bai kho rieng tren o dia lai ghi chu bai bang ma tai khoan - ma doi thi giang
        // vien mau mat quyen voi bai minh da tao o lan chay truoc.
        createAccount(new Student(sampleUserId("hiennm"), "hiennm", "Nguyễn Minh Hiển", "B24DCCN199", "D24CQCN01"), rawPassword);
        createAccount(new Student(sampleUserId("lananh"), "lananh", "Trần Lan Anh", "B24DCCN042", "D24CQCN01"), rawPassword);
        createAccount(new Student(sampleUserId("tuanpv"), "tuanpv", "Phạm Văn Tuấn", "B24DCCN311", "D24CQCN02"), rawPassword);
        createAccount(new Teacher(sampleUserId("gvthanh"), "gvthanh", "Lê Minh Thành", "Khoa học máy tính"), rawPassword);
        return true;
    }

    private static String sampleUserId(String username) {
        return "U-SAMPLE-" + username.toUpperCase(Locale.ROOT);
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
