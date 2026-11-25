package com.ptit.oj.controller.judge;

import com.ptit.oj.dao.DatabaseConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Co lap bai nop: chay trinh bien dich va chuong trinh cua thi sinh bang MOT TAI KHOAN HE THONG
 * KHAC voi tai khoan cua may chu (qua "sudo -u"), kem gioi han tai nguyen (prlimit).
 *
 * Bat bang bien moi truong OJ_SANDBOX_USER (image Docker dat san la "ojrunner"). Khi bat:
 *  - ma cua thi sinh KHONG doc duoc thu muc data/ (test an, file minh chung), khong doc duoc bien
 *    moi truong cua may chu qua /proc (mat khau CSDL, mat khau admin), khong dung duoc tien trinh
 *    may chu - vi tat ca thuoc ve mot tai khoan khac va thu muc data/ chi chu so huu vao duoc;
 *  - gioi han so tien trinh (chong fork bomb), kich thuoc file ghi ra, va bo nho ao voi C++ / Python;
 *  - sau moi lan cham, moi tien trinh con sot lai cua tai khoan do bi diet va file no tao bi xoa.
 *
 * KHONG chan duoc: ket noi mang ra ngoai va viec dung CPU trong gioi han thoi gian. Muon chan mang
 * can container / namespace rieng cho tung lan chay, vuot qua kha nang cua mot container thuong.
 *
 * Neu khong dat OJ_SANDBOX_USER, hoac may khong chay duoc sudo (Windows, may ca nhan, nen tang cam
 * nang quyen), lop nay tu tat va bo cham chay nhu truoc; trang thai in ra luc khoi dong va hien o
 * Quan tri -> He thong. Tat ca cac ham deu an toan khi sandbox tat (khong lam gi).
 *
 * Moi lan chi co MOT bai duoc cham / chay thu (JudgeService khoa tuan tu), nen viec "diet moi tien
 * trinh cua tai khoan sandbox" khong dung nham vao bai cua nguoi khac.
 */
public final class Sandbox {

    public static final String USER_ENV = "OJ_SANDBOX_USER";

    /** Bo nho ao cho buoc bien dich (cc1plus can nhieu hon chuong trinh cua thi sinh). */
    public static final int COMPILE_MEMORY_MB = 2048;

    private static final long FILE_LIMIT_BYTES = 64L * 1024 * 1024;
    /** Tinh ca luong: JVM tu tao vai chuc luong, nen khong dat qua thap. */
    private static final int PROCESS_LIMIT = 256;
    /** Phan cong them vao gioi han bo nho cua bai: thu vien dong, ngan xep, trinh thong dich. */
    private static final int MEMORY_HEADROOM_MB = 128;
    private static final int MIN_ADDRESS_SPACE_MB = 256;

    /** null = sandbox tat. */
    private static volatile String user;
    private static volatile String status = "Tắt (chưa khởi tạo)";

    private Sandbox() { }

