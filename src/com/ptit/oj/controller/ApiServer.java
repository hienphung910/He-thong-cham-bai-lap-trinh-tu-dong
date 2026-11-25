package com.ptit.oj.controller;

import com.ptit.oj.controller.api.AccountApi;
import com.ptit.oj.controller.api.AnalyticsApi;
import com.ptit.oj.controller.api.ApiModule;
import com.ptit.oj.controller.api.AssignmentApi;
import com.ptit.oj.controller.api.CommunityApi;
import com.ptit.oj.controller.api.ContestApi;
import com.ptit.oj.controller.api.MeetingApi;
import com.ptit.oj.controller.api.PlaygroundApi;
import com.ptit.oj.controller.api.Request;
import com.ptit.oj.controller.api.SchoolApi;
import com.ptit.oj.controller.api.SystemApi;
import com.ptit.oj.controller.api.TestcaseApi;
import com.ptit.oj.controller.judge.Language;
import com.ptit.oj.dao.DataAccessException;
import com.ptit.oj.model.JudgeResult;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.ProblemDraft;
import com.ptit.oj.model.Submission;
import com.ptit.oj.model.TestCase;
import com.ptit.oj.model.TestCaseResult;
import com.ptit.oj.model.Topic;
import com.ptit.oj.model.User;
import com.ptit.oj.model.Verdict;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * May chu HTTP cho giao dien web, dung com.sun.net.httpserver co san trong JDK.
 *
 * Nhiem vu:
 *  - Cung cap REST API doc/ghi du lieu cua JudgeService (tang nghiep vu).
 *  - Kiem tra dang nhap va phan quyen cho tung endpoint.
 *  - Phuc vu file tinh cua frontend da build (frontend/dist).
 *
 * An toan: mac dinh chi lang nghe tren 127.0.0.1. Vi may chu nay bien dich va chay ma
 * nguon tuy y nen KHONG duoc mo ra mang LAN. Khi chay trong container (Docker, Railway)
 * thi dat bien moi truong OJ_BIND_ADDRESS=0.0.0.0 de nen tang chuyen request vao duoc.
 *
 * Nguyen tac phan quyen: KHONG BAO GIO tin du lieu client gui len.
 *  - nguoi nop bai lay tu token, khong lay tu truong "username" trong body,
 *  - hoc sinh chi doc duoc lich su cua chinh minh,
 *  - endpoint /api/admin/* kiem tra vai tro o backend, khong dua vao viec
 *    frontend co hien nut hay khong.
 */
public class ApiServer {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_BODY_BYTES = 512 * 1024;        // 512 KB, du cho mot bai nop
    /** Dang ky giang vien co the kem file minh chung toi da 1 MB (ma hoa base64 thanh ~1,4 MB). */
    private static final int MAX_REGISTER_BODY_BYTES = 1536 * 1024;
    private static final int MAX_ADMIN_BODY_BYTES = 8 * 1024 * 1024;  // de bai + test co the lon hon

    /** Dia chi lang nghe; bo trong thi chi nghe 127.0.0.1. Trong container dat la 0.0.0.0. */
    public static final String BIND_ADDRESS_ENV = "OJ_BIND_ADDRESS";

    private final JudgeService service;
    private final Platform platform;
    /** Cac nhom endpoint cua module mo rong; route() hoi lan luot tung nhom. */
    private final List<ApiModule> modules;
    private final int port;
    private final Path webRoot;

    private HttpServer server;
    private ExecutorService pool;

    public ApiServer(Platform platform, int port, Path webRoot) {
        this.platform = platform;
        this.service = platform.getJudge();
        this.port = port;
        this.webRoot = webRoot;
        this.modules = Arrays.<ApiModule>asList(
                new AccountApi(platform), new SchoolApi(platform),
                new AssignmentApi(platform), new MeetingApi(platform), new ContestApi(platform),
                new AnalyticsApi(platform), new CommunityApi(platform),
                new SystemApi(platform), new PlaygroundApi(platform),
                new TestcaseApi(platform));
    }

    public void start() throws IOException {
        InetAddress address = bindAddress();
        server = HttpServer.create(new InetSocketAddress(address, port), 0);
        // Bai nop duoc cham tuan tu va giu luon thread cua request cho toi khi cham xong.
        // 4 thread thi bon nguoi nop cung luc la ca trang web dung; 32 thread de cac request
        // doc du lieu van duoc phuc vu trong luc co bai dang cho cham.
        pool = Executors.newFixedThreadPool(32);
        server.setExecutor(pool);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.start();

        if (address.isLoopbackAddress()) {
            System.out.println("Máy chủ web đã chạy: http://localhost:" + port);
        } else {
            System.out.println("Máy chủ web đã chạy trên " + address.getHostAddress() + ":" + port
                    + " (mở ra ngoài máy này theo " + BIND_ADDRESS_ENV + ")");
        }
        if (Files.isDirectory(webRoot)) {
            System.out.println("Giao diện web: " + webRoot.toAbsolutePath());
        } else {
            System.out.println("Chưa có frontend đã build tại " + webRoot.toAbsolutePath());
            System.out.println("  -> chạy: cd frontend && npm install && npm run build");
            System.out.println("  -> hoặc dùng chế độ dev: cd frontend && npm run dev");
        }
        System.out.println("Nhấn Ctrl+C để dừng.");
    }

