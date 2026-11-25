package com.ptit.oj.controller;

import java.util.HashSet;
import java.util.Set;

/**
 * Cong vao cua bo cham. Bai duoc cham tuan tu va moi request nop bai giu mot luong HTTP cho toi khi
 * cham xong; may chu chi co 32 luong, nen neu khong chan thi vai chuc bai cho la moi trang deu dung.
 *
 * Cong nay giu hai gioi han:
 *  - moi nguoi chi co MOT bai (nop bai hoac chay thu) dang cho / dang cham tai mot thoi diem;
 *  - ca he thong toi da MAX_WAITING bai dang cho, de luon con luong phuc vu cac trang khac.
 * Vuot gioi han thi tu choi ngay kem thong bao, thay vi de request treo.
 */
public final class SubmissionGate {

    /** Mot nua so luong HTTP cua may chu (ApiServer dung 32 luong). */
    private static final int MAX_WAITING = 16;

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
