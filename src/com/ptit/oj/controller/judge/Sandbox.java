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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Co lap bai nop: chay trinh bien dich va chuong trinh cua thi sinh bang MOT TAI KHOAN HE THONG
 * KHAC voi tai khoan cua may chu (qua "sudo -u"), kem gioi han tai nguyen (prlimit).
 *
 * Bat bang bien moi truong OJ_SANDBOX_USER (image Docker dat san la "ojrunner"). Khi bat:
 *  - ma cua thi sinh KHONG doc duoc thu muc data/ (test an, file minh chung), khong doc duoc bien
 *    moi truong cua may chu qua /proc (mat khau CSDL, mat khau admin), khong dung duoc tien trinh
 *    may chu - vi tat ca thuoc ve mot tai khoan khac va thu muc data/ chi chu so huu vao duoc;
 *  - moi buoc (bien dich / chay) co gioi han rieng theo ngon ngu (xem Limits): so tien trinh + luong,
 *    bo nho, thoi gian CPU, so file mo, kich thuoc file ghi ra. Chuong trinh C++ / Python chi duoc dung
 *    MOT tien trinh (khong fork, khong tao luong), nen tong bo nho cua bai nop khong vuot gioi han;
 *  - Java chay voi Security Manager (JDK duoi 24): khong goi chuong trinh khac, khong mo mang, khong doc
 *    file, khong dung Unsafe. Node chay voi che do phan quyen (--permission): khong goi chuong trinh khac,
 *    khong tao worker, khong doc / ghi file ngoai thu muc bai;
 *  - neu nen tang cho phep tao "network namespace" rieng (unshare), moi buoc chay trong mot mang trong:
 *    khong ket noi ra ngoai duoc. Viec nay duoc THU luc khoi dong; Docker mac dinh khong cho;
 *  - sau moi lan cham, moi tien trinh con sot lai cua tai khoan do bi diet va file no tao bi xoa.
 *
 * KHONG chan duoc khi nen tang khong cho tao network namespace: ket noi mang ra ngoai cua C++, Python
 * va Node (Java van bi Security Manager chan).
 *
 * Neu khong dat OJ_SANDBOX_USER, hoac may khong chay duoc sudo (Windows, may ca nhan, nen tang cam
 * nang quyen), lop nay tu tat va bo cham chay nhu truoc; trang thai in ra luc khoi dong va hien o
 * Quan tri -> He thong. Tat ca cac ham deu an toan khi sandbox tat (khong lam gi).
 *
 * CHAM SONG SONG: moi "o cham" (JudgeSlots) dung MOT tai khoan sandbox rieng (OJ_SANDBOX_USERS =
 * "ojrunner,ojrunner2,..."), nen "diet moi tien trinh cua tai khoan sandbox", gioi han so tien trinh
 * (tinh theo tai khoan) va don file sau khi chay khong dung vao bai dang chay o o khac. Thu muc lam
 * viec cua moi lan cham nam trong mot thu muc cha quyen 711 (khong liet ke duoc) voi ten ngau nhien:
 * chuong trinh o o khac khong tim ra duoc de doc ma nguon hay test an cua nguoi khac.
 */
public final class Sandbox {

    public static final String USER_ENV = "OJ_SANDBOX_USER";
    /** Danh sach tai khoan sandbox, cach nhau dau phay - moi tai khoan mot o cham song song. */
    public static final String USERS_ENV = "OJ_SANDBOX_USERS";

    private static final long FILE_LIMIT_BYTES = 64L * 1024 * 1024;

    /**
     * Gioi han cho mot buoc chay trong sandbox. Cac con so do tren image Docker cua du an (JDK 17, Node 22,
     * g++ 11, Python 3.10): JVM can 14 luong voi -XX:+UseSerialGC -XX:ActiveProcessorCount=1, javac 16,
     * Node 4 voi --v8-pool-size=1, g++ 3 tien trinh va 256 MB bo nho ao cho bits/stdc++.h.
     */
    public static final class Limits {
        /** RLIMIT_NPROC: so tien trinh VA luong cua tai khoan sandbox. 1 = khong fork, khong tao luong. */
        private final int processes;
        /** RLIMIT_AS (MB, 0 = khong dat): toan bo bo nho ao - chat, dung cho chuong trinh C++ / Python. */
        private final int addressSpaceMb;
        /**
         * RLIMIT_DATA (MB, 0 = khong dat): chi bo nho THAT SU duoc cap de ghi (heap, mmap rieng). Dung cho
         * JVM va V8: chung giu cho san hang GB dia chi ao (khong dung toi) nen khong the dung RLIMIT_AS.
         */
        private final int dataMb;
        /** RLIMIT_CPU (giay): chot chan cuoi, gioi han thoi gian thuc van do ProcessRunner lo. */
        private final int cpuSeconds;
        private final int openFiles;

