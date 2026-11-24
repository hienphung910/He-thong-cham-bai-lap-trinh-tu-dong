package com.ptit.oj.model;

import java.time.LocalDateTime;

/**
 * Mot su kien giam sat trong ky thi: thi sinh roi tab, chuyen sang cua so khac, sao chep, dan...
 *
 * Trinh duyet cua thi sinh tu ghi nhan va gui len; nguoi tao ky thi (va quan tri vien) xem lai
 * tren trang ky thi. Day la du lieu do phia trinh duyet bao cao nen chi mang tinh tham khao:
 * thi sinh tat JavaScript hoac dung thiet bi khac thi khong co gi duoc ghi.
 */
public class ProctorEvent extends Entity {

    /** Cac loai su kien duoc chap nhan. */
    public static final String TAB_HIDDEN = "TAB_HIDDEN";         // chuyen tab khac / thu nho trinh duyet
    public static final String TAB_RETURN = "TAB_RETURN";         // quay lai tab thi
    public static final String WINDOW_BLUR = "WINDOW_BLUR";       // chuyen sang cua so / ung dung khac
    public static final String WINDOW_RETURN = "WINDOW_RETURN";   // quay lai cua so thi
    public static final String COPY = "COPY";
    public static final String CUT = "CUT";
    public static final String PASTE = "PASTE";
    public static final String PAGE_CLOSED = "PAGE_CLOSED";       // dong tab hoac tai lai trang
    /**
     * Trinh duyet cua thi sinh vua bat dau giam sat (mo ky thi hoac mot bai cua ky thi). Khong phai
     * vi pham; ghi lai de nguoi tao ky thi biet thi sinh nao dang duoc giam sat du chua co vi pham nao.
     */
    public static final String MONITOR_START = "MONITOR_START";

    private final String contestId;
    private final String userId;
    private final String type;
    private final String detail;       // vi du "vang 12 giay", "dan 340 ky tu"
    private final String problemId;    // bai dang mo luc xay ra; "" neu khong o trang lam bai
    private final LocalDateTime at = LocalDateTime.now();

    public ProctorEvent(String id, String contestId, String userId, String type, String detail, String problemId) {
        super(id);
        this.contestId = contestId;
        this.userId = userId;
        this.type = type;
        this.detail = detail;
        this.problemId = problemId;
    }

    public static boolean isKnownType(String type) {
        return TAB_HIDDEN.equals(type) || TAB_RETURN.equals(type) || WINDOW_BLUR.equals(type)
                || WINDOW_RETURN.equals(type) || COPY.equals(type) || CUT.equals(type)
                || PASTE.equals(type) || PAGE_CLOSED.equals(type) || MONITOR_START.equals(type);
    }

    /** Su kien "roi di" hoac thao tac dang ngo - duoc dem la mot lan vi pham. Su kien "quay lai" thi khong. */
    public boolean isViolation() {
        return !TAB_RETURN.equals(type) && !WINDOW_RETURN.equals(type) && !MONITOR_START.equals(type);
    }

    public String getContestId() { return contestId; }
    public String getUserId() { return userId; }
    public String getType() { return type; }
    public String getDetail() { return detail; }
    public String getProblemId() { return problemId; }
    public LocalDateTime getAt() { return at; }
}
