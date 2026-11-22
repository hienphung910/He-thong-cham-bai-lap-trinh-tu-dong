package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lop cha cua moi nhom endpoint (moi nhom module mot lop con ...Api).
 *
 * ApiServer hoi lan luot tung lop con qua handle(): lop nao nhan ra duong dan cua minh
 * thi xu ly va tra ve true; khong phai cua minh thi tra ve false de lop sau thu tiep.
 * Lop con chi lam 3 viec: kiem tra quyen, goi service, doi ket qua thanh JSON.
 */
public abstract class ApiModule {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    protected final Platform platform;
    protected final JudgeService judge;

    protected ApiModule(Platform platform) {
        this.platform = platform;
        this.judge = platform.getJudge();
    }

    /** @return true neu request thuoc nhom nay va da duoc tra loi */
    public abstract boolean handle(Request req) throws IOException;

    // ---------------------------------------------------------------- thoi gian

    protected static String time(LocalDateTime t) {
        return t == null ? null : t.format(ISO);
    }

    /** Doc thoi gian tu o nhap datetime-local cua trinh duyet ("2026-11-22T14:30"). Rong -> null. */
    protected static LocalDateTime parseTime(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Thời gian không hợp lệ: " + text);
        }
    }

    // --------------------------------------------------------------- nguoi dung

    protected static boolean isAdmin(User user) {
        return User.ROLE_ADMIN.equals(user.getRoleCode());
    }

    protected static boolean isStudent(User user) {
        return User.ROLE_STUDENT.equals(user.getRoleCode());
    }

    /** Tra cuu nhanh nguoi dung theo ma, de doi userId thanh ho ten khi dung JSON. */
    protected Map<String, User> usersById() {
        Map<String, User> map = new HashMap<>();
        for (User u : judge.getUsers()) map.put(u.getId(), u);
        return map;
    }

    protected static String nameOf(Map<String, User> users, String userId) {
        User u = users.get(userId);
        return u == null ? "(đã xoá)" : u.getFullName();
    }

    protected User requireUsername(String username) {
        return judge.findUserByUsername(username)
                .orElseThrow(() -> ApiException.notFound("Không có tài khoản " + username));
    }

    /** Thong tin co ban cua mot tai khoan - khong bao gio kem hash mat khau. */
    protected static Json.Obj userJson(User u) {
        return Json.obj()
                .put("id", u.getId())
                .put("username", u.getUsername())
                .put("fullName", u.getFullName())
                .put("role", u.getRoleCode())
                .put("roleName", u.getRole());
    }

    // --------------------------------------------------------------------- JSON

    /** Mang JSON cac chuoi. */
    protected static String stringArray(Collection<String> values) {
        List<String> items = new ArrayList<>();
        for (String v : values) items.add(Json.quote(v));
        return Json.array(items);
    }

    /**
     * Doc mang ma bai tu body, kiem tra tung ma co ton tai, tra ve ma da chuan hoa.
     * user chi duoc dung bai kho chung hoac bai trong kho cua minh (User.canUseProblem).
     */
    protected List<String> problemIds(Json.Value body, String key, User user) {
        List<String> ids = new ArrayList<>();
        for (Json.Value v : body.getArray(key)) {
            String raw = v.asString();
            Problem p = judge.findProblem(raw)
                    .orElseThrow(() -> ApiException.badRequest("Không có bài " + raw));
            if (!user.canUseProblem(p)) {
                throw ApiException.forbidden("Bài " + p.getId() + " nằm trong kho riêng của giảng viên khác");
            }
            if (!ids.contains(p.getId())) ids.add(p.getId());
        }
        return ids;
    }

    /** Mang [{id, title}] cho mot danh sach ma bai; bai da bi xoa thi hien ma lam ten. */
    protected String problemsJson(List<String> ids) {
        List<String> items = new ArrayList<>();
        for (String id : ids) {
            String title = judge.findProblem(id).map(Problem::getTitle).orElse(id);
            items.add(Json.obj().put("id", id).put("title", title).build());
        }
        return Json.array(items);
    }
}
