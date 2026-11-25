package com.ptit.oj.controller;

import com.ptit.oj.model.User;

/**
 * Gui thu xac minh email khoi phuc (xem EmailVerificationService), kem cac gioi han de form dang ky
 * va trang tai khoan khong bi dung de doi bom hop thu cua nguoi khac.
 */
public final class AccountMailer {

    private static final long COOLDOWN_MS = 60_000L;
    private static final long HOUR_MS = 60 * 60_000L;
    private static final long DAY_MS = 24 * HOUR_MS;
    /** Moi tai khoan xin gui thu xac minh toi da chung nay lan mot gio. */
    private static final int USER_HOUR_LIMIT = 5;
    /** Moi DIA CHI nhan toi da chung nay thu xac minh, du do bao nhieu tai khoan khai. */
    private static final int RECIPIENT_HOUR_LIMIT = 3;
    private static final int RECIPIENT_DAY_LIMIT = 6;

    private final ResendMailer mailer;
    private final EmailVerificationService verifications;
    private final RateLimiter limiter;

    AccountMailer(ResendMailer mailer, EmailVerificationService verifications, RateLimiter limiter) {
        this.mailer = mailer;
        this.verifications = verifications;
        this.limiter = limiter;
    }

    /** Co gui duoc thu khong. Khong gui duoc thi email khai la co hieu luc ngay (khong co cach xac minh). */
    public boolean isEnabled() {
        return mailer.isEnabled();
    }

    /**
     * Ghi nhan email dang cho cua tai khoan va gui thu xac minh toi dia chi do.
     *
     * @return 0 neu da nhan yeu cau; so giay phai doi neu tai khoan nay vua xin gui xong.
     *         Dia chi nhan da nhan qua nhieu thu thi van tra ve 0 nhung KHONG gui (va khong doi email
     *         dang cho), de nguoi goi khong biet duoc dia chi do dang bi ai khac khai.
     */
    public long sendVerification(User user, String email) {
        String coolKey = "verify-cool:" + user.getId();
        String hourKey = "verify-user:" + user.getId();
        long wait = Math.max(limiter.retryAfterSeconds(coolKey, COOLDOWN_MS, 1),
                limiter.retryAfterSeconds(hourKey, HOUR_MS, USER_HOUR_LIMIT));
        if (wait > 0) return wait;
        limiter.hit(coolKey, COOLDOWN_MS);
        limiter.hit(hourKey, HOUR_MS);

        String toHourKey = "verify-to:" + email;
        String toDayKey = "verify-to-day:" + email;
        if (limiter.retryAfterSeconds(toHourKey, HOUR_MS, RECIPIENT_HOUR_LIMIT) > 0
                || limiter.retryAfterSeconds(toDayKey, DAY_MS, RECIPIENT_DAY_LIMIT) > 0) {
            return 0;
        }
        limiter.hit(toHourKey, HOUR_MS);
        limiter.hit(toDayKey, DAY_MS);

        String token = verifications.issue(user.getId(), email);
        String link = mailer.getPublicUrl() + "/verify-email?token=" + token;
        mailer.queue(email, "Xác minh email CodeHub", html(user, link), text(user, link),
                "verify-email/" + PasswordResetService.hash(token));
        return 0;
    }

    private static String text(User user, String link) {
        return "Xin chào,\n\n"
                + "Tài khoản @" + user.getUsername() + " trên CodeHub vừa khai địa chỉ này làm email khôi phục mật khẩu.\n"
                + "Mở liên kết sau để xác minh (có hiệu lực 24 giờ, dùng được một lần):\n\n"
                + link + "\n\n"
                + "Nếu không phải bạn, hãy bỏ qua thư này: địa chỉ của bạn sẽ không được gắn với tài khoản nào.\n";
    }

    private static String html(User user, String rawLink) {
        String link = ResendMailer.escapeHtml(rawLink);
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#212121;max-width:520px;margin:0 auto;padding:24px\">"
                + "<h2 style=\"font-size:20px;margin:0 0 16px\">Xác minh email CodeHub</h2>"
                + "<p>Tài khoản <b>@" + ResendMailer.escapeHtml(user.getUsername()) + "</b> vừa khai địa chỉ này làm email khôi phục mật khẩu.</p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + link + "\" style=\"display:inline-block;padding:12px 22px;background:#F7931E;color:#111111;"
                + "text-decoration:none;border-radius:8px;font-weight:bold\">Xác minh email</a></p>"
                + "<p>Liên kết có hiệu lực 24 giờ và chỉ dùng được một lần. Nếu nút không bấm được, hãy dán địa chỉ sau vào trình duyệt:</p>"
                + "<p style=\"word-break:break-all;font-size:13px;color:#616161\">" + link + "</p>"
                + "<p style=\"font-size:13px;color:#757575\">Nếu không phải bạn, hãy bỏ qua thư này: địa chỉ của bạn sẽ không được gắn với tài khoản nào.</p>"
                + "</div>";
    }
}