    /** Doc OJ_BIND_ADDRESS; khong dat thi chi nghe loopback nhu truoc. */
    private static InetAddress bindAddress() throws IOException {
        String configured = com.ptit.oj.dao.DatabaseConfig.setting(BIND_ADDRESS_ENV);
        if (configured == null || configured.trim().isEmpty()) {
            return InetAddress.getLoopbackAddress();
        }
        return InetAddress.getByName(configured.trim());
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    // --------------------------------------------------------------- REST API

    private void handleApi(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        int status = 200;          // ma HTTP cua request nay, de ghi vao nhat ky
        String error = null;
        try {
            enforceExamMode(ex, method, path);
            route(ex, method, path);
        } catch (ApiException e) {
            status = e.getStatus();
            error = e.getMessage();
        } catch (AuthService.AuthException e) {
            status = e.getHttpStatus();
            error = e.getMessage();
        } catch (ProblemAdminService.ProblemAdminException e) {
            status = e.getHttpStatus();
            error = e.getMessage();
        } catch (IllegalArgumentException e) {
            status = 400;
            error = e.getMessage();
        } catch (DataAccessException e) {
            // Khong lo chi tiet SQL / duong dan CSDL ra ngoai, nhung van ghi log de sua.
            System.err.println("Lỗi CSDL khi xử lý " + method + " " + path + ": " + e);
            status = 500;
            error = "Lỗi truy cập cơ sở dữ liệu";
        } catch (RuntimeException e) {
            // Khong de mot request loi lam sap ca may chu, cung khong tra stack trace ve client.
            System.err.println("Lỗi khi xử lý " + method + " " + path + ": " + e);
            status = 500;
            error = "Lỗi máy chủ nội bộ";
        }
        if (status != 200) sendError(ex, status, error);
        audit(ex, method, path, status);
    }

    /**
     * Ghi nhat ky hoat dong (Module 40) cho moi request LAM THAY DOI du lieu.
     * Bo qua GET (chi doc), dang nhap (handleLogin tu ghi kem ten dang nhap) va cac
     * request cua phong hoc (trinh duyet gui lien tuc khi go ma / ve bang).
     */
    private void audit(HttpExchange ex, String method, String path, int status) {
        if ("GET".equals(method) || path.equals("/api/auth/login") || path.contains("/room/")
                || path.endsWith("/proctor")) return;      // su kien giam sat da co nhat ky rieng o trang ky thi
        String token = SessionService.extractBearerToken(ex.getRequestHeaders().getFirst("Authorization"));
        String username = service.getAuth().authenticate(token).map(User::getUsername).orElse("(khách)");
        platform.getSystem().log(username, method + " " + path, "HTTP " + status, status == 200);
    }

    /**
     * Che do thi o PHIA MAY CHU: hoc sinh dang trong bai thi chi goi duoc cac API can cho viec thi
     * (xem de cua ky thi, nop bai, xem ket qua cua minh, bang xep hang, nop bai som, giam sat).
     * Chat, binh luan, chay thu ma, lop hoc, phan tich... bi tu choi cho toi khi nop bai hoac het gio,
     * du goi thang API ma khong qua giao dien.
     */
    private void enforceExamMode(HttpExchange ex, String method, String path) {
        String token = SessionService.extractBearerToken(ex.getRequestHeaders().getFirst("Authorization"));
        Optional<User> user = service.getAuth().authenticate(token);
        if (!user.isPresent() || !User.ROLE_STUDENT.equals(user.get().getRoleCode())) return;
        com.ptit.oj.model.Contest sitting = platform.getContests().sittingContestOf(user.get().getId());
        if (sitting == null || allowedWhileSitting(method, path)) return;
        throw ApiException.forbidden("Bạn đang trong bài thi \"" + sitting.getTitle()
                + "\". Chức năng này mở lại sau khi bạn nộp bài hoặc hết giờ");
    }

    private static boolean allowedWhileSitting(String method, String path) {
        if (path.startsWith("/api/auth/")) return true;
        if ("GET".equals(method)) {
            return path.equals("/api/health") || path.equals("/api/languages") || path.equals("/api/problems")
                    || path.equals("/api/topics") || path.equals("/api/submissions")
                    || path.startsWith("/api/submissions/") || path.equals("/api/contests")
                    || path.startsWith("/api/contests/") || path.equals("/api/notifications")
                    || path.equals("/api/settings");
        }
        if ("POST".equals(method)) {
            return path.equals("/api/submit")
                    || (path.startsWith("/api/contests/")
                        && (path.endsWith("/finish") || path.endsWith("/proctor") || path.endsWith("/join")));
        }
        return false;
    }

    private void route(HttpExchange ex, String method, String path) throws IOException {
        // ---- cong khai (chua can dang nhap) ----
        if ("GET".equals(method) && path.equals("/api/health")) {
            sendJson(ex, 200, Json.obj().put("ok", true)
                    .put("problems", service.getProblems().size()).build());
            return;
        }
        if ("GET".equals(method) && path.equals("/api/languages")) {
            sendJson(ex, 200, languagesJson());
            return;
        }
        if ("POST".equals(method) && path.equals("/api/auth/register")) {
            handleRegister(ex);
            return;
        }
        if ("POST".equals(method) && path.equals("/api/auth/login")) {
            handleLogin(ex);
            return;
        }
        if ("POST".equals(method) && path.equals("/api/auth/logout")) {
            handleLogout(ex);
            return;
        }

        // ---- can dang nhap ----
        if ("GET".equals(method) && path.equals("/api/auth/me")) {
            sendJson(ex, 200, userJson(requireUser(ex)));
            return;
        }
        if ("GET".equals(method) && path.equals("/api/problems")) {
            sendJson(ex, 200, problemsJson(requireUser(ex)));
            return;
        }
        if ("GET".equals(method) && path.equals("/api/topics")) {
            requireUser(ex);
            sendJson(ex, 200, topicsJson());
            return;
        }
        if ("GET".equals(method) && path.equals("/api/submissions")) {
            sendJson(ex, 200, submissionsJson(requireUser(ex)));
            return;
        }
        if ("GET".equals(method) && path.startsWith("/api/submissions/")) {
            handleSubmissionDetail(ex, requireUser(ex), path.substring("/api/submissions/".length()));
            return;
        }
        if ("GET".equals(method) && path.equals("/api/scoreboard")) {
            requireUser(ex);
            sendJson(ex, 200, scoreboardJson());
            return;
        }
        if ("POST".equals(method) && path.equals("/api/submit")) {
            handleSubmit(ex, requireUser(ex));
            return;
        }

        // ---- quan ly bai tap: quan tri vien (kho chung) va giang vien (kho rieng cua minh) ----
        // Hoc sinh bi chan o day; con "bai nay co phai cua minh khong" do ProblemAdminService kiem tra.
        if ("POST".equals(method) && path.equals("/api/admin/problems")) {
            handleCreateProblem(ex, requireStaff(ex));
            return;
        }
        // Xoa bai: duong dan dung la /api/admin/problems/{ma}; /tests/... thuoc ve TestcaseApi.
        if ("DELETE".equals(method) && path.startsWith("/api/admin/problems/")
                && path.indexOf('/', "/api/admin/problems/".length()) < 0) {
            handleDeleteProblem(ex, requireStaff(ex), path.substring("/api/admin/problems/".length()));
            return;
        }
        if ("PUT".equals(method) && path.startsWith("/api/admin/problems/")) {
            handleUpdateProblem(ex, requireStaff(ex), path.substring("/api/admin/problems/".length()));
            return;
        }

        // ---- cac module mo rong (lop hoc, khoa hoc, phong hoc, cuoc thi...) ----
        Request request = new Request(ex, service);
        for (ApiModule module : modules) {
            if (module.handle(request)) return;
        }
        throw ApiException.notFound("Không có endpoint " + method + " " + path);
    }

    // ------------------------------------------------------------ xac thuc

    /** Lay tai khoan tu header Authorization. Nem 401 neu chua dang nhap. */
    private User requireUser(HttpExchange ex) {
        String token = SessionService.extractBearerToken(ex.getRequestHeaders().getFirst("Authorization"));
        Optional<User> user = service.getAuth().authenticate(token);
        if (!user.isPresent()) {
            throw ApiException.unauthorized("Bạn cần đăng nhập để dùng chức năng này");
        }
        return user.get();
    }

    /** Nem 401 neu chua dang nhap, 403 neu la hoc sinh. */
    private User requireStaff(HttpExchange ex) {
        User user = requireUser(ex);
        if (!canSeeEveryone(user)) {
            throw ApiException.forbidden("Chỉ giảng viên hoặc quản trị viên mới được dùng chức năng này");
        }
        return user;
    }

    /** ADMIN va giang vien xem duoc toan bo lich su; hoc sinh chi xem cua minh. */
    private boolean canSeeEveryone(User user) {
        return User.ROLE_ADMIN.equals(user.getRoleCode())
                || User.ROLE_TEACHER.equals(user.getRoleCode());
    }

    private void handleRegister(HttpExchange ex) throws IOException {
        if (!platform.getSystem().isRegisterAllowed()) {
            throw ApiException.forbidden("Quản trị viên đang tạm khoá chức năng tự đăng ký");
        }
        Json.Value body = Json.parseObject(readBody(ex, MAX_REGISTER_BODY_BYTES));
        String role = body.getString("role", User.ROLE_STUDENT);
        // Giang vien tu dang ky BAT BUOC gui kem file minh chung de quan tri vien co can cu duyet.
        // Kiem tra file TRUOC khi tao tai khoan: thieu hoac hong thi khong tao gi ca, nguoi dung sua
        // roi dang ky lai bang dung ten do. Hoc sinh khong can minh chung; co gui cung bo qua.
        EvidenceStore.Evidence evidence = null;
        if (User.ROLE_TEACHER.equals(role)) {
            String rawEvidence = body.getString("evidence", "");
            if (rawEvidence.trim().isEmpty()) {
                throw ApiException.badRequest("Giảng viên cần gửi kèm file minh chứng khi đăng ký");
            }
            evidence = EvidenceStore.decode(rawEvidence);
        }
        // role chi duoc la STUDENT hoac TEACHER; gui "ADMIN" thi register() tu choi (400).
        User created = service.getAuth().register(
                role,
                body.getString("username"),
                body.getString("password"),
                body.getString("fullName"),
                body.getString("studentCode", ""),
                body.getString("className", ""),
                body.getString("department", ""));
        if (evidence != null) {
            try {
                platform.getEvidence().save(created.getId(), evidence);
            } catch (IOException | RuntimeException e) {
                // Khong luu duoc minh chung thi huy luon tai khoan vua tao de nguoi dung dang ky lai duoc.
                service.getAuth().reject(created.getId());
                throw new ApiException(500, "Không lưu được file minh chứng, hãy thử lại");
            }
        }
        // Giang vien tu dang ky phai cho duyet: bao cho moi quan tri vien biet de vao duyet.
        boolean pending = !created.isApproved();
        if (pending) {
            List<String> adminIds = new ArrayList<>();
            for (User u : service.getUsers()) {
                if (User.ROLE_ADMIN.equals(u.getRoleCode())) adminIds.add(u.getId());
            }
            platform.getCommunity().notifyMany(adminIds, "Giảng viên mới chờ duyệt",
                    created.getFullName() + " (@" + created.getUsername() + ") vừa đăng ký tài khoản giảng viên.",
                    "Accounts");
        }
        platform.getSystem().log(created.getUsername(), "Đăng ký",
                pending ? "Giảng viên, chờ duyệt" : "Học sinh", true);
        sendJson(ex, 201, Json.obj()
                .put("message", pending
                        ? "Đã gửi đăng ký. Tài khoản giảng viên cần quản trị viên duyệt rồi mới đăng nhập được"
                        : "Đăng ký thành công, mời bạn đăng nhập")
                .put("pending", pending)
                .putRaw("user", userJson(created))
                .build());
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        Json.Value body = Json.parseObject(readBody(ex, MAX_BODY_BYTES));
        String username = body.getString("username", "");
        AuthService.LoginResult result;
        try {
            result = service.getAuth().login(username, body.getString("password"));
        } catch (AuthService.AuthException e) {
            platform.getSystem().log(username, "Đăng nhập thất bại", e.getMessage(), false);
            throw e;
        }
        platform.getSystem().log(result.getUser().getUsername(), "Đăng nhập", "", true);
        sendJson(ex, 200, Json.obj()
                .put("token", result.getToken())
                .putRaw("user", userJson(result.getUser()))
                .build());
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String token = SessionService.extractBearerToken(ex.getRequestHeaders().getFirst("Authorization"));
        service.getAuth().logout(token);
        // Luon tra ve 200: dang xuat mot token da het han khong phai la loi.
        sendJson(ex, 200, Json.obj().put("ok", true).build());
    }

    /** JSON cua mot tai khoan - KHONG BAO GIO kem password_hash hay password_salt. */
    private String userJson(User user) {
        return Json.obj()
                .put("id", user.getId())
                .put("username", user.getUsername())
                .put("fullName", user.getFullName())
                .put("role", user.getRoleCode())
                .put("roleName", user.getRole())
                .put("canCreateProblem", user.canCreateProblem())
                .build();
    }

    // ------------------------------------------------------------- du lieu

    private String languagesJson() {
        List<String> items = new ArrayList<>();
        for (Language l : service.getLanguages()) {
            items.add(Json.obj()
                    .put("name", l.getName())
                    .put("extension", l.getFileExtension())
                    .put("available", l.isAvailable())
                    .build());
        }
        return Json.array(items);
    }

    /**
     * Danh sach bai tap nguoi xem duoc thay. KHONG tra ve so luong test, va chi tra ve
     * input/output cua test VI DU - test an khong bao gio roi xuong trinh duyet.
     *
     * Ai thay bai nao: xem ProblemAccess (bai kho rieng chi mo qua lop cua minh; bai cua ky thi
     * dang dien ra chi mo cho hoc sinh dang o trong bai thi).
     */
    private String problemsJson(User viewer) {
        ProblemAccess access = platform.accessFor(viewer);
        List<String> items = new ArrayList<>();
        for (Problem p : service.getProblems()) {
            if (!access.canOpen(p)) continue;
            List<String> samples = new ArrayList<>();
            for (TestCase tc : p.getTestCases()) {
                if (!tc.isSample()) continue;
                samples.add(Json.obj()
                        .put("input", tc.getInput())
                        .put("output", tc.getExpectedOutput())
                        .build());
            }
            items.add(Json.obj()
                    .put("id", p.getId())
                    .put("title", p.getTitle())
                    .put("statement", p.getStatement())
                    .put("timeLimitMs", p.getTimeLimitMs())
                    .put("memoryLimitMb", p.getMemoryLimitMb())
                    .put("comparator", p.getComparatorSpec())
                    .put("maxPoints", p.getMaxPoints())
                    .put("topic", p.getTopic())
                    .put("difficulty", p.getDifficulty())
                    .put("order", p.getOrder())
                    .put("owner", p.getOwner())            // "" = kho chung
                    .putRaw("samples", Json.array(samples))
                    .build());
        }
        return Json.array(items);
    }

    /** Lo trinh hoc theo dung thu tu; dem so bai de giao dien khoi phai tu dem lai. */
    private String topicsJson() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Problem p : service.getSharedProblems()) {      // lo trinh chi dem bai kho chung
            counts.merge(p.getTopic(), 1, Integer::sum);
        }
        List<String> items = new ArrayList<>();
        for (Topic t : service.getTopics()) {
            items.add(Json.obj()
                    .put("id", t.getId())
                    .put("name", t.getName())
                    .put("description", t.getDescription())
                    .put("group", t.getGroup())
                    .put("order", t.getOrder())
                    .put("problemCount", counts.getOrDefault(t.getId(), 0))
                    .build());
        }
        return Json.array(items);
    }

    private String submissionsJson(User viewer) {
        List<String> items = new ArrayList<>();
        if (User.ROLE_STUDENT.equals(viewer.getRoleCode())) {
            for (Submission s : service.getSubmissionsOf(viewer)) items.add(submissionJson(s, false));
            return Json.array(items);
        }
        Set<String> myStudents = platform.getSchool().studentIdsTaughtBy(viewer.getId());
        for (Submission s : service.getSubmissionsNewestFirst()) {
            if (canSeeSubmission(viewer, s, myStudents)) items.add(submissionJson(s, false));
        }
        return Json.array(items);
    }

    /**
     * Ai xem duoc mot bai nop (kem ma nguon): quan tri vien xem het; nguoi nop xem bai cua minh;
     * giang vien chi xem bai cua hoc sinh trong lop minh day, va bai nop vao bai tap thuoc kho cua minh.
     * Giang vien KHONG xem duoc bai nop cua hoc sinh lop khac.
     */
    private boolean canSeeSubmission(User viewer, Submission s, Set<String> myStudents) {
        if (User.ROLE_ADMIN.equals(viewer.getRoleCode())) return true;
        if (s.getAuthor().getId().equals(viewer.getId())) return true;
        if (!User.ROLE_TEACHER.equals(viewer.getRoleCode())) return false;
        return myStudents.contains(s.getAuthor().getId()) || (!s.getProblem().isShared() && viewer.canEditProblem(s.getProblem()));
    }

    private void handleSubmissionDetail(HttpExchange ex, User viewer, String id) throws IOException {
        Optional<Submission> found = service.findSubmission(id);
        if (!found.isPresent()) {
            throw ApiException.notFound("Không có mã nộp " + id);
        }
        Submission s = found.get();
        // Chan viec doan ma nop de doc ma nguon cua nguoi khac.
        if (!canSeeSubmission(viewer, s, platform.getSchool().studentIdsTaughtBy(viewer.getId()))) {
            throw ApiException.forbidden(User.ROLE_TEACHER.equals(viewer.getRoleCode())
                    ? "Bạn chỉ xem được bài nộp của học sinh trong lớp mình dạy"
                    : "Bạn chỉ được xem bài nộp của chính mình");
        }
        sendJson(ex, 200, submissionJson(s, true));
    }

    /** @param detail true -> kem ket qua tung test va ma nguon */
    private String submissionJson(Submission s, boolean detail) {
        JudgeResult r = s.getResult();
        Json.Obj o = Json.obj()
                .put("id", s.getId())
                .put("submittedAt", s.getSubmittedAt().format(ISO))
                .put("author", s.getAuthor().getUsername())
                .put("authorName", s.getAuthor().getFullName())
                .putRaw("problem", Json.obj()
                        .put("id", s.getProblem().getId())
                        .put("title", s.getProblem().getTitle())
                        .build())
                .put("language", s.getLanguage().getName())
                .put("fileName", s.getFileName())
                .put("maxPoints", s.getProblem().getMaxPoints());

        if (r == null) {
            o.put("verdict", "PENDING").put("verdictName", "Chờ chấm")
             .putNull("execTimeMs").putNull("score")
             .put("passed", 0).put("total", s.getProblem().getTestCases().size());
        } else {
            Verdict v = r.getOverallVerdict();
            o.put("verdict", v.getCode())
             .put("verdictName", v.getDisplay())
             .put("execTimeMs", r.getMaxRuntimeMs())
             .put("judgeTimeMs", r.getJudgeTimeMs())
             .put("score", r.getScore())
             .put("passed", r.getPassedCount())
             .put("total", r.getTotalTests())
             .put("message", r.getGlobalMessage());
        }

        if (detail) {
            List<String> tests = new ArrayList<>();
            if (r != null) {
                for (TestCaseResult tr : r.getTestResults()) {
                    // Chi tra ve ket qua cham, khong tra ve input/output cua test an;
                    // message cung khong chua dap an (xem OutputComparator.explain()).
                    tests.add(Json.obj()
                            .put("id", tr.getTestCase().getId())
                            .put("sample", tr.getTestCase().isSample())
                            .put("verdict", tr.getVerdict().getCode())
                            .put("runtimeMs", tr.getRuntimeMs())
                            .put("points", tr.getEarnedPoints())
                            .put("message", tr.getMessage())
                            .build());
                }
            }
            o.putRaw("tests", Json.array(tests));
            if (s.getSourceCode() != null) o.put("sourceCode", s.getSourceCode());
        }
        return o.build();
    }

    private String scoreboardJson() {
        Map<String, Integer> solvedByUser = service.getSolvedCountByUser();
        Map<String, Integer> privateSolved = privateSolvedCount();
        List<String> rows = new ArrayList<>();
        int rank = 1;
        for (ScoreboardService.Row row : service.buildScoreboard()) {
            List<String> perProblem = new ArrayList<>();
            for (Map.Entry<String, Double> e : row.getBestPerProblem().entrySet()) {
                perProblem.add(Json.obj().put("problemId", e.getKey()).put("score", e.getValue()).build());
            }
            rows.add(Json.obj()
                    .put("rank", rank++)
                    .put("username", row.getUser().getUsername())
                    .put("fullName", row.getUser().getFullName())
                    .put("role", row.getUser().getRoleCode())
                    .put("roleName", row.getUser().getRole())
                    .put("total", row.getTotal())
                    .put("solved", row.getSolved())
                    .put("solvedCount", solvedByUser.getOrDefault(row.getUser().getId(), 0)
                            - privateSolved.getOrDefault(row.getUser().getId(), 0))
                    .put("attempts", row.getAttempts())
                    .putRaw("perProblem", Json.array(perProblem))
                    .build());
        }
        return Json.array(rows);
    }

    /**
     * userId -> so bai KHO RIENG nguoi do da AC. So bai da giai lay tu CSDL (view user_stats)
     * dem moi bai, nen bang xep hang tru di phan nay de chi con bai kho chung.
     */
    private Map<String, Integer> privateSolvedCount() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : platform.getAnalytics().solvedByUser().entrySet()) {
            for (String problemId : e.getValue()) {
                Problem p = service.findProblem(problemId).orElse(null);
                if (p != null && !p.isShared()) counts.merge(e.getKey(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * POST /api/submit  body: {problemId, language, code}
     *
     * Nop bai: cham xong moi tra ve ban ghi bai nop kem ket qua tung test.
     * Nguoi nop luon lay tu token dang nhap, khong nhan tu body, nen khong the mao danh.
     */
    private void handleSubmit(HttpExchange ex, User author) throws IOException {
        Json.Value body = Json.parseObject(readBody(ex, MAX_BODY_BYTES));
        String problemId = body.getString("problemId");
        String languageName = body.getString("language");
        String code = body.getString("code");

        Problem problem = service.findProblem(problemId)
                .orElseThrow(() -> ApiException.notFound("Không có bài " + problemId));
        // Cung quy tac voi danh sach bai (ProblemAccess): khong mo duoc bai thi cung khong nop duoc,
        // du co doan dung ma bai.
        ProblemAccess access = platform.accessFor(author);
        if (!access.canOpen(problem)) {
            String reason = access.lockReason(problem);
            if (reason != null) throw ApiException.forbidden(reason);
            throw ApiException.notFound("Không có bài " + problemId);
        }
        Language language = service.findLanguage(languageName)
                .orElseThrow(() -> ApiException.badRequest("Không hỗ trợ ngôn ngữ " + languageName));
        if (!language.isAvailable()) {
            throw ApiException.badRequest("Máy này chưa cài toolchain cho " + language.getName());
        }
        if (code == null || code.trim().isEmpty()) {
            throw ApiException.badRequest("Mã nguồn rỗng");
        }

        JudgeResult result;
        try {
            result = service.submitCode(problem, author, language, code);
        } catch (RuntimeException e) {
            // Vi du khong luu duoc vao CSDL: ghi log de sua, chi bao cho nguoi dung mot cau chung.
            System.err.println("Lỗi khi chấm bài: " + e);
            throw new ApiException(500, "Lỗi máy chủ khi chấm bài");
        }
        sendJson(ex, 200, submissionJson(result.getSubmission(), true));
    }

    /**
     * POST /api/admin/problems - tao bai tap moi. Quan tri vien tao vao kho chung,
     * giang vien tao vao kho rieng; chu bai do may chu tu gan, khong doc tu body.
     */
    private void handleCreateProblem(HttpExchange ex, User admin) throws IOException {
        Json.Value body = Json.parseObject(readBody(ex, MAX_ADMIN_BODY_BYTES));

        ProblemDraft draft = new ProblemDraft();
        draft.setId(body.getString("id"));
        draft.setTitle(body.getString("title"));
        draft.setStatement(body.getString("statement"));
        draft.setTimeLimitMs(body.getLong("timeLimitMs", 2000));
        draft.setMemoryLimitMb((int) body.getLong("memoryLimitMb", 256));
        draft.setComparator(body.getString("comparator", "token"));
        draft.setTotalPoints(body.getDouble("totalPoints", 100));
        draft.setTopic(body.getString("topic", ""));
        draft.setDifficulty((int) body.getLong("difficulty", 0));

        if (!body.get("tests").isArray()) {
            throw ApiException.badRequest("Thiếu danh sách test (\"tests\" phải là một mảng)");
        }
        for (Json.Value t : body.getArray("tests")) {
            if (!t.isObject()) throw ApiException.badRequest("Mỗi phần tử của \"tests\" phải là một object");
            draft.addTest(new ProblemDraft.TestDraft(
                    t.getString("name"),
                    t.getString("input"),
                    t.getString("output"),
                    t.getBoolean("sample", false)));
        }

        Problem created = service.createProblem(admin, draft);
        sendJson(ex, 201, Json.obj()
                .put("message", "Đã tạo bài " + created.getId())
                .putRaw("problem", problemSummaryJson(created))
                .build());
    }

    /**
     * PUT /api/admin/problems/{id} - doi chu de va do kho cua bai da co.
     * Quan tri vien sua duoc moi bai, giang vien chi sua bai trong kho cua minh.
     * Body: {"topic": "vong-lap", "difficulty": 2}. Truong nao vang mat thi giu nhu cu.
     */
    private void handleUpdateProblem(HttpExchange ex, User admin, String id) throws IOException {
        Json.Value body = Json.parseObject(readBody(ex, MAX_BODY_BYTES));
        Problem current = service.findProblem(id)
                .orElseThrow(() -> ApiException.notFound("Không có bài " + id));

        // Co gui ten / de / gioi han thi sua noi dung bai truoc (kiem tra quyen o ProblemAdminService).
        if (body.has("title") || body.has("statement") || body.has("timeLimitMs") || body.has("memoryLimitMb")) {
            current = service.updateProblem(admin, id,
                    body.has("title") ? body.getString("title", "") : current.getTitle(),
                    body.has("statement") ? body.getString("statement", "") : current.getStatement(),
                    body.getLong("timeLimitMs", current.getTimeLimitMs()),
                    intField(body, "memoryLimitMb", current.getMemoryLimitMb()));
        }
        Problem updated = service.updateProblemMeta(admin, id,
                body.has("topic") ? body.getString("topic", "") : current.getTopic(),
                intField(body, "difficulty", current.getDifficulty()));
        sendJson(ex, 200, Json.obj()
                .put("message", "Đã cập nhật bài " + updated.getId())
                .putRaw("problem", problemSummaryJson(updated))
                .build());
    }

    /** Xoa bai: chu bai (hoac quan tri vien voi bai kho chung). Bai dang duoc dung o dau do thi tu choi. */
    private void handleDeleteProblem(HttpExchange ex, User actor, String rawId) throws IOException {
        String id = rawId.trim().toUpperCase();
        Problem problem = service.findProblemForEdit(actor, id);
        List<String> usedIn = new ArrayList<>();
        for (com.ptit.oj.model.Contest c : platform.getContests().getContests()) {
            if (c.getProblemIds().contains(problem.getId())) usedIn.add("kỳ thi \"" + c.getTitle() + "\"");
        }
        for (com.ptit.oj.model.Assignment a : platform.getAssignments().getAll()) {
            if (a.getProblemIds().contains(problem.getId())) usedIn.add("bài tập giao \"" + a.getTitle() + "\"");
        }
        for (com.ptit.oj.model.Classroom c : platform.getSchool().getClassrooms()) {
            if (c.getPracticeProblemIds().contains(problem.getId())) usedIn.add("bài luyện tập của lớp " + c.getName());
        }
        if (!usedIn.isEmpty()) {
            throw new ApiException(409, "Bài " + problem.getId() + " đang được dùng trong " + String.join(", ", usedIn)
                    + ". Hãy gỡ bài khỏi các nơi đó trước");
        }
        service.deleteProblem(actor, problem.getId());
        sendJson(ex, 200, Json.obj().put("ok", true).put("message", "Đã xoá bài " + problem.getId()).build());
    }

    /** So nguyen trong body; so qua lon bi tu choi thay vi ep kieu thanh mot so khac. */
    private int intField(Json.Value body, String key, int fallback) {
        long value = body.getLong(key, fallback);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw ApiException.badRequest("Thuộc tính \"" + key + "\" quá lớn");
        }
        return (int) value;
    }

    /** Thong tin chung cua bai tra ve sau khi tao / sua (khong kem de bai va test). */
    private String problemSummaryJson(Problem p) {
        return Json.obj()
                .put("id", p.getId())
                .put("title", p.getTitle())
                .put("timeLimitMs", p.getTimeLimitMs())
                .put("memoryLimitMb", p.getMemoryLimitMb())
                .put("comparator", p.getComparatorSpec())
                .put("maxPoints", p.getMaxPoints())
                .put("topic", p.getTopic())
                .put("difficulty", p.getDifficulty())
                .put("order", p.getOrder())
                .put("owner", p.getOwner())
                .build();
    }

    public static String readBody(HttpExchange ex, int maxBytes) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > maxBytes) {
                    throw ApiException.badRequest("Dữ liệu gửi lên quá lớn (> " + (maxBytes / 1024) + " KB)");
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------ file tinh

    private void handleStatic(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "Chỉ hỗ trợ GET cho file tĩnh");
            return;
        }
        String urlPath = ex.getRequestURI().getPath();
        if (urlPath.endsWith("/")) urlPath += "index.html";

        Path file = safeResolve(urlPath);
        // SPA: duong dan khong co duoi file va khong ton tai -> tra ve index.html
        if (file == null || !Files.isRegularFile(file)) {
            Path index = webRoot.resolve("index.html");
            if (!Files.isRegularFile(index)) {
                sendText(ex, 404, "Chưa build frontend. Chạy: cd frontend && npm install && npm run build");
                return;
            }
            file = index;
        }
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    /** Chong duong dan vuot ra ngoai webRoot (path traversal). */
    private Path safeResolve(String urlPath) {
        String relative = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
        if (relative.isEmpty()) relative = "index.html";
        try {
            Path resolved = webRoot.resolve(relative).normalize().toAbsolutePath();
            return resolved.startsWith(webRoot.toAbsolutePath().normalize()) ? resolved : null;
        } catch (java.nio.file.InvalidPathException e) {
            return null;        // ky tu khong hop le trong duong dan (vi du ? * | tren Windows)
        }
    }

    private String contentType(String fileName) {
        String n = fileName.toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------- tien ich

    public static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendError(HttpExchange ex, int status, String message) throws IOException {
        String text = message == null || message.trim().isEmpty() ? "Yêu cầu không hợp lệ" : message;
        sendJson(ex, status, Json.obj().put("error", text).build());
    }

    private void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }
}