    /** Goi mot lan luc khoi dong: doc cau hinh va THU chay mot lenh bang tai khoan sandbox. */
    public static synchronized void init() {
        user = null;
        String configured = DatabaseConfig.setting(USER_ENV);
        if (configured == null || configured.trim().isEmpty()) {
            status = "Tắt: chưa đặt " + USER_ENV + " (bài nộp chạy bằng chính tài khoản của máy chủ)";
            return;
        }
        String name = configured.trim();
        if (!name.matches("[a-z_][a-z0-9_-]{0,30}")) {
            status = "Tắt: " + USER_ENV + " không phải tên tài khoản hợp lệ";
            return;
        }
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            status = "Tắt: chỉ hỗ trợ Linux";
            return;
        }
        String[] mine = exec(10, "id", "-u");
        String[] theirs = exec(10, "sudo", "-n", "-u", name, "--", "id", "-u");
        if (theirs == null || !"0".equals(theirs[0]) || !theirs[1].trim().matches("\\d+")) {
            status = "Tắt: không chạy được \"sudo -n -u " + name + "\""
                    + (theirs == null ? "" : " (" + firstLine(theirs[2]) + ")");
            return;
        }
        if (mine != null && mine[1].trim().equals(theirs[1].trim())) {
            status = "Tắt: " + name + " trùng với tài khoản đang chạy máy chủ";
            return;
        }
        String[] limits = exec(10, "sudo", "-n", "-u", name, "--", "prlimit", "--nproc=" + PROCESS_LIMIT, "--", "true");
        if (limits == null || !"0".equals(limits[0])) {
            status = "Tắt: không chạy được prlimit bằng tài khoản " + name
                    + (limits == null ? "" : " (" + firstLine(limits[2]) + ")");
            return;
        }
        user = name;
        status = "Bật: bài nộp chạy bằng tài khoản hệ thống riêng \"" + name + "\"";
    }

    public static boolean isActive() { return user != null; }

    /** Mot dong mo ta trang thai, de in luc khoi dong va hien o trang quan tri. */
    public static String describe() { return status; }

    /**
     * Boc lenh de chay bang tai khoan sandbox kem gioi han tai nguyen. memoryLimitMb = 0 thi khong
     * gioi han bo nho ao. java / javac / node tu dat tran bo nho rieng (-Xmx, --max-old-space-size)
     * va can vung dia chi ao rat lon de khoi dong, nen khong ap gioi han bo nho ao cho chung.
     */
    static List<String> wrap(List<String> command, int memoryLimitMb) {
        String name = user;
        if (name == null) return command;
        List<String> out = new ArrayList<>(Arrays.asList("sudo", "-n", "-H", "-u", name, "--",
                "prlimit", "--nproc=" + PROCESS_LIMIT, "--fsize=" + FILE_LIMIT_BYTES, "--core=0"));
        String exe = command.get(0);
        exe = exe.substring(Math.max(exe.lastIndexOf('/'), exe.lastIndexOf('\\')) + 1);
        boolean ownLimit = exe.equals("java") || exe.equals("javac") || exe.equals("node");
        if (memoryLimitMb > 0 && !ownLimit) {
            long megabytes = Math.max(MIN_ADDRESS_SPACE_MB, (long) memoryLimitMb + MEMORY_HEADROOM_MB);
            out.add("--as=" + megabytes * 1024 * 1024);
        }
        out.add("--");
        out.addAll(command);
        return out;
    }

    /** Thu muc lam viec do may chu tao (chi chu so huu vao duoc): mo cho tai khoan sandbox doc / ghi. */
    public static void prepareWorkDir(Path dir) {
        if (user == null || dir == null) return;
        // Truoc khi bat dau mot lan cham moi: bao dam khong con tien trinh nao cua lan truoc de
        // can thiep vao thu muc lam viec cua nguoi nay.
        killAll();
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (IOException | UnsupportedOperationException e) {
            // Khong mo duoc quyen: buoc bien dich se bao loi ro rang, khong can xu ly o day.
        }
    }

    /** Diet moi tien trinh cua tai khoan sandbox - dung khi bai chay qua gio, vi may chu khong tu kill duoc tien trinh cua tai khoan khac. */
    static void killAll() {
        String name = user;
        if (name == null) return;
        exec(10, "sudo", "-n", "-u", name, "--", "sh", "-c", "kill -9 -1 2>/dev/null; exit 0");
    }

    /**
     * Don dep sau mot lan cham: diet tien trinh con sot lai, xoa moi file tai khoan sandbox da tao
     * (trong thu muc lam viec va cac thu muc tam). Phan con lai cua thu muc lam viec do may chu xoa.
     */
    public static void cleanup(Path workDir) {
        String name = user;
        if (name == null) return;
        String script = "kill -9 -1 2>/dev/null; "
                + "find /tmp /var/tmp /dev/shm \"$1\" \"$3\" -mindepth 1 -user \"$2\" -delete 2>/dev/null; exit 0";
        String dir = workDir == null ? "/tmp" : workDir.toString();
        exec(20, "sudo", "-n", "-u", name, "--", "sh", "-c", script, "sh", "/home/" + name, name, dir);
    }

    /** Chay mot lenh ngan cua chinh may chu; tra ve {ma thoat, stdout, stderr}, null neu khong chay duoc. */
    private static String[] exec(int timeoutSeconds, String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
            process.getOutputStream().close();
            boolean done = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return null;
            }
            return new String[] {String.valueOf(process.exitValue()), read(process.getInputStream()), read(process.getErrorStream())};
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1 && buffer.size() < 65536) buffer.write(chunk, 0, n);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String firstLine(String text) {
        String t = text == null ? "" : text.trim();
        int nl = t.indexOf('\n');
        t = nl < 0 ? t : t.substring(0, nl);
        return t.length() > 160 ? t.substring(0, 160) : t;
    }
}
