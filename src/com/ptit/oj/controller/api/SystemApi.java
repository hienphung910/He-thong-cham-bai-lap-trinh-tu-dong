package com.ptit.oj.controller.api;

import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.controller.SystemService;
import com.ptit.oj.controller.judge.Language;
import com.ptit.oj.model.AuditLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Module 39 (quan tri he thong) va 40 (nhat ky hoat dong):
 *   GET /api/settings            - cau hinh cong khai (thong bao chung) cho moi nguoi dang nhap
 *   PUT /api/admin/settings      - quan tri vien doi cau hinh
 *   GET /api/admin/system        - thong tin may chu
 *   GET /api/admin/audit?q=...   - nhat ky hoat dong
 */
public class SystemApi extends ApiModule {

    private final SystemService system;

    public SystemApi(Platform platform) {
        super(platform);
        this.system = platform.getSystem();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "settings")) {
            req.send(settingsJson(isAdmin(req.user())));
            return true;
        }
        if (req.is("PUT", "admin", "settings")) {
            req.admin();
            Json.Value body = req.body();
            if (body.has(SystemService.ANNOUNCEMENT)) {
                system.setSetting(SystemService.ANNOUNCEMENT, body.getString(SystemService.ANNOUNCEMENT));
            }
            if (body.has(SystemService.ALLOW_REGISTER)) {
                system.setSetting(SystemService.ALLOW_REGISTER,
                        String.valueOf(body.getBoolean(SystemService.ALLOW_REGISTER, true)));
            }
            if (body.has(SystemService.BANNED_WORDS)) {
                system.setSetting(SystemService.BANNED_WORDS, body.getString(SystemService.BANNED_WORDS, ""));
            }
            req.send(settingsJson(true));
            return true;
        }
        if (req.is("GET", "admin", "system")) {
            req.admin();
            List<String> info = new ArrayList<>();
            for (Map.Entry<String, String> e : system.systemInfo().entrySet()) {
                info.add(Json.obj().put("label", e.getKey()).put("value", e.getValue()).build());
            }
            for (Language l : judge.getLanguages()) {
                info.add(Json.obj().put("label", "Ngôn ngữ " + l.getName())
                        .put("value", l.isAvailable() ? "đã cài" : "chưa cài toolchain").build());
            }
            info.add(Json.obj().put("label", "Số tài khoản").put("value", String.valueOf(judge.getUsers().size())).build());
            info.add(Json.obj().put("label", "Số bài tập").put("value", String.valueOf(judge.getProblems().size())).build());
            req.send(Json.array(info));
            return true;
        }
        return false;
    }

    /** Danh sach tu cam bo sung chi gui cho quan tri vien (nguoi khac khong can biet de ne). */
    private String settingsJson(boolean admin) {
        Json.Obj json = Json.obj()
                .put(SystemService.ANNOUNCEMENT, system.getSetting(SystemService.ANNOUNCEMENT))
                .put(SystemService.ALLOW_REGISTER, system.isRegisterAllowed());
        if (admin) json = json.put(SystemService.BANNED_WORDS, system.getSetting(SystemService.BANNED_WORDS));
        return json.build();
    }
}
