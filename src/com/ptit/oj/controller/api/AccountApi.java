package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.AuthService;
import com.ptit.oj.controller.EvidenceStore;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Student;
import com.ptit.oj.model.Teacher;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 1 (tai khoan) va 35 (giang vien):
 *   GET  /api/users            - danh sach tai khoan (chi quan tri vien)
 *   POST /api/admin/users/{id}/approve - duyet giang vien tu dang ky
 *   POST /api/admin/users/{id}/reject  - tu choi: xoa tai khoan dang cho duyet
 *   GET  /api/admin/users/{id}/evidence - file minh chung giang vien gui kem khi dang ky
 *   POST /api/admin/users/{id}/lock    - khoa tai khoan dang dung (mo lai bang approve)
 *   PUT  /api/admin/users/{id}/role    - doi vai tro
 *   DELETE /api/admin/users/{id}       - xoa han tai khoan
 *   POST /api/auth/password    - doi mat khau cua chinh minh
 *   PUT  /api/auth/email       - doi email khoi phuc cua chinh minh (phai nhap mat khau hien tai);
 *                                email moi chi co hieu luc sau khi xac minh qua thu
 *   POST /api/auth/email/resend - gui lai thu xac minh cho email dang cho
 */
public class AccountApi extends ApiModule {

    public AccountApi(Platform platform) {
        super(platform);
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "users")) {
            // Danh sach moi tai khoan (ca quan tri vien, ca trang thai khoa / cho duyet): chi quan tri vien.
            req.admin();
            List<String> items = new ArrayList<>();
            for (User u : judge.getUsers()) {
                Json.Obj item = userJson(u).put("detail", detailOf(u)).put("approved", u.isApproved());
                // Chi tai khoan dang cho duyet moi con file minh chung (duyet / tu choi xong la xoa).
                boolean hasEvidence = !u.isApproved() && platform.getEvidence().exists(u.getId());
                if (!u.isApproved()) item = item.put("hasEvidence", hasEvidence);
                item = item.put("status", statusOf(u, hasEvidence));
                items.add(item.build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "admin", "users", "*", "approve")) {
            req.admin();
            User approved = judge.getAuth().approve(req.arg(0));
            platform.getResets().revokeUser(approved.getId());
            platform.getEvidence().delete(approved.getId());
            req.send(userJson(approved).put("detail", detailOf(approved)).put("approved", true).put("status", "ACTIVE").build());
            return true;
        }
        if (req.is("POST", "admin", "users", "*", "reject")) {
            req.admin();
            requireNoLedClassroom(req.arg(0));       // tai khoan bi khoa co the dang phu trach lop
            User rejected = judge.getAuth().reject(req.arg(0));
            platform.getSchool().removeEverywhere(rejected.getId());
            platform.getEvidence().delete(rejected.getId());
            platform.revokeMailLinks(rejected.getId());
            req.sendOk();
            return true;
        }
        if (req.is("POST", "admin", "users", "*", "lock")) {
            User admin = req.admin();
            User locked = judge.getAuth().lock(admin, req.arg(0));
            platform.getResets().revokeUser(locked.getId());
            req.send(userJson(locked).put("detail", detailOf(locked)).put("approved", false).put("status", "LOCKED").build());
            return true;
        }
        if (req.is("PUT", "admin", "users", "*", "role")) {
            User admin = req.admin();
            String role = req.body().getString("role");
            User current = judge.getAuth().findById(req.arg(0))
                    .orElseThrow(() -> ApiException.notFound("Không có tài khoản này"));
            // Thoi lam giang vien thi khong con phu trach lop nao duoc.
            if (User.ROLE_TEACHER.equals(current.getRoleCode()) && !User.ROLE_TEACHER.equals(role)) {
                requireNoLedClassroom(current.getId());
            }
            User changed = judge.getAuth().changeRole(admin, current.getId(), role);
            platform.getResets().revokeUser(current.getId());
            // Tu cach thanh vien cu (hoc sinh cua lop) khong con hop voi vai tro moi.
            if (changed != current) platform.getSchool().removeEverywhere(changed.getId());
            req.send(userJson(changed).put("detail", detailOf(changed)).put("approved", changed.isApproved()).build());
            return true;
        }
        if (req.is("DELETE", "admin", "users", "*")) {
            User admin = req.admin();
            requireNoLedClassroom(req.arg(0));
            User deleted = judge.getAuth().delete(admin, req.arg(0));
            platform.getSchool().removeEverywhere(deleted.getId());
            platform.getEvidence().delete(deleted.getId());
            platform.revokeMailLinks(deleted.getId());
            req.sendOk();
            return true;
        }
        if (req.is("GET", "admin", "users", "*", "evidence")) {
            req.admin();
            EvidenceStore.Evidence evidence = platform.getEvidence().find(req.arg(0))
                    .orElseThrow(() -> ApiException.notFound("Tài khoản này không có file minh chứng"));
            // Tra ve dang base64 trong JSON: chi quan tri vien da dang nhap moi doc duoc, va trinh duyet
            // khong bao gio mo thang file nguoi la gui len nhu mot trang cua he thong.
            req.send(Json.obj()
                    .put("contentType", evidence.getContentType())
                    .put("data", evidence.toBase64())
                    .build());
            return true;
        }
        if (req.is("POST", "auth", "password")) {
            User user = req.user();
            Json.Value body = req.body();
            requirePasswordTries(user);
            try {
                judge.getAuth().changePassword(user, body.getString("oldPassword"), body.getString("newPassword"),
                        req.token());
            } catch (AuthService.WrongPasswordException e) {
                platform.getLimiter().hit(passwordTryKey(user), PASSWORD_TRY_WINDOW_MS);
                throw e;
            }
            platform.getLimiter().reset(passwordTryKey(user));
            // Lien ket dat lai mat khau xin truoc do khong con dung duoc nua.
            platform.getResets().revokeUser(user.getId());
            req.sendOk();
            return true;
        }
        if (req.is("PUT", "auth", "email")) {
            User user = req.user();
            Json.Value body = req.body();
            requirePasswordTries(user);
            String email;
            try {
                email = judge.getAuth().checkNewEmail(user, body.getString("password", ""), body.getString("email", ""));
            } catch (AuthService.WrongPasswordException e) {
                platform.getLimiter().hit(passwordTryKey(user), PASSWORD_TRY_WINDOW_MS);
                throw e;
            }
            platform.getLimiter().reset(passwordTryKey(user));
            if (platform.getAccountMailer().isEnabled()) {
                // Email moi chua co hieu luc: gui thu xac minh toi chinh dia chi do, email cu van dung cho toi luc xac minh.
                long wait = platform.getAccountMailer().sendVerification(user, email);
                if (wait > 0) throw new ApiException(429, "Bạn vừa yêu cầu gửi thư xác minh. Hãy đợi " + wait + " giây rồi thử lại");
                req.send(Json.obj().put("email", user.getEmail()).put("pendingEmail", email).build());
            } else {
                // May chu khong gui duoc thu thi khong co cach xac minh: ghi luon.
                User changed = judge.getAuth().confirmEmail(user.getId(), email);
                platform.revokeMailLinks(user.getId());
                req.send(Json.obj().put("email", changed.getEmail()).put("pendingEmail", "").build());
            }
            return true;
        }
        if (req.is("POST", "auth", "email", "resend")) {
            User user = req.user();
            String pending = platform.getVerifications().pendingEmailOf(user.getId());
            if (pending.isEmpty()) throw ApiException.notFound("Không có email nào đang chờ xác minh. Hãy khai lại email");
            long wait = platform.getAccountMailer().sendVerification(user, pending);
            if (wait > 0) throw new ApiException(429, "Bạn vừa yêu cầu gửi thư xác minh. Hãy đợi " + wait + " giây rồi thử lại");
            req.sendOk();
            return true;
        }
        return false;
    }

    /** So lan nhap SAI mat khau hien tai (doi mat khau, doi email) cho phep trong 10 phut cua mot tai khoan. */
    private static final int PASSWORD_TRY_LIMIT = 8;
    private static final long PASSWORD_TRY_WINDOW_MS = 10 * 60_000L;

    private static String passwordTryKey(User user) {
        return "password-try:" + user.getId();
    }

    /** Chan viec do mat khau hien tai bang mot phien dang nhap nhat duoc; kiem tra TRUOC khi bam mat khau. */
    private void requirePasswordTries(User user) {
        long wait = platform.getLimiter().retryAfterSeconds(passwordTryKey(user), PASSWORD_TRY_WINDOW_MS, PASSWORD_TRY_LIMIT);
        if (wait > 0) {
            throw new ApiException(429, "Bạn nhập sai mật khẩu hiện tại quá nhiều lần. Hãy thử lại sau " + (wait + 59) / 60 + " phút");
        }
    }

    /**
     * Trang thai cua tai khoan de trang quan tri hien rieng tung loai:
     *   ACTIVE  - dang dung binh thuong;
     *   PENDING - giang vien tu dang ky, dang cho duyet;
     *   LOCKED  - bi quan tri vien khoa.
     * Kho chi luu mot co "approved", nen hai loai chua dang nhap duoc phan biet bang file minh chung:
     * giang vien dang ky BAT BUOC gui file, va file chi bi xoa khi duyet / tu choi. Vay tai khoan chua
     * duoc phep ma con file la dang cho duyet; khong con file (da tung duoc duyet), hoac khong phai
     * giang vien (hoc sinh va quan tri vien khong qua buoc duyet), la dang bi khoa.
     */
    private static String statusOf(User u, boolean hasEvidence) {
        if (u.isApproved()) return "ACTIVE";
        return User.ROLE_TEACHER.equals(u.getRoleCode()) && hasEvidence ? "PENDING" : "LOCKED";
    }

    /** Lop van phai co giang vien phu trach: khong xoa / ha quyen tai khoan dang phu trach lop. */
    private void requireNoLedClassroom(String userId) {
        List<String> names = new ArrayList<>();
        for (com.ptit.oj.model.Classroom c : platform.getSchool().classroomsLedBy(userId)) names.add(c.getName());
        if (!names.isEmpty()) {
            throw new ApiException(409, "Tài khoản đang phụ trách lớp " + String.join(", ", names)
                    + ". Hãy xoá các lớp này trước");
        }
    }

    /** Lop cua hoc sinh hoac bo mon cua giang vien. */
    private String detailOf(User u) {
        if (u instanceof Student) return ((Student) u).getClassName();
        if (u instanceof Teacher) return ((Teacher) u).getDepartment();
        return "";
    }
}