        private Limits(int processes, int addressSpaceMb, int dataMb, int cpuSeconds, int openFiles) {
            this.processes = processes;
            this.addressSpaceMb = addressSpaceMb;
            this.dataMb = dataMb;
            this.cpuSeconds = cpuSeconds;
            this.openFiles = openFiles;
        }

        /** Gioi han theo bo nho ao (chuong trinh native: C++, trinh thong dich Python, g++). */
        public static Limits forNative(int processes, int addressSpaceMb, int cpuSeconds, int openFiles) {
            return new Limits(processes, addressSpaceMb, 0, cpuSeconds, openFiles);
        }

        /** Gioi han theo bo nho du lieu (may ao giu cho truoc nhieu dia chi ao: JVM, Node). */
        public static Limits forManaged(int processes, int dataMb, int cpuSeconds, int openFiles) {
            return new Limits(processes, 0, dataMb, cpuSeconds, openFiles);
        }

        /** Thoi gian CPU cho mot lan chay: thoi gian cho phep (lam tron len giay) nhan he so, cong them 2 giay. */
        public static int cpuSecondsFor(long timeLimitMs, int factor) {
            return (int) Math.min(600, (timeLimitMs + 999) / 1000 * factor + 2);
        }

        public int getProcesses() { return processes; }
        public int getAddressSpaceMb() { return addressSpaceMb; }
        public int getDataMb() { return dataMb; }
    }

    /** Cac tai khoan sandbox dung duoc, moi o cham mot tai khoan; rong = sandbox tat. */
    private static volatile List<String> users = Collections.emptyList();
    /** Tai khoan cua o cham ma luong hien tai dang giu (JudgeSlots gan truoc khi chay). */
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    /** Thu muc cha (quyen 711) chua thu muc lam viec cua moi lan cham; null = dung /tmp nhu cu. */
    private static volatile Path workRoot;

    /** Tai khoan sandbox cho lan chay nay: cua o cham dang giu, hoac tai khoan dau tien. null = sandbox tat. */
    private static String currentUser() {
        String bound = CURRENT.get();
        if (bound != null) return bound;
        List<String> list = users;
        return list.isEmpty() ? null : list.get(0);
    }

    /** So tai khoan sandbox dung duoc (= so o cham toi da khi sandbox bat). */
    public static int userCount() { return users.size(); }

    /** Gan tai khoan cua o cham thu slot cho luong hien tai (JudgeSlots goi); slot < 0 = bo gan. */
    static void bindSlot(int slot) {
        List<String> list = users;
        if (slot < 0 || list.isEmpty()) CURRENT.remove();
        else CURRENT.set(list.get(slot % list.size()));
    }

    /**
     * Tao thu muc lam viec cho mot lan cham / chay thu. Sandbox bat: nam trong thu muc cha 711 voi ten
     * ngau nhien, de chuong trinh chay song song o o khac khong liet ke / doan duoc.
     */
    public static Path newWorkDir(String prefix) throws IOException {
        Path root = workRoot;
        return root == null ? Files.createTempDirectory(prefix) : Files.createTempDirectory(root, prefix);
    }
    private static volatile String status = "Tắt (chưa khởi tạo)";
    /** Moi buoc chay trong mot network namespace trong (khong ket noi ra ngoai duoc). */
    private static volatile boolean networkIsolated;
    private static volatile String networkStatus = "";
    /** Tham so JVM bat Security Manager; rong neu JDK khong ho tro (tu JDK 24) hoac khong ghi duoc file chinh sach. */
    private static volatile List<String> javaSecurityArgs = Collections.emptyList();
    /** Co bat che do phan quyen cua Node: "--permission" (Node 22.13+), "--experimental-permission" (Node 20-22) hoac "". */
    private static volatile String nodePermissionFlag = "";
    private static volatile boolean nodeDisableWarning;

    private Sandbox() { }

