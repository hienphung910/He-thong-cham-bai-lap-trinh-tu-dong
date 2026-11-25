package com.ptit.oj;

import com.ptit.oj.controller.ApiServer;
import com.ptit.oj.controller.AuthService;
import com.ptit.oj.controller.DemoData;
import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.dao.DataAccessException;
import com.ptit.oj.dao.DatabaseConfig;
import com.ptit.oj.dao.MySqlDatabaseManager;
import com.ptit.oj.dao.ProblemLoadException;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Diem vao chuong trinh: nap du lieu roi mo may chu web.
 *
 * Cach chay (tu thu muc goc cua project, noi co folder data):
 *   java -cp "out;lib/*" com.ptit.oj.Main --serve           -> http://localhost:8080, luu vao MySQL
 *   java -cp "out;lib/*" com.ptit.oj.Main --serve=9000      -> chon cong khac
 *   java -cp out com.ptit.oj.Main --serve --no-db           -> khong can MySQL, du lieu nam trong RAM
 *
 * Cau hinh MySQL lay tu BIEN MOI TRUONG hoac file .env (xem .env.example):
 *   OJ_DB_URL / OJ_DB_USER / OJ_DB_PASSWORD
 * Mat khau KHONG BAO GIO nhan qua dong lenh de khong bi luu vao lich su shell.
 */
public class Main {

    private static final int DEFAULT_PORT = 8080;

    /** Mat khau cua cac tai khoan mau, chi dung lan dau khi chua co tai khoan nao. */
    private static final String SEED_PASSWORD_ENV = "OJ_SEED_PASSWORD";

    public static void main(String[] args) {
        forceUtf8Output();

        boolean noDatabase = false;
        int port = DEFAULT_PORT;
        for (String arg : args) {
            if (arg.equals("--no-db")) {
                noDatabase = true;
            } else if (arg.startsWith("--serve=")) {
                port = parsePort(arg.substring("--serve=".length()));
            } else if (!arg.equals("--serve")) {
                System.out.println("Tham số không hiểu: " + arg);
            }
        }

        Path dataDir = Paths.get("data");
        if (!Files.isDirectory(dataDir)) {
            System.out.println("Không tìm thấy thư mục dữ liệu: " + dataDir.toAbsolutePath());
            System.out.println("Hãy chạy chương trình từ thư mục gốc của project (nơi có folder data).");
            return;
        }

        try {
            JudgeService service;
            if (noDatabase) {
                printNoDatabaseBanner();
                service = JudgeService.inMemory(dataDir);
            } else {
                MySqlDatabaseManager database = connectToMySql();
                if (database == null) return;
                service = JudgeService.mysql(dataDir, database);
            }
            runServer(service, Paths.get("frontend", "dist"), port, noDatabase);
        } catch (ProblemLoadException e) {
            System.out.println("Lỗi nạp đề bài: " + e.getMessage());
        } catch (DataAccessException e) {
            System.out.println("Lỗi cơ sở dữ liệu: " + e.getMessage());
        }
    }

    /**
     * Doc cau hinh tu bien moi truong, mo ket noi va kiem tra bang SELECT 1.
     * Tra ve null (kem huong dan) neu thieu cau hinh hoac khong ket noi duoc.
     */
    private static MySqlDatabaseManager connectToMySql() {
        DatabaseConfig config;
        try {
            config = DatabaseConfig.fromEnvironment();
        } catch (DatabaseConfig.MissingConfigException e) {
            System.out.println(e.getMessage());
            return null;
        }

        MySqlDatabaseManager database = new MySqlDatabaseManager(
                config.getUrl(), config.getUser(), config.getPassword());
        try {
            database.testConnection();
        } catch (DataAccessException e) {
            System.out.println("Không kết nối được MySQL.");
            System.out.println("  " + e.getMessage());
            System.out.println("  Xem hướng dẫn tạo cơ sở dữ liệu ở database/mysql/01_create_database.sql");
            return null;
        }
        System.out.println("MySQL: " + database.getSafeUrl() + " (user " + database.getUser() + ")");
        return database;
    }

