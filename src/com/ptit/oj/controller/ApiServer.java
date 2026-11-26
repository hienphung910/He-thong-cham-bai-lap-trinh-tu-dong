package com.ptit.oj.controller;

import com.ptit.oj.controller.api.AccountApi;
import com.ptit.oj.controller.api.AnalyticsApi;
import com.ptit.oj.controller.api.TeachingApi;
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
    // ---- gioi han tan suat (dem theo RateLimiter cua Platform) ----
    private static final long LOGIN_WINDOW_MS = 10 * 60_000L;
    /** So lan dang nhap sai trong 10 phut: cung ten + cung dia chi / cung ten tu moi noi / cung dia chi. */
    private static final int LOGIN_PAIR_LIMIT = 8;
    private static final int LOGIN_USER_LIMIT = 40;
    private static final int LOGIN_IP_LIMIT = 100;
    private static final long REGISTER_WINDOW_MS = 60 * 60_000L;
    /** Ca mot lop co the dang ky tu cung mot mang (chung dia chi) nen khong dat qua thap. */
    private static final int REGISTER_IP_LIMIT = 60;
    /** Don giang vien DA NHAN tu mot dia chi moi gio (moi don chiem mot cho trong hang cho duyet). */
    private static final int REGISTER_TEACHER_IP_LIMIT = 3;
    /** So lan THU gui don giang vien moi gio, ke ca lan bi tu choi: chan viec bat may chu giai ma file lien tuc. */
    private static final int REGISTER_TEACHER_ATTEMPT_LIMIT = 20;

    /**
     * May chu dung sau reverse proxy thi dia chi ket noi luon la cua proxy. OJ_TRUST_PROXY cho biet
     * lay dia chi nguoi dung tu dau:
     *   true       - mot lop proxy (Railway...): gia tri CUOI cua X-Forwarded-For, do proxy gan.
     *   cloudflare - co Cloudflare dung truoc: header CF-Connecting-IP do Cloudflare gan. Nen dat kem
     *                OJ_ORIGIN_SECRET, vi request di vong khong qua Cloudflare tu gui header nay duoc.
     * KHONG dat khi may chu nhan ket noi truc tiep, vi nguoi dung tu gui cac header nay duoc.
     */
    private static final String TRUST_PROXY_ENV = "OJ_TRUST_PROXY";
    private final String proxyMode = String.valueOf(com.ptit.oj.dao.DatabaseConfig.setting(TRUST_PROXY_ENV))
            .trim().toLowerCase(java.util.Locale.ROOT);
    private final boolean trustProxy = proxyMode.equals("true") || proxyMode.equals("cloudflare");
    private final boolean behindCloudflare = proxyMode.equals("cloudflare");

    /**
     * Chi nhan request di qua Cloudflare. Tren Cloudflare tao mot Transform Rule gan header
     * X-Origin-Secret = gia tri bi mat vao moi request; dat cung gia tri do vao OJ_ORIGIN_SECRET.
     * Request khong co dung header (goi thang ten mien goc cua nen tang hosting, bo qua Cloudflare)
     * bi tu choi. Bo trong thi khong kiem tra.
     */
    private static final String ORIGIN_SECRET_ENV = "OJ_ORIGIN_SECRET";
    private static final String ORIGIN_SECRET_HEADER = "X-Origin-Secret";
    private final String originSecret = textSetting(ORIGIN_SECRET_ENV);

    /** CAPTCHA cho form dang ky; tat neu chua dat khoa (xem TurnstileVerifier). */
    private final TurnstileVerifier turnstile = new TurnstileVerifier();

    /** Gui email qua Resend; tat neu chua cau hinh (xem ResendMailer). Chi dung cho "Quen mat khau". */
    private final ResendMailer mailer;
    private final PasswordResetService resets;
    private static final long RESET_WINDOW_MS = 60 * 60_000L;
    /** Yeu cau "quen mat khau" moi gio tu mot dia chi (moi yeu cau co the lam may chu gui mot thu). */
    private static final int FORGOT_IP_LIMIT = 10;
    /** So thu dat lai mat khau gui cho MOT tai khoan moi gio: khong ai dung form nay de doi bom hop thu nguoi khac. */
    private static final int FORGOT_USER_LIMIT = 3;
    /** ... va moi ngay. */
    private static final long FORGOT_DAY_MS = 24 * 60 * 60_000L;
    private static final int FORGOT_USER_DAY_LIMIT = 6;
    /**
     * Khoang cho giua hai lan "gui lai lien ket" (nhu nut "Gui lai sau 60s" o cac nen tang khac).
     * Ap cho cap (dia chi, chuoi da nhap) - bao 429 de giao dien dem nguoc - va cho tung tai khoan
     * (lang le bo qua, de nguoi goi tu dia chi khac khong biet tai khoan co that hay khong).
     */
    private static final long FORGOT_COOLDOWN_MS = 60_000L;
    /** So lan gui ma dat lai SAI moi gio tu mot dia chi (chong do ma). */
    private static final int RESET_IP_LIMIT = 30;

    /** Mot dong ve trang thai gui email, de Main in luc khoi dong. */
    public String describeMail() {
        return mailer.describe();
    }

    private static String textSetting(String key) {
        String value = com.ptit.oj.dao.DatabaseConfig.setting(key);
        return value == null ? "" : value.trim();
    }

    /** Request nay co di qua Cloudflare khong (khi OJ_ORIGIN_SECRET duoc dat). So sanh khong lo thoi gian. */
    private boolean fromTrustedOrigin(HttpExchange ex) {
        if (originSecret.isEmpty()) return true;
        // Kiem tra suc khoe cua nen tang hosting goi thang vao container, khong qua Cloudflare.
        if (ex.getRequestURI().getPath().equals("/api/health")) return true;
        String given = ex.getRequestHeaders().getFirst(ORIGIN_SECRET_HEADER);
        return given != null && java.security.MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), originSecret.getBytes(StandardCharsets.UTF_8));
    }

    private static final String TURNSTILE_HOST = "https://challenges.cloudflare.com";

    /**
     * CSP cho trang giao dien: chi tai ma tu chinh may chu; style noi tuyen va Google Fonts la do giao
     * dien dang dung. Khi bat CAPTCHA thi cho them script va khung cua Turnstile.
     */
    private final String cspPage = "default-src 'self'; script-src 'self'" + (turnstile.isEnabled() ? " " + TURNSTILE_HOST : "") + "; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' data: https://fonts.gstatic.com; img-src 'self' data: blob:; "
            + "connect-src 'self'; frame-src blob:" + (turnstile.isEnabled() ? " " + TURNSTILE_HOST : "") + "; "
            + "object-src blob:; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";
    /** CSP cho phan hoi cua API (JSON / van ban): khong duoc phep tai hay nhung gi ca. */
    private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";

    /** Cac header bao mat gan vao MOI phan hoi. */
    private static void secure(HttpExchange ex, String csp) {
        com.sun.net.httpserver.Headers h = ex.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", csp);
        // Chi bao trinh duyet "luon dung HTTPS" khi request nay that su di qua HTTPS (proxy bao lai).
        if ("https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto"))) {
            h.set("Strict-Transport-Security", "max-age=31536000");
        }
    }

    /** Dia chi cua nguoi gui request, de gioi han tan suat. */
    private String clientIp(HttpExchange ex) {
        String ip = null;
        // Co Cloudflare dung truoc: X-Forwarded-For ket thuc bang dia chi cua may chu Cloudflare (nhieu
        // nguoi dung chung), dia chi that cua nguoi dung nam o CF-Connecting-IP.
        if (behindCloudflare) ip = ex.getRequestHeaders().getFirst("CF-Connecting-IP");
        if (trustProxy && (ip == null || ip.trim().isEmpty())) {
            // Proxy noi them dia chi no nhin thay vao CUOI X-Forwarded-For; cac gia tri phia truoc do nguoi dung tu gui.
            String forwarded = ex.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.trim().isEmpty()) {
                ip = forwarded.substring(forwarded.lastIndexOf(',') + 1).trim();
            }
            if (ip == null || ip.isEmpty()) ip = ex.getRequestHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.trim().isEmpty()) ip = ex.getRemoteAddress().getAddress().getHostAddress();
        ip = ip.trim();
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }

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
        this.mailer = platform.getMailer();
        this.resets = platform.getResets();
        this.service = platform.getJudge();
        this.port = port;
        this.webRoot = webRoot;
        this.modules = Arrays.<ApiModule>asList(
                new AccountApi(platform), new SchoolApi(platform),
                new AssignmentApi(platform), new MeetingApi(platform), new ContestApi(platform),
                new AnalyticsApi(platform), new CommunityApi(platform),
                new SystemApi(platform), new PlaygroundApi(platform),
                new TestcaseApi(platform), new TeachingApi(platform));
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
        if (!originSecret.isEmpty()) {
            System.out.println("Chỉ nhận request có header " + ORIGIN_SECRET_HEADER + " đúng (" + ORIGIN_SECRET_ENV + ").");
        } else if (behindCloudflare) {
            System.out.println("CẢNH BÁO: " + TRUST_PROXY_ENV + "=cloudflare nhưng chưa đặt " + ORIGIN_SECRET_ENV
                    + " - request không đi qua Cloudflare tự gửi được CF-Connecting-IP để né giới hạn tần suất.");
        }
        System.out.println("CAPTCHA khi đăng ký: " + (turnstile.isEnabled() ? "bật (Cloudflare Turnstile)" : "tắt"));

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
        if (!fromTrustedOrigin(ex)) {
            sendError(ex, 403, "Hãy truy cập bằng địa chỉ chính thức của hệ thống");
            return;
        }
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
        // Moi request khong phai GET deu co the da doi du lieu (ke ca khi bao loi giua chung): bao de luu xuong o dia.
        if (!"GET".equals(method)) platform.markChanged();
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
        // Cau hinh cong khai ma form dang nhap / dang ky can biet truoc khi co phien dang nhap.
        if ("GET".equals(method) && path.equals("/api/config")) {
            sendJson(ex, 200, Json.obj().put("turnstileSiteKey", turnstile.getSiteKey())
                    // Co gui duoc email khong: co thi form dang nhap moi hien "Quen mat khau?".
                    .put("passwordReset", mailer.isEnabled())
                    // De form dang ky bao ngay "dang tam khoa" thay vi de nguoi dung dien het roi moi bi tu choi.
                    .put("allowRegister", platform.getSystem().isRegisterAllowed()).build());
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
        if ("POST".equals(method) && path.equals("/api/auth/email/verify")) {
            handleVerifyEmail(ex);
            return;
        }
        if ("POST".equals(method) && path.equals("/api/auth/forgot")) {
            handleForgotPassword(ex);
            return;
        }
        if ("POST".equals(method) && path.equals("/api/auth/reset/check")) {
            handleResetPassword(ex, true);
            return;
        }
        if ("POST".equals(method) && path.equals("/api/auth/reset")) {
            handleResetPassword(ex, false);
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
        // Dia chi nguoi gui (da xu ly proxy / Cloudflare) cho cac module can, vi du nhat ky ky thi.
        ex.setAttribute(Request.CLIENT_IP_ATTRIBUTE, clientIp(ex));
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
        // Chan dang ky hang loat tu mot dia chi (lam day bo nho / hang cho duyet giang vien).
        RateLimiter limiter = platform.getLimiter();
        String ip = clientIp(ex);
        long wait = limiter.retryAfterSeconds("register-ip:" + ip, REGISTER_WINDOW_MS, REGISTER_IP_LIMIT);
        if (wait > 0) {
            throw new ApiException(429, "Có quá nhiều lượt đăng ký từ mạng của bạn. Hãy thử lại sau " + (wait + 59) / 60 + " phút");
        }
        Json.Value body = Json.parseObject(readBody(ex, MAX_REGISTER_BODY_BYTES));
        // CAPTCHA (neu bat): kiem tra TRUOC moi viec ton tai nguyen khac.
        requireCaptcha(body, ip);
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
            // Don giang vien ton tai nguyen hon (giai ma file, cho trong hang cho duyet): moi dia chi chi vai don mot gio.
            long teacherWait = Math.max(
                    limiter.retryAfterSeconds("register-teacher-ip:" + ip, REGISTER_WINDOW_MS, REGISTER_TEACHER_IP_LIMIT),
                    limiter.retryAfterSeconds("register-teacher-try:" + ip, REGISTER_WINDOW_MS, REGISTER_TEACHER_ATTEMPT_LIMIT));
            if (teacherWait > 0) {
                throw new ApiException(429, "Bạn đã gửi nhiều đơn đăng ký giảng viên. Hãy thử lại sau " + (teacherWait + 59) / 60 + " phút");
            }
            limiter.hit("register-teacher-try:" + ip, REGISTER_WINDOW_MS);
            evidence = EvidenceStore.decode(rawEvidence);
        }
        boolean verifyEmail = platform.getAccountMailer().isEnabled();
        // role chi duoc la STUDENT hoac TEACHER; gui "ADMIN" thi register() tu choi (400).
        User created = service.getAuth().register(
                role,
                body.getString("username"),
                body.getString("password"),
                body.getString("fullName"),
                body.getString("studentCode", ""),
                body.getString("className", ""),
                body.getString("department", ""),
                body.getString("email", ""),
                // Gui duoc thu thi email phai xac minh roi moi ghi vao tai khoan (xem EmailVerificationService).
                !verifyEmail);
        if (evidence != null) {
            try {
                platform.getEvidence().save(created.getId(), evidence);
            } catch (IOException | RuntimeException e) {
                // Khong luu duoc minh chung thi huy luon tai khoan vua tao de nguoi dung dang ky lai duoc.
                service.getAuth().reject(created.getId());
                throw new ApiException(500, "Không lưu được file minh chứng, hãy thử lại");
            }
        }
        limiter.hit("register-ip:" + ip, REGISTER_WINDOW_MS);
        if (User.ROLE_TEACHER.equals(role)) limiter.hit("register-teacher-ip:" + ip, REGISTER_WINDOW_MS);
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
        String toVerify = verifyEmail ? service.getAuth().emailToVerify(body.getString("email", "")) : "";
        if (!toVerify.isEmpty()) platform.getAccountMailer().sendVerification(created, toVerify);
        sendJson(ex, 201, Json.obj()
                // true: email chua co hieu luc, nguoi dung can bam lien ket trong thu xac minh.
                .put("emailVerification", !toVerify.isEmpty())
                .put("message", pending
                        ? "Đã gửi đăng ký. Tài khoản giảng viên cần quản trị viên duyệt rồi mới đăng nhập được"
                        : "Đăng ký thành công, mời bạn đăng nhập")
                .put("pending", pending)
                .putRaw("user", userJson(created))
                .build());
    }

    /**
     * Khi bat CAPTCHA (TurnstileVerifier): body phai co "captcha" la token trinh duyet vua nhan tu
     * widget, va Cloudflare xac nhan token do hop le. Token chi dung duoc MOT LAN, nen sau moi lan
     * gui form (du thanh cong hay khong) trinh duyet phai lay token moi. CAPTCHA tat thi khong lam gi.
     */
    private void requireCaptcha(Json.Value body, String ip) {
        if (!turnstile.isEnabled()) return;
        String token = body.getString("captcha", "");
        if (token.trim().isEmpty()) throw ApiException.badRequest("Hãy hoàn thành bước xác minh CAPTCHA");
        boolean human;
        try {
            human = turnstile.verify(token, ip);
        } catch (IOException e) {
            System.err.println("Không gọi được Cloudflare Turnstile: " + e.getMessage());
            throw new ApiException(503, "Không xác minh được CAPTCHA lúc này. Hãy thử lại sau ít phút");
        }
        if (!human) throw ApiException.badRequest("Xác minh CAPTCHA không hợp lệ hoặc đã hết hạn. Hãy thử lại");
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        Json.Value body = Json.parseObject(readBody(ex, MAX_BODY_BYTES));
        String username = body.getString("username", "");

        // Chong do mat khau: dem so lan SAI gan day theo (ten + dia chi), theo ten, theo dia chi.
        // Kiem tra TRUOC khi bam mat khau, nen lan bi chan khong ton CPU cua may chu.
        RateLimiter limiter = platform.getLimiter();
        String ip = clientIp(ex);
        String name = username.trim().toLowerCase(java.util.Locale.ROOT);
        if (name.length() > 64) name = name.substring(0, 64);
        String pairKey = "login:" + name + "|" + ip;
        String userKey = "login-user:" + name;
        String ipKey = "login-ip:" + ip;
        long wait = Math.max(limiter.retryAfterSeconds(pairKey, LOGIN_WINDOW_MS, LOGIN_PAIR_LIMIT),
                Math.max(limiter.retryAfterSeconds(userKey, LOGIN_WINDOW_MS, LOGIN_USER_LIMIT),
                        limiter.retryAfterSeconds(ipKey, LOGIN_WINDOW_MS, LOGIN_IP_LIMIT)));
        if (wait > 0) {
            ex.getResponseHeaders().set("Retry-After", String.valueOf(wait));
            throw new ApiException(429, "Đăng nhập sai quá nhiều lần. Hãy thử lại sau "
                    + (wait >= 120 ? (wait + 59) / 60 + " phút" : wait + " giây"));
        }
        // CAPTCHA (neu bat): sau buoc gioi han tan suat de lan bi chan khong ton mot lan goi Cloudflare,
        // truoc buoc kiem tra mat khau de may tu dong khong thu duoc mat khau nao.
        requireCaptcha(body, ip);

        AuthService.LoginResult result;
        try {
            result = service.getAuth().login(username, body.getString("password"), body.getBoolean("remember", false));
        } catch (AuthService.AuthException e) {
            platform.getSystem().log(username, "Đăng nhập thất bại", e.getMessage(), false);
            if (e.getHttpStatus() == 401) {
                int failures = limiter.hit(pairKey, LOGIN_WINDOW_MS);
                limiter.hit(userKey, LOGIN_WINDOW_MS);
                limiter.hit(ipKey, LOGIN_WINDOW_MS);
                // Cac lan bi chan sau do KHONG ghi nhat ky nua, de mot loat dang nhap sai khong lam ngap nhat ky.
                if (failures == LOGIN_PAIR_LIMIT) {
                    platform.getSystem().log(username, "Tạm khoá đăng nhập",
                            "Sai " + failures + " lần trong 10 phút từ " + ip, false);
                }
            }
            throw e;
        }
        limiter.reset(pairKey);
        platform.getSystem().log(result.getUser().getUsername(), "Đăng nhập", "", true);
        sendJson(ex, 200, Json.obj()
                .put("token", result.getToken())
                .putRaw("user", userJson(result.getUser()))
                .build());
    }

    /**
     * "Quen mat khau": nhan ten dang nhap hoac email, gui link dat lai mat khau vao email cua tai khoan.
     *
     * LUON tra loi cung mot cau du tai khoan co ton tai hay khong, co email hay khong, de form nay
     * khong bi dung de do ten dang nhap / email nao co that. Thu duoc gui o luong nen nen thoi gian
     * tra loi cung khong tiet lo gi.
     */
    private void handleForgotPassword(HttpExchange ex) throws IOException {
        if (!mailer.isEnabled()) {
            throw new ApiException(503, "Hệ thống chưa bật gửi email nên chưa đặt lại mật khẩu qua email được");
        }
        RateLimiter limiter = platform.getLimiter();
        String ip = clientIp(ex);
        long wait = limiter.retryAfterSeconds("forgot-ip:" + ip, RESET_WINDOW_MS, FORGOT_IP_LIMIT);
        if (wait > 0) {
            ex.getResponseHeaders().set("Retry-After", String.valueOf(wait));
            throw new ApiException(429, "Bạn đã yêu cầu đặt lại mật khẩu quá nhiều lần. Hãy thử lại sau " + (wait + 59) / 60 + " phút");
        }
        Json.Value body = Json.parseObject(readBody(ex, MAX_BODY_BYTES));
        String account = body.getString("account", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (account.length() > 190) account = account.substring(0, 190);
        // Bam "gui lai" lien tuc: phai doi het khoang cho. Tinh theo CHUOI DA NHAP (khong theo tai khoan
        // tim duoc) nen cau tra loi nay khong tiet lo tai khoan co ton tai hay khong.
        String pairKey = "forgot-pair:" + ip + "|" + account;
        long cooldown = limiter.retryAfterSeconds(pairKey, FORGOT_COOLDOWN_MS, 1);
        if (cooldown > 0) {
            ex.getResponseHeaders().set("Retry-After", String.valueOf(cooldown));
            throw new ApiException(429, "Bạn vừa yêu cầu gửi liên kết. Hãy đợi " + cooldown + " giây rồi gửi lại");
        }
        requireCaptcha(body, ip);
        limiter.hit("forgot-ip:" + ip, RESET_WINDOW_MS);
        limiter.hit(pairKey, FORGOT_COOLDOWN_MS);

        Optional<User> target = service.getAuth().findForReset(account);
        if (target.isPresent()) {
            User user = target.get();
            String userKey = "forgot-user:" + user.getId();
            String userDayKey = "forgot-user-day:" + user.getId();
            String userCoolKey = "forgot-user-cool:" + user.getId();
            // Qua gioi han cho tai khoan nay (vua gui xong / 3 thu mot gio / 6 thu mot ngay) thi lang le
            // khong gui nua, van tra loi nhu binh thuong.
            if (limiter.retryAfterSeconds(userCoolKey, FORGOT_COOLDOWN_MS, 1) == 0
                    && limiter.retryAfterSeconds(userKey, RESET_WINDOW_MS, FORGOT_USER_LIMIT) == 0
                    && limiter.retryAfterSeconds(userDayKey, FORGOT_DAY_MS, FORGOT_USER_DAY_LIMIT) == 0) {
                limiter.hit(userCoolKey, FORGOT_COOLDOWN_MS);
                limiter.hit(userKey, RESET_WINDOW_MS);
                limiter.hit(userDayKey, FORGOT_DAY_MS);
                String token = resets.issue(user.getId());
                boolean queued = mailer.queue(user.getEmail(), "Đặt lại mật khẩu CodeHub",
                        resetMailHtml(user, token), resetMailText(user, token),
                        // Moi ma mot khoa: goi lai Resend cho CUNG ma nay (mang chap chon) khong sinh thu thu hai.
                        "password-reset/" + PasswordResetService.hash(token));
                platform.getSystem().log(user.getUsername(), "Yêu cầu đặt lại mật khẩu",
                        queued ? "Đã xếp thư vào hàng gửi" : "Hàng chờ gửi thư đã đầy", queued);
            }
        }
        sendJson(ex, 200, Json.obj().put("ok", true)
                // So giay phai doi truoc khi bam "gui lai": giao dien dem nguoc tren nut.
                .put("retryAfter", FORGOT_COOLDOWN_MS / 1000)
                .put("message", "Nếu tài khoản tồn tại và đã khai email, thư hướng dẫn đặt lại mật khẩu vừa được gửi tới email đó. "
                        + "Liên kết trong thư có hiệu lực 30 phút.")
                .build());
    }

    private String resetLink(String token) {
        return mailer.getPublicUrl() + "/reset-password?token=" + token;
    }

    private String resetMailText(User user, String token) {
        return "Xin chào " + user.getFullName() + ",\n\n"
                + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản @" + user.getUsername() + " trên CodeHub.\n"
                + "Mở liên kết sau để đặt mật khẩu mới (có hiệu lực 30 phút, dùng được một lần):\n\n"
                + resetLink(token) + "\n\n"
                + "Nếu bạn không yêu cầu, hãy bỏ qua thư này: mật khẩu của bạn không thay đổi.\n";
    }

    private String resetMailHtml(User user, String token) {
        String link = ResendMailer.escapeHtml(resetLink(token));
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#212121;max-width:520px;margin:0 auto;padding:24px\">"
                + "<h2 style=\"font-size:20px;margin:0 0 16px\">Đặt lại mật khẩu CodeHub</h2>"
                + "<p>Xin chào " + ResendMailer.escapeHtml(user.getFullName()) + ",</p>"
                + "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <b>@" + ResendMailer.escapeHtml(user.getUsername()) + "</b>.</p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + link + "\" style=\"display:inline-block;padding:12px 22px;background:#F7931E;color:#111111;"
                + "text-decoration:none;border-radius:8px;font-weight:bold\">Đặt mật khẩu mới</a></p>"
                + "<p>Liên kết có hiệu lực 30 phút và chỉ dùng được một lần. Nếu nút không bấm được, hãy dán địa chỉ sau vào trình duyệt:</p>"
                + "<p style=\"word-break:break-all;font-size:13px;color:#616161\">" + link + "</p>"
                + "<p style=\"font-size:13px;color:#757575\">Nếu bạn không yêu cầu, hãy bỏ qua thư này: mật khẩu của bạn không thay đổi.</p>"
                + "</div>";
    }

    /**
     * Nguoi dung bam lien ket trong thu xac minh: email dang cho duoc ghi vao tai khoan va tu luc nay
     * dung de lay lai mat khau. Khong can dang nhap - giu duoc lien ket nghia la doc duoc hop thu do.
     */
    private void handleVerifyEmail(HttpExchange ex) throws IOException {
        RateLimiter limiter = platform.getLimiter();
        String ipKey = "verify-ip:" + clientIp(ex);
        long wait = limiter.retryAfterSeconds(ipKey, RESET_WINDOW_MS, RESET_IP_LIMIT);
        if (wait > 0) {
            ex.getResponseHeaders().set("Retry-After", String.valueOf(wait));
            throw new ApiException(429, "Bạn đã thử quá nhiều lần. Hãy thử lại sau " + (wait + 59) / 60 + " phút");
        }
        Json.Value body = Json.parseObject(readBody(ex, MAX_BODY_BYTES));
        Optional<EmailVerificationService.Pending> pending =
                platform.getVerifications().consume(body.getString("token", "").trim());
        if (!pending.isPresent()) {
            limiter.hit(ipKey, RESET_WINDOW_MS);
            throw ApiException.badRequest("Liên kết xác minh không hợp lệ hoặc đã hết hạn. Hãy gửi lại thư xác minh ở trang Tài khoản của tôi");
        }
        User user = service.getAuth().confirmEmail(pending.get().getUserId(), pending.get().getEmail());
        // Email khoi phuc vua doi: lien ket dat lai mat khau gui toi dia chi cu khong con dung duoc.
        resets.revokeUser(user.getId());
        platform.getSystem().log(user.getUsername(), "Xác minh email", "", true);
        sendJson(ex, 200, Json.obj().put("ok", true).put("username", user.getUsername()).put("email", user.getEmail()).build());
    }

    /**
     * Dat mat khau moi bang ma trong link cua thu. checkOnly = true: chi hoi ma con dung duoc khong
     * (trang dat lai mat khau hoi truoc de bao "link het han" ngay, khong bat nguoi dung go xong moi biet).
     */
    private void handleResetPassword(HttpExchange ex, boolean checkOnly) throws IOException {
        RateLimiter limiter = platform.getLimiter();
        String ip = clientIp(ex);
        String ipKey = "reset-ip:" + ip;
        long wait = limiter.retryAfterSeconds(ipKey, RESET_WINDOW_MS, RESET_IP_LIMIT);
        if (wait > 0) {
            ex.getResponseHeaders().set("Retry-After", String.valueOf(wait));
            throw new ApiException(429, "Bạn đã thử quá nhiều lần. Hãy thử lại sau " + (wait + 59) / 60 + " phút");
        }
        Json.Value body = Json.parseObject(readBody(ex, MAX_BODY_BYTES));
        String token = body.getString("token", "").trim();
        if (checkOnly) {
            boolean valid = resets.isValid(token);
            if (!valid) limiter.hit(ipKey, RESET_WINDOW_MS);
            sendJson(ex, 200, Json.obj().put("valid", valid).build());
            return;
        }
        String password = body.getString("password", "");
        // Kiem tra mat khau TRUOC khi dung ma: mat khau qua yeu thi ma van con, nguoi dung sua roi gui lai.
        // (Xem truoc ma thuoc tai khoan nao de kiem tra ca "mat khau khong chua ten dang nhap".)
        service.getAuth().checkPassword(password, resets.peek(token).orElse(null));
        Optional<String> userId = resets.consume(token);
        if (!userId.isPresent()) {
            limiter.hit(ipKey, RESET_WINDOW_MS);
            throw ApiException.badRequest("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu liên kết mới");
        }
        User user = service.getAuth().resetPassword(userId.get(), password);
        // Nguoi nay vua chung minh la chu tai khoan: bo dem "dang nhap sai" de dang nhap lai duoc ngay.
        limiter.reset("login:" + user.getUsername().toLowerCase(java.util.Locale.ROOT) + "|" + ip);
        platform.getSystem().log(user.getUsername(), "Đặt lại mật khẩu", "Qua email", true);
        sendJson(ex, 200, Json.obj().put("ok", true).put("username", user.getUsername()).build());
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
                // Ham nay chi dung cho CHINH tai khoan dang dang nhap (dang nhap, /api/auth/me, dang ky).
                .put("email", user.getEmail())
                // Email da khai nhung chua xac minh qua thu ("" neu khong co): chua dung de lay lai mat khau duoc.
                .put("pendingEmail", platform.getVerifications().pendingEmailOf(user.getId()))
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

    /**
     * Trang "Trang thai": hien bai nop cua MOI NGUOI, nhung chi o muc ai nop bai nao, ket qua gi.
     * Ma nguon va ket qua tung test van phai qua handleSubmissionDetail (xem canSeeSubmission).
     *
     * Mot dong duoc hien khi:
     *  - nguoi xem co quyen xem chi tiet bai nop do (bai cua minh, cua hoc sinh lop minh...), HOAC
     *  - bai tap do nguoi xem mo duoc (kho chung, bai cua lop minh - xem ProblemAccess).
     * Hoc sinh dang trong bai thi thi chi thay bai minh nop cho chinh bai thi do (xem partOfSitting),
     * de khong nhin ket qua cua ban cung thi va khong mo lai bai nop cu.
     */
    private String submissionsJson(User viewer) {
        List<String> items = new ArrayList<>();
        com.ptit.oj.model.Contest sitting = User.ROLE_STUDENT.equals(viewer.getRoleCode())
                ? platform.getContests().sittingContestOf(viewer.getId()) : null;
        if (sitting != null) {
            for (Submission s : service.getSubmissionsOf(viewer)) {
                if (partOfSitting(sitting, viewer, s)) items.add(submissionJson(s, false));
            }
            return Json.array(items);
        }
        ProblemAccess access = platform.accessFor(viewer);
        Set<String> myStudents = platform.getSchool().studentIdsTaughtBy(viewer.getId());
        for (Submission s : service.getSubmissionsNewestFirst()) {
            boolean canSeeDetail = canSeeSubmission(viewer, s, myStudents);
            if (canSeeDetail || access.canOpen(s.getProblem())) items.add(submissionJson(s, false));
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

    /**
     * Hoc sinh DANG TRONG BAI THI chi thay cac bai minh nop cho chinh bai thi do (bai thuoc ky thi, nop
     * sau luc bam "Vao thi"): bai nop cu - kem ma nguon - cua cac bai khac khong mo lai duoc trong luc thi.
     * sitting = null (khong dang thi) thi khong han che gi.
     */
    private static boolean partOfSitting(com.ptit.oj.model.Contest sitting, User viewer, Submission s) {
        if (sitting == null) return true;
        java.time.LocalDateTime joined = sitting.getJoinedAt().get(viewer.getId());
        return sitting.getProblemIds().contains(s.getProblem().getId())
                && joined != null && !s.getSubmittedAt().isBefore(joined);
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
        if (User.ROLE_STUDENT.equals(viewer.getRoleCode())
                && !partOfSitting(platform.getContests().sittingContestOf(viewer.getId()), viewer, s)) {
            throw ApiException.forbidden("Bạn đang trong bài thi nên chỉ xem được bài nộp của bài thi này");
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
             .put("total", r.getTotalTests());
        }

        if (detail) {
            // Thong bao loi bien dich co the trich ca dong code, nen chi gui kem ban chi tiet.
            if (r != null) o.put("message", r.getGlobalMessage());
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

        // Dang trong bai thi: ghi lai dia chi nop bai vao nhat ky ky thi (ca khi ky thi khong bat giam sat).
        if (access.getSitting() != null) access.getSitting().noteIp(author.getId(), clientIp(ex));

        JudgeResult result;
        // Moi nguoi mot bai mot luc, ca he thong co tran so bai dang cho: xem SubmissionGate.
        platform.getGate().enter(author.getId());
        try {
            result = service.submitCode(problem, author, language, code);
        } catch (RuntimeException e) {
            // Vi du khong luu duoc vao CSDL: ghi log de sua, chi bao cho nguoi dung mot cau chung.
            System.err.println("Lỗi khi chấm bài: " + e);
            throw new ApiException(500, "Lỗi máy chủ khi chấm bài");
        } finally {
            platform.getGate().leave(author.getId());
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
        if (!fromTrustedOrigin(ex)) {
            sendError(ex, 403, "Hãy truy cập bằng địa chỉ chính thức của hệ thống");
            return;
        }
        if (!"GET".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "Chỉ hỗ trợ GET cho file tĩnh");
            return;
        }
        String urlPath = ex.getRequestURI().getPath();
        if (urlPath.endsWith("/")) urlPath += "index.html";

        Path file = safeResolve(urlPath);
        // SPA: duong dan cua mot trang (/problems, /problems/K001...) khong ung voi file nao -> tra ve
        // index.html de giao dien tu mo dung trang. Duong dan CO duoi file ma khong ton tai (vi du mot
        // file .js cua ban build cu) thi tra 404, khong tra trang HTML gia lam file do.
        if (file == null || !Files.isRegularFile(file)) {
            String lastSegment = urlPath.substring(urlPath.lastIndexOf('/') + 1);
            if (lastSegment.contains(".") && !lastSegment.equals("index.html")) {
                sendError(ex, 404, "Không có file này");
                return;
            }
            Path index = webRoot.resolve("index.html");
            if (!Files.isRegularFile(index)) {
                sendText(ex, 404, "Chưa build frontend. Chạy: cd frontend && npm install && npm run build");
                return;
            }
            file = index;
        }
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        secure(ex, cspPage);
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
        secure(ex, CSP_API);
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
        secure(ex, CSP_API);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }
}
