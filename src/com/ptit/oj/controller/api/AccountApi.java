package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
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
 *   GET  /api/users            - danh sach tai khoan (giang vien, quan tri)
 *   POST /api/admin/users      - quan tri vien tao tai khoan voi vai tro bat ky
 *   POST /api/admin/users/{id}/approve - duyet giang vien tu dang ky
 *   POST /api/admin/users/{id}/reject  - tu choi: xoa tai khoan dang cho duyet
 *   GET  /api/admin/users/{id}/evidence - file minh chung giang vien gui kem khi dang ky
 *   POST /api/admin/users/{id}/lock    - khoa tai khoan dang dung (mo lai bang approve)
 *   PUT  /api/admin/users/{id}/role    - doi vai tro
 *   DELETE /api/admin/users/{id}       - xoa han tai khoan
 *   POST /api/auth/password    - doi mat khau cua chinh minh
 */
public class AccountApi extends ApiModule {

    public AccountApi(Platform platform) {
        super(platform);
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "users")) {
            req.staff();
            List<String> items = new ArrayList<>();
            for (User u : judge.getUsers()) {
                Json.Obj item = userJson(u).put("detail", detailOf(u)).put("approved", u.isApproved());
                // Chi tai khoan dang cho duyet moi con file minh chung (duyet / tu choi xong la xoa).
                if (!u.isApproved()) item = item.put("hasEvidence", platform.getEvidence().exists(u.getId()));
                items.add(item.build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "admin", "users", "*", "approve")) {
            req.admin();
            User approved = judge.getAuth().approve(req.arg(0));
            platform.getEvidence().delete(approved.getId());
            req.send(userJson(approved).put("detail", detailOf(approved)).put("approved", true).build());
            return true;
        }
        if (req.is("POST", "admin", "users", "*", "reject")) {
            req.admin();
            requireNoLedClassroom(req.arg(0));       // tai khoan bi khoa co the dang phu trach lop
            User rejected = judge.getAuth().reject(req.arg(0));
            platform.getSchool().removeEverywhere(rejected.getId());
            platform.getEvidence().delete(rejected.getId());
            req.sendOk();
            return true;
        }
        if (req.is("POST", "admin", "users", "*", "lock")) {
            User admin = req.admin();
            User locked = judge.getAuth().lock(admin, req.arg(0));
            req.send(userJson(locked).put("detail", detailOf(locked)).put("approved", false).build());
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
            // Tu cach thanh vien cu (hoc sinh / tro giang cua lop) khong con hop voi vai tro moi.
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
        if (req.is("POST", "admin", "users")) {
            req.admin();
            Json.Value body = req.body();
            User created = judge.getAuth().createByAdmin(
                    body.getString("role"), body.getString("username"), body.getString("password"),
                    body.getString("fullName"), body.getString("detail"));
            req.send(userJson(created).put("detail", detailOf(created)).put("approved", true).build());
            return true;
        }
        if (req.is("POST", "auth", "password")) {
            User user = req.user();
            Json.Value body = req.body();
            judge.getAuth().changePassword(user, body.getString("oldPassword"), body.getString("newPassword"),
                    req.token());
            req.sendOk();
            return true;
        }
        return false;
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
