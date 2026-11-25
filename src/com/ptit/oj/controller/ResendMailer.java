package com.ptit.oj.controller;

import com.ptit.oj.dao.DatabaseConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Gui email qua Resend (https://resend.com) bang REST API tren HTTPS.
 *
 * Vi sao khong dung SMTP: nhieu nen tang hosting (Railway goi Free / Hobby...) chan cong SMTP di ra,
 * con HTTPS thi luon di duoc. Resend khong co SDK chinh thuc cho Java thuan nen goi thang API.
 *
 * Bat khi dat CA BA bien moi truong:
 *   RESEND_API_KEY - khoa API cua Resend, loai "Sending access" gioi han trong ten mien gui thu;
 *   OJ_MAIL_FROM   - nguoi gui, vi du "CodeHub <no-reply@mail.example.com>" (ten mien da xac minh o Resend);
 *   OJ_PUBLIC_URL  - dia chi trang web, vi du "https://example.com", de dung link trong thu.
 * Thieu mot trong ba thi coi nhu tat: giao dien khong hien "Quen mat khau?".
 *
 * Link trong thu KHONG BAO GIO dung tu header Host cua request: ke gui request tu dat header do duoc
 * va se lai nguoi nhan thu sang trang gia.
 */
public final class ResendMailer {

    public static final String API_KEY_ENV = "RESEND_API_KEY";
    public static final String FROM_ENV = "OJ_MAIL_FROM";
    public static final String PUBLIC_URL_ENV = "OJ_PUBLIC_URL";
    /** Chi dung khi kiem thu: tro sang mot may chu gia thay cho api.resend.com. */
    static final String API_URL_ENV = "OJ_RESEND_API_URL";

    private static final String DEFAULT_API_URL = "https://api.resend.com";
    private static final int TIMEOUT_MS = 10_000;
    /** Mot lan gui thu toi da 4 lan goi API; giua cac lan cho 1, 2, 4 giay. */
    private static final int MAX_ATTEMPTS = 4;
    private static final long MAX_RETRY_WAIT_MS = 30_000;
    private static final int MAX_QUEUED = 200;

    private final String apiKey;
    private final String from;
    private final String publicUrl;
    private final String apiUrl;

    /** Mot luong gui lan luot: request cua nguoi dung tra loi ngay, khong cho Resend. */
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(MAX_QUEUED), runnable -> {
                Thread thread = new Thread(runnable, "resend-mailer");
                thread.setDaemon(true);
                return thread;
            });

    public ResendMailer() {
        this(DatabaseConfig.setting(API_KEY_ENV), DatabaseConfig.setting(FROM_ENV),
                DatabaseConfig.setting(PUBLIC_URL_ENV), DatabaseConfig.setting(API_URL_ENV));
    }

    ResendMailer(String apiKey, String from, String publicUrl, String apiUrl) {
        this.apiKey = clean(apiKey);
        this.from = clean(from);
        String url = clean(publicUrl);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        // Chi nhan dia chi http(s); gia tri la thi coi nhu chua dat.
        this.publicUrl = url.startsWith("https://") || url.startsWith("http://") ? url : "";
        String api = clean(apiUrl);
        this.apiUrl = api.isEmpty() ? DEFAULT_API_URL : api;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty() && !from.isEmpty() && !publicUrl.isEmpty();
    }

    /** Dia chi trang web (khong co dau / o cuoi); "" neu chua dat. */
    public String getPublicUrl() {
        return publicUrl;
    }

    /** Mot dong mo ta trang thai de in luc khoi dong. KHONG in khoa API. */
    public String describe() {
        if (isEnabled()) return "Bật (người gửi: " + from + ", link về " + publicUrl + ")";
        return "Tắt (đặt " + API_KEY_ENV + ", " + FROM_ENV + ", " + PUBLIC_URL_ENV + " để bật quên mật khẩu)";
    }

    /**
     * Xep thu vao hang cho gui o luong nen. idempotencyKey: cung mot khoa thi Resend chi gui MOT thu
     * du may chu co goi lai nhieu lan (mang chap chon, het thoi gian cho...).
     * @return false neu hang cho da day va thu bi bo
     */
    public boolean queue(String to, String subject, String html, String text, String idempotencyKey) {
        try {
            worker.execute(() -> {
                try {
                    String id = send(to, subject, html, text, idempotencyKey);
                    System.out.println("Đã gửi email \"" + subject + "\" qua Resend, id = " + id);
                } catch (IOException | RuntimeException e) {
                    System.err.println("Không gửi được email \"" + subject + "\": " + e.getMessage());
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            System.err.println("Hàng chờ gửi email đã đầy, bỏ thư \"" + subject + "\"");
            return false;
        }
    }

    /**
     * Gui mot thu va cho ket qua. Tu thu lai khi loi mang, HTTP 5xx hoac 429 (theo Retry-After);
     * cac loi 4xx khac la loi cau hinh / du lieu, thu lai cung vo ich nen bao loi ngay.
     * @return ma thu cua Resend
     */
    public String send(String to, String subject, String html, String text, String idempotencyKey) throws IOException {
        if (!isEnabled()) throw new IOException("Chưa cấu hình gửi email");
        String payload = Json.obj()
                .put("from", from)
                .putRaw("to", Json.array(Collections.singletonList(Json.quote(to))))
                .put("subject", subject)
                .put("html", html)
                .put("text", text)
                .build();
        IOException last = null;
        long wait = 1000;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Bị ngắt khi đang chờ gửi lại");
                }
                wait = Math.min(wait * 2, MAX_RETRY_WAIT_MS);
            }
            Response response;
            try {
                response = post(payload, idempotencyKey);
            } catch (IOException e) {
                last = new IOException("không gọi được Resend (" + e.getMessage() + ")");
                continue;
            }
            if (response.status >= 200 && response.status < 300) {
                try {
                    return Json.parseObject(response.body).getString("id", "");
                } catch (RuntimeException e) {
                    return "";
                }
            }
            last = new IOException("Resend trả về HTTP " + response.status + ": " + shorten(response.body));
            boolean retryable = response.status == 429 || response.status >= 500;
            if (!retryable) throw last;
            if (response.retryAfterSeconds > 0) {
                wait = Math.min(response.retryAfterSeconds * 1000L, MAX_RETRY_WAIT_MS);
            }
        }
        throw last;
    }

    private static final class Response {
        private final int status;
        private final String body;
        private final long retryAfterSeconds;

        Response(int status, String body, long retryAfterSeconds) {
            this.status = status;
            this.body = body;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    private Response post(String payload, String idempotencyKey) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl + "/emails").openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            // Resend tu choi request khong co User-Agent.
            connection.setRequestProperty("User-Agent", "codehub-oj/1.0");
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                connection.setRequestProperty("Idempotency-Key", idempotencyKey);
            }
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            long retryAfter = 0;
            try {
                String header = connection.getHeaderField("Retry-After");
                if (header != null) retryAfter = Math.max(0, Long.parseLong(header.trim()));
            } catch (NumberFormatException ignored) {
                // Retry-After dang ngay thang: dung khoang cho mac dinh.
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = "";
            if (stream != null) {
                try (InputStream in = stream) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096];
                    int n;
                    while ((n = in.read(chunk)) != -1 && buffer.size() < 65536) buffer.write(chunk, 0, n);
                    body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return new Response(status, body, retryAfter);
        } finally {
            connection.disconnect();
        }
    }

    private static String shorten(String body) {
        String oneLine = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }

    /** Thoat ky tu dac biet truoc khi chen van ban cua nguoi dung (ho ten...) vao HTML cua thu. */
    public static String escapeHtml(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }
}
