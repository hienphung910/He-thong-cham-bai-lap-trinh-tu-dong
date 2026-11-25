package com.ptit.oj.controller;

import com.ptit.oj.dao.DatabaseConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * CAPTCHA Cloudflare Turnstile cho form dang ky (mien phi, phan lon nguoi dung khong phai giai gi).
 *
 * Bat khi dat CA HAI bien moi truong:
 *   OJ_TURNSTILE_SITE_KEY - khoa cong khai, gui cho trinh duyet de hien widget;
 *   OJ_TURNSTILE_SECRET   - khoa bi mat, chi may chu giu de hoi Cloudflare xem token co that khong.
 * Thieu mot trong hai thi coi nhu tat: dang ky khong doi CAPTCHA.
 *
 * Trinh duyet lam xong thu thach thi nhan mot token dung MOT LAN; may chu gui token do toi
 * /siteverify cua Cloudflare, Cloudflare tra ve success = true / false.
 */
public final class TurnstileVerifier {

    public static final String SITE_KEY_ENV = "OJ_TURNSTILE_SITE_KEY";
    public static final String SECRET_ENV = "OJ_TURNSTILE_SECRET";

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final int TIMEOUT_MS = 6000;
    /** Token cua Turnstile dai toi da 2048 ky tu. */
    private static final int MAX_TOKEN_CHARS = 2048;

    private final String siteKey;
    private final String secret;

    public TurnstileVerifier() {
        this(DatabaseConfig.setting(SITE_KEY_ENV), DatabaseConfig.setting(SECRET_ENV));
    }

    TurnstileVerifier(String siteKey, String secret) {
        this.siteKey = siteKey == null ? "" : siteKey.trim();
        this.secret = secret == null ? "" : secret.trim();
    }

    public boolean isEnabled() {
        return !siteKey.isEmpty() && !secret.isEmpty();
    }

    /** Khoa cong khai de trinh duyet hien widget; "" neu CAPTCHA dang tat. */
    public String getSiteKey() {
        return isEnabled() ? siteKey : "";
    }

    /**
     * Hoi Cloudflare xem token co hop le khong.
     * @throws IOException neu khong goi duoc Cloudflare (mat mang, qua thoi gian...)
     */
    public boolean verify(String token, String remoteIp) throws IOException {
        if (token == null || token.trim().isEmpty() || token.length() > MAX_TOKEN_CHARS) return false;
        String form = "secret=" + encode(secret) + "&response=" + encode(token.trim())
                + (remoteIp == null || remoteIp.isEmpty() ? "" : "&remoteip=" + encode(remoteIp));
        HttpURLConnection connection = (HttpURLConnection) new URL(VERIFY_URL).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(form.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) throw new IOException("Cloudflare trả về HTTP " + status + " không kèm nội dung");
            String body;
            try (InputStream in = stream) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1 && buffer.size() < 65536) buffer.write(chunk, 0, n);
                body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
            try {
                return Json.parseObject(body).getBoolean("success", false);
            } catch (RuntimeException e) {
                throw new IOException("Cloudflare trả về nội dung không đọc được (HTTP " + status + ")");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }
}
