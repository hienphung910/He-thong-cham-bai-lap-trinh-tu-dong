package com.ptit.oj.controller;

import java.util.HashSet;
import java.util.Set;

/**
 * Cong vao cua bo cham. Nop bai vao hang doi cham (khong giu luong HTTP); chay thu / stress test van cho
 * ket qua trong request nen van giu mot luong HTTP. Gioi han tong de hang doi khong dai vo han.
 *
 * Cong nay giu hai gioi han:
 *  - moi nguoi chi co MOT bai (nop bai hoac chay thu) dang cho / dang cham tai mot thoi diem;
 *  - ca he thong toi da MAX_WAITING bai dang cho, de luon con luong phuc vu cac trang khac.
 * Vuot gioi han thi tu choi ngay kem thong bao, thay vi de request treo.
 */
public final class SubmissionGate {

    /** Toi da so bai dang cho / dang cham tren ca he thong (ca lop 40-60 em cung nop van vua). */
    private static final int MAX_WAITING = 120;

    private final Set<String> waiting = new HashSet<>();

    /** Goi truoc khi cham; PHAI goi leave() trong finally. */
    public synchronized void enter(String userId) {
        if (waiting.contains(userId)) {
            throw new ApiException(429, "Bài trước của bạn chưa chấm xong. Hãy đợi kết quả rồi nộp tiếp");
        }
        if (waiting.size() >= MAX_WAITING) {
            throw new ApiException(503, "Hệ thống đang chấm nhiều bài. Hãy thử lại sau ít giây");
        }
        waiting.add(userId);
    }

    public synchronized void leave(String userId) {
        waiting.remove(userId);
    }
}