    /** Nap du lieu roi mo may chu HTTP, chay den khi bi dung (Ctrl+C). */
    private static void runServer(JudgeService service, Path webRoot, int port, boolean noDatabase) {
        service.loadData();
        prepareAccounts(service, noDatabase);
        int migrated = service.migrateProblemOwners();
        if (migrated > 0) System.out.println("Đã chuyển chủ của " + migrated + " bài kho riêng từ tên đăng nhập sang mã tài khoản.");
        System.out.printf("Đã nạp %d bài tập, %d tài khoản.%n",
                service.getProblems().size(), service.getUsers().size());

        // Phai goi TRUOC khi tao Platform: gia tri mac dinh cua "cho tu dang ky" phu thuoc vao viec sandbox co bat khong.
        com.ptit.oj.controller.judge.Sandbox.init();
        System.out.println("Cô lập bài nộp: " + com.ptit.oj.controller.judge.Sandbox.describe());

        Platform platform = new Platform(service);
        // Du lieu mau (lop, ky thi, bai giao mau) chi danh cho che do chay thu --no-db. Khi chay voi
        // CSDL that thi KHONG tao: neu khong, moi lan khoi dong lai tu dua hoc sinh that vao mot lop mau.
        if (noDatabase) new DemoData(platform).seed(true);
        if (!platform.getSystem().isRegisterAllowed()) {
            System.out.println("Tự đăng ký: ĐANG TẮT (đặt " + com.ptit.oj.controller.SystemService.ALLOW_REGISTER_ENV
                    + "=true hoặc bật ở Quản trị → Hệ thống để mở).");
        } else if (com.ptit.oj.controller.SystemService.exposedToNetwork()
                && !com.ptit.oj.controller.judge.Sandbox.isActive()) {
            System.out.println("CẢNH BÁO: đang mở tự đăng ký trên mạng trong khi bài nộp KHÔNG được cô lập - "
                    + "ai đăng ký được cũng chạy được mã tuỳ ý bằng quyền của máy chủ.");
        }

        ApiServer server = new ApiServer(platform, port, webRoot);
        try {
            server.start();
        } catch (IOException e) {
            System.out.println("Không mở được cổng " + port + ": " + e.getMessage());
            System.out.println("Cổng có thể đang bị chiếm - thử --serve=8081");
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nĐang dừng máy chủ...");
            server.stop();
        }));
        try {
            Thread.currentThread().join();   // giu tien trinh song cho HttpServer phuc vu
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop();
        }
    }

    /**
     * Chuan bi tai khoan cho lan chay dau tien:
     *   - tao tai khoan mau (neu chua co tai khoan nao),
     *   - tao tai khoan ADMIN va in mat khau DUNG MOT LAN neu phai sinh ngau nhien.
     * Cac lan chay sau khong dat lai mat khau cua ai ca.
     */
    /** Ghi mot bi mat ra file chi chu so huu doc duoc (tren he thong ho tro quyen POSIX). */
    private static boolean writeSecret(Path file, String secret) {
        try {
            Files.write(file, (secret + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try {
                Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: khong co quyen POSIX, file nam trong thu muc data cua chinh nguoi chay
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void prepareAccounts(JudgeService service, boolean noDatabase) {
        // KHONG co mat khau mac dinh: mat khau ai cung biet tren mot tai khoan giang vien la mot cua hau.
        //  - Dat OJ_SEED_PASSWORD: tao 4 tai khoan mau voi mat khau do (khong in ra).
        //  - Khong dat, che do --no-db (chay thu tren may ca nhan, du lieu trong RAM): sinh mat khau ngau nhien va in ra.
        //  - Khong dat, chay voi CSDL that: KHONG tao tai khoan mau.
        String seedPassword = DatabaseConfig.setting(SEED_PASSWORD_ENV);
        boolean configured = seedPassword != null && !seedPassword.trim().isEmpty();
        if (!configured && noDatabase) seedPassword = service.getAuth().newRandomPassword();
        if ((configured || noDatabase) && service.getAuth().seedSampleUsersIfEmpty(seedPassword)) {
            System.out.println("Đã tạo 4 tài khoản mẫu (hiennm, lananh, tuanpv, gvthanh).");
            System.out.println(configured
                    ? "  Mật khẩu mẫu: lấy từ biến môi trường " + SEED_PASSWORD_ENV
                    : "  Mật khẩu mẫu (sinh ngẫu nhiên cho lần chạy thử này): " + seedPassword);
        }

        AuthService.BootstrapResult admin = service.getAuth().bootstrapAdmin();
        if (!admin.isCreated()) return;

        System.out.println();
        System.out.println("==================================================");
        System.out.println(" ĐÃ TẠO TÀI KHOẢN QUẢN TRỊ");
        System.out.println("   Tên đăng nhập: " + admin.getAdmin().getUsername());
        if (admin.getGeneratedPassword() != null && noDatabase) {
            System.out.println("   Mật khẩu     : " + admin.getGeneratedPassword());
            System.out.println("   (chỉ hiện DUY NHẤT lần này - hãy chép lại ngay)");
        } else if (admin.getGeneratedPassword() != null) {
            // Chay voi CSDL that: KHONG in mat khau ra man hinh, vi log cua nen tang hosting thuong
            // duoc luu lau va nhieu nguoi xem duoc. Ghi ra mot file chi tai khoan may chu doc duoc.
            Path file = service.getDataDir().resolve("admin-password.txt");
            if (writeSecret(file, admin.getGeneratedPassword())) {
                System.out.println("   Mật khẩu     : đã ghi vào " + file.toAbsolutePath());
                System.out.println("   (đọc xong hãy XOÁ file này; lần sau nên đặt biến môi trường "
                        + AuthService.ADMIN_PASSWORD_ENV + " trước lần chạy đầu)");
            } else {
                System.out.println("   Mật khẩu     : " + admin.getGeneratedPassword());
                System.out.println("   (không ghi được ra file nên in ra đây DUY NHẤT lần này - hãy chép lại ngay)");
            }
        } else {
            System.out.println("   Mật khẩu     : lấy từ biến môi trường "
                    + AuthService.ADMIN_PASSWORD_ENV);
        }
        System.out.println("==================================================");
        System.out.println();
    }

    /** Noi ro ngay tu dau rang du lieu se mat khi tat, de khong ai hieu nham. */
    private static void printNoDatabaseBanner() {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(" CHẾ ĐỘ KHÔNG CƠ SỞ DỮ LIỆU (--no-db)");
        System.out.println("   Đầy đủ tính năng, nhưng tài khoản và lịch sử nộp");
        System.out.println("   bài chỉ nằm trong RAM: TẮT CHƯƠNG TRÌNH LÀ MẤT.");
        System.out.println("   Đề bài vẫn đọc/ghi ở data/problems/ nên còn nguyên.");
        System.out.println("   Muốn mật khẩu admin cố định thì đặt biến môi trường");
        System.out.println("   " + AuthService.ADMIN_PASSWORD_ENV + " trước khi chạy.");
        System.out.println("==================================================");
        System.out.println();
    }

    private static int parsePort(String text) {
        try {
            int p = Integer.parseInt(text.trim());
            if (p < 1 || p > 65535) throw new NumberFormatException();
            return p;
        } catch (NumberFormatException e) {
            System.out.println("Cổng không hợp lệ (" + text + "), dùng mặc định " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /** Bat buoc stdout dung UTF-8 de khong bi loi font khi in tieng Viet tren Windows. */
    private static void forceUtf8Output() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
            // moi JVM deu ho tro UTF-8, nhung neu that bai thi cu dung stdout mac dinh
        }
    }
}