    /** Goi mot lan luc khoi dong: doc cau hinh va THU chay mot lenh bang tai khoan sandbox. */
    public static synchronized void init() {
        users = Collections.emptyList();
        networkIsolated = false;
        networkStatus = "";
        javaSecurityArgs = Collections.emptyList();
        nodePermissionFlag = "";
        String many = DatabaseConfig.setting(USERS_ENV);
        String configured = many != null && !many.trim().isEmpty() ? many : DatabaseConfig.setting(USER_ENV);
        if (configured == null || configured.trim().isEmpty()) {
            status = "Tắt: chưa đặt " + USER_ENV + " (bài nộp chạy bằng chính tài khoản của máy chủ)";
            return;
        }
        String[] names = configured.trim().split("\\s*,\\s*");
        String name = names[0];
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
        String[] limits = exec(10, "sudo", "-n", "-u", name, "--", "prlimit", "--nproc=4", "--data=67108864", "--", "true");
        if (limits == null || !"0".equals(limits[0])) {
            status = "Tắt: không chạy được prlimit bằng tài khoản " + name
                    + (limits == null ? "" : " (" + firstLine(limits[2]) + ")");
            return;
        }
        // Thu tao mot mang trong: trong do chi duoc co giao dien "lo" (loopback).
        String[] net = exec(10, "sudo", "-n", "-u", name, "--", "unshare", "--user", "--net", "--", "cat", "/proc/net/dev");
        if (net != null && "0".equals(net[0]) && onlyLoopback(net[1])) {
            networkIsolated = true;
            networkStatus = "chặn mạng: có";
        } else {
            networkStatus = "chặn mạng: không (nền tảng không cho tạo network namespace"
                    + (net == null ? "" : ": " + firstLine(net[2])) + ")";
        }
        javaSecurityArgs = prepareJavaSecurity();
        detectNodePermission();
        // Cac tai khoan con lai (o cham thu 2, 3...): chi nhan neu chay duoc sudo va prlimit nhu tai khoan dau.
        List<String> ok = new ArrayList<>();
        ok.add(name);
        for (int i = 1; i < names.length; i++) {
            String extra = names[i];
            if (!extra.matches("[a-z_][a-z0-9_-]{0,30}") || ok.contains(extra)) continue;
            String[] id = exec(10, "sudo", "-n", "-u", extra, "--", "id", "-u");
            if (id == null || !"0".equals(id[0]) || (mine != null && mine[1].trim().equals(id[1].trim()))) continue;
            String[] lim = exec(10, "sudo", "-n", "-u", extra, "--", "prlimit", "--nproc=4", "--data=67108864", "--", "true");
            if (lim != null && "0".equals(lim[0])) ok.add(extra);
        }
        workRoot = prepareWorkRoot();
        users = Collections.unmodifiableList(ok);
        status = "Bật: bài nộp chạy bằng " + ok.size() + " tài khoản hệ thống riêng " + ok + "; " + networkStatus
                + "; Java: " + (javaSecurityArgs.isEmpty() ? "không có Security Manager" : "Security Manager")
                + "; Node: " + (nodePermissionFlag.isEmpty() ? "không có chế độ phân quyền" : nodePermissionFlag);
    }

    private static boolean onlyLoopback(String procNetDev) {
        int interfaces = 0;
        for (String line : procNetDev.split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String iface = line.substring(0, colon).trim();
            if (iface.isEmpty() || iface.contains("|")) continue;
            interfaces++;
            if (!iface.equals("lo")) return false;
        }
        return interfaces > 0;
    }

    /**
     * Security Manager cho chuong trinh Java (JDK 17 cua image; JDK 24 tro di da bo han nen bo qua). Chinh
     * sach chi cho doc thuoc tinh he thong (nhieu loi giai goi System.getProperty("ONLINE_JUDGE")), doi
     * System.in / out va doc bien moi truong; moi thu khac - goi chuong trinh khac, mang, doc file, Unsafe,
     * reflection vuot quyen - deu bi chan. File chinh sach thuoc tai khoan may chu, nam trong /tmp (co bit
     * sticky) nen tai khoan sandbox doc duoc nhung khong sua / xoa duoc.
     */
    private static List<String> prepareJavaSecurity() {
        String[] version = exec(15, "java", "-version");
        if (version == null) return Collections.emptyList();
        Matcher m = Pattern.compile("version \"(?:1\\.)?(\\d+)").matcher(version[2] + version[1]);
        if (!m.find()) return Collections.emptyList();
        int major = Integer.parseInt(m.group(1));
        if (major >= 24) return Collections.emptyList();
        try {
            Path policy = Files.createTempFile("codehub-java-", ".policy");
            Files.write(policy, ("grant {\n"
                    + "  permission java.util.PropertyPermission \"*\", \"read\";\n"
                    + "  permission java.lang.RuntimePermission \"setIO\";\n"
                    + "  permission java.lang.RuntimePermission \"getenv.*\";\n"
                    + "};\n").getBytes(StandardCharsets.UTF_8));
            Files.setPosixFilePermissions(policy, PosixFilePermissions.fromString("rw-r--r--"));
            // "==": dung DUY NHAT file nay, bo chinh sach mac dinh cua JDK.
            return Arrays.asList("-Djava.security.manager=default", "-Djava.security.policy==" + policy);
        } catch (IOException | UnsupportedOperationException e) {
            return Collections.emptyList();
        }
    }

