package com.ptit.oj.controller.judge;

import com.ptit.oj.dao.DatabaseConfig;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

/**
 * Cac "o cham": so bai duoc bien dich / chay CUNG LUC. Moi lan cham, chay thu hay stress test giu mot o
 * tu luc bat dau toi luc xong; het o thi cho.
 *
 * Vi sao co gioi han va khong cham tuy y song song:
 *  - Thoi gian chay duoc do bang dong ho thuc: nhieu bai hon so nhan CPU thi chung tranh nhau CPU va bi
 *    TLE oan. So o mac dinh = so nhan CPU may chu thay duoc (OJ_JUDGE_WORKERS de dat tay).
 *  - Khi sandbox bat, moi o dung mot tai khoan sandbox rieng (Sandbox.bindSlot): lenh "diet moi tien trinh
 *    cua tai khoan" sau moi lan chay, va gioi han so tien trinh theo tai khoan, khong dung sang o khac. Nen
 *    so o khong vuot so tai khoan sandbox dung duoc (OJ_SANDBOX_USERS).
 */
public final class JudgeSlots {

    public static final String WORKERS_ENV = "OJ_JUDGE_WORKERS";

    private static volatile BlockingQueue<Integer> free = new ArrayBlockingQueue<>(1, false, java.util.Collections.singletonList(0));
    private static volatile int size = 1;

    private JudgeSlots() { }

    /** Goi mot lan luc khoi dong, SAU Sandbox.init(). */
    public static synchronized void init() {
        int cpus = Math.max(1, Runtime.getRuntime().availableProcessors());
        int wanted = cpus;
        String configured = DatabaseConfig.setting(WORKERS_ENV);
        if (configured != null && configured.trim().matches("\\d{1,2}")) {
            wanted = Math.max(1, Integer.parseInt(configured.trim()));
        }
        int n = Math.min(wanted, 16);
        if (Sandbox.isActive()) n = Math.min(n, Sandbox.userCount());
        // fair = true: luong cho lau nhat duoc o truoc (bai nop truoc cham truoc).
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(n, true);
        for (int i = 0; i < n; i++) queue.add(i);
        free = queue;
        size = n;
    }

    public static int size() { return size; }

    /** So o dang ranh (de hien trang thai / uoc luong hang cho). */
    public static int available() { return free.size(); }

    public static String describe() {
        return size + " ô chấm song song" + (Sandbox.isActive() ? " (mỗi ô một tài khoản sandbox)" : "");
    }

    /** Chay mot viec trong mot o cham: cho o ranh, gan tai khoan sandbox cua o cho luong nay, chay, tra o. */
    public static <T> T run(Callable<T> work) throws Exception {
        BlockingQueue<Integer> queue = free;
        Integer slot = queue.take();
        Sandbox.bindSlot(slot);
        try {
            return work.call();
        } finally {
            Sandbox.bindSlot(-1);
            queue.add(slot);
        }
    }
}
