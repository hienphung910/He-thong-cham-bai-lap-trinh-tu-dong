package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.ApiServer;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.controller.SessionService;
import com.ptit.oj.model.User;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mot request toi /api/... da duoc tach san: phuong thuc, cac doan cua duong dan,
 * nguoi dang dang nhap va body JSON. Cac lop ...Api chi lam viec voi lop nay,
 * khong dung truc tiep HttpExchange.
 *
 * Vi du: PUT /api/classrooms/CLS-001/students  ->  parts = [classrooms, CLS-001, students]
 *        req.is("PUT", "classrooms", "*", "students") tra ve true va req.arg(0) = "CLS-001".
 */
public class Request {

    private static final int MAX_BODY_BYTES = 512 * 1024;

    private final HttpExchange exchange;
    private final JudgeService judge;
    private final String method;
    private final String[] parts;
    /** Gia tri cua cac dau * trong lan goi is() khop gan nhat. */
    private final List<String> args = new ArrayList<>();

    public Request(HttpExchange exchange, JudgeService judge) {
        this.exchange = exchange;
        this.judge = judge;
        this.method = exchange.getRequestMethod();
        this.parts = exchange.getRequestURI().getPath().substring("/api/".length()).split("/");
    }

    /** So khop phuong thuc va duong dan; "*" khop voi mot doan bat ky va duoc luu lai cho arg(). */
    public boolean is(String method, String... pattern) {
        if (!this.method.equals(method) || pattern.length != parts.length) return false;
        List<String> found = new ArrayList<>();
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i].equals("*")) {
                found.add(parts[i]);
            } else if (!pattern[i].equals(parts[i])) {
                return false;
            }
        }
        args.clear();
        args.addAll(found);
        return true;
    }

    /** Gia tri cua dau * thu index (tinh tu 0) trong duong dan vua khop. */
    public String arg(int index) {
        return args.get(index);
    }

    /** Tham so sau dau ? cua URL, vi du ?after=5 -> query("after") = "5". Khong co thi tra ve "". */
    public String query(String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return "";
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
        }
        return "";
    }

    /** Tham so so nguyen sau dau ?; thieu hoac sai dinh dang thi dung fallback. */
    public int queryInt(String name, int fallback) {
        try {
            return Integer.parseInt(query(name));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------ phan quyen

    /** Tai khoan dang dang nhap (lay tu token trong header Authorization). Nem 401 neu chua dang nhap. */
    public User user() {
        String token = SessionService.extractBearerToken(exchange.getRequestHeaders().getFirst("Authorization"));
        return judge.getAuth().authenticate(token)
                .orElseThrow(() -> ApiException.unauthorized("Bạn cần đăng nhập để dùng chức năng này"));
    }

    /** Giang vien hoac quan tri vien. Nem 403 voi hoc sinh. */
    public User staff() {
        User user = user();
        if (User.ROLE_STUDENT.equals(user.getRoleCode())) {
            throw ApiException.forbidden("Chỉ giảng viên hoặc quản trị viên mới được dùng chức năng này");
        }
        return user;
    }

    /** Chi quan tri vien. */
    public User admin() {
        User user = user();
        if (!User.ROLE_ADMIN.equals(user.getRoleCode())) {
            throw ApiException.forbidden("Chỉ tài khoản quản trị mới được dùng chức năng này");
        }
        return user;
    }

    // ------------------------------------------------------------ doc va tra loi

    public Json.Value body() throws IOException {
        return Json.parseObject(ApiServer.readBody(exchange, MAX_BODY_BYTES));
    }

    public void send(String json) throws IOException {
        ApiServer.sendJson(exchange, 200, json);
    }

    /** Tra loi cho cac thao tac khong can tra du lieu ve. */
    public void sendOk() throws IOException {
        send(Json.obj().put("ok", true).build());
    }
}