    private static void detectNodePermission() {
        String[] help = exec(15, "node", "--help");
        if (help == null || !"0".equals(help[0])) return;
        String text = help[1];
        if (Pattern.compile("(?m)--permission\\b").matcher(text).find()) nodePermissionFlag = "--permission";
        else if (text.contains("--experimental-permission")) nodePermissionFlag = "--experimental-permission";
        nodeDisableWarning = text.contains("--disable-warning");
    }

    public static boolean isActive() { return !users.isEmpty(); }

    /** Thu muc cha cho thu muc lam viec: quyen 711 - tai khoan sandbox di qua duoc nhung khong liet ke duoc. */
    private static Path prepareWorkRoot() {
        try {
            Path root = Files.createTempDirectory("codehub-judge-");
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx--x--x"));
            return root;
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    /** Mot dong mo ta trang thai, de in luc khoi dong va hien o trang quan tri. */
    public static String describe() { return status; }

    /** Tham so them cho lenh "java" chay bai nop (Security Manager); rong khi sandbox tat. */
    public static List<String> javaRunArgs() {
        return !isActive() ? Collections.<String>emptyList() : javaSecurityArgs;
    }

    /**
     * Tham so them cho lenh "node" chay bai nop (che do phan quyen); rong khi sandbox tat. Chi cho doc thu
     * muc bai (ma nguon, file input) va /dev/stdin - nhieu loi giai doc input bang readFileSync("/dev/stdin").
     */
    public static List<String> nodeRunArgs(Path workDir) {
        if (!isActive() || nodePermissionFlag.isEmpty()) return Collections.emptyList();
        List<String> args = new ArrayList<>();
        args.add(nodePermissionFlag);
        args.add("--allow-fs-read=" + workDir);
        args.add("--allow-fs-read=/dev/stdin");
        if (nodeDisableWarning) args.add("--disable-warning=ExperimentalWarning");
        return args;
    }

    /** Boc lenh de chay bang tai khoan sandbox kem gioi han tai nguyen. */
    static List<String> wrap(List<String> command, Limits limits) {
        String name = currentUser();
        if (name == null) return command;
        List<String> out = new ArrayList<>(Arrays.asList("sudo", "-n", "-H", "-u", name, "--"));
        if (networkIsolated) out.addAll(Arrays.asList("unshare", "--user", "--net", "--"));
        out.addAll(Arrays.asList("prlimit",
                "--nproc=" + limits.processes,
                "--fsize=" + FILE_LIMIT_BYTES,
                "--core=0",
                "--nofile=" + limits.openFiles,
                "--cpu=" + limits.cpuSeconds));
        if (limits.addressSpaceMb > 0) out.add("--as=" + (long) limits.addressSpaceMb * 1024 * 1024);
        if (limits.dataMb > 0) out.add("--data=" + (long) limits.dataMb * 1024 * 1024);
        out.add("--");
        out.addAll(command);
        return out;
    }

    /** Thu muc lam viec do may chu tao (chi chu so huu vao duoc): mo cho tai khoan sandbox doc / ghi. */
    public static void prepareWorkDir(Path dir) {
        if (!isActive() || dir == null) return;
        // Truoc khi bat dau mot lan cham moi: bao dam khong con tien trinh nao cua lan truoc de
        // can thiep vao thu muc lam viec cua nguoi nay.
        killAll();
        openDir(dir);
    }

    /** Mo mot thu muc con (vd. thu muc cua tung chuong trinh trong stress test) cho tai khoan sandbox. */
    public static void openDir(Path dir) {
        if (!isActive() || dir == null) return;
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (IOException | UnsupportedOperationException e) {
            // Khong mo duoc quyen: buoc bien dich se bao loi ro rang, khong can xu ly o day.
        }
    }

    /** Diet moi tien trinh cua tai khoan sandbox - dung khi bai chay qua gio, vi may chu khong tu kill duoc tien trinh cua tai khoan khac. */
    static void killAll() {
        String name = currentUser();
        if (name == null) return;
        exec(10, "sudo", "-n", "-u", name, "--", "sh", "-c", "kill -9 -1 2>/dev/null; exit 0");
    }

    /**
     * Don dep sau mot lan cham: diet tien trinh con sot lai, xoa moi file tai khoan sandbox da tao
     * (trong thu muc lam viec va cac thu muc tam). Phan con lai cua thu muc lam viec do may chu xoa.
     */
    public static void cleanup(Path workDir) {
        String name = currentUser();
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
