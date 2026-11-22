package com.ptit.oj.controller.api;

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
                items.add(userJson(u).put("detail", detailOf(u)).build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "admin", "users")) {
            req.admin();
            Json.Value body = req.body();
            User created = judge.getAuth().createByAdmin(
                    body.getString("role"), body.getString("username"), body.getString("password"),
                    body.getString("fullName"), body.getString("detail"));
            req.send(userJson(created).put("detail", detailOf(created)).build());
            return true;
        }
        if (req.is("POST", "auth", "password")) {
            User user = req.user();
            Json.Value body = req.body();
            judge.getAuth().changePassword(user, body.getString("oldPassword"), body.getString("newPassword"));
            req.sendOk();
            return true;
        }
        return false;
    }

    /** Lop cua hoc sinh hoac bo mon cua giang vien. */
    private String detailOf(User u) {
        if (u instanceof Student) return ((Student) u).getClassName();
        if (u instanceof Teacher) return ((Teacher) u).getDepartment();
        return "";
    }
}
