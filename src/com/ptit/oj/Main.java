package com.ptit.oj;

import com.ptit.oj.controller.JudgeService;
import com.ptit.oj.dao.DataAccessException;
import com.ptit.oj.dao.DatabaseConfig;
import com.ptit.oj.dao.MySqlDatabaseManager;
import com.ptit.oj.dao.ProblemLoadException;
import com.ptit.oj.view.account.LoginView;

import javax.swing.UIManager;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Diem vao chuong trinh: nap du lieu roi mo cua so dang nhap.
 *
 * Cach chay (tu thu muc goc cua project, noi co folder data):
 *   java -cp "out;lib/*" com.ptit.oj.Main            -> tai khoan va bai nop luu vao MySQL
 *   java -cp out com.ptit.oj.Main --no-db            -> khong can MySQL, du lieu nam trong RAM
 *
 * Cau hinh MySQL lay tu BIEN MOI TRUONG hoac file .env (xem .env.example):
 *   OJ_DB_URL / OJ_DB_USER / OJ_DB_PASSWORD
 * Mat khau KHONG BAO GIO nhan qua dong lenh de khong bi luu vao lich su shell.
 */
public class Main {

    /** Mat khau cua cac tai khoan mau, chi dung lan dau khi chua co tai khoan nao. */
    private static final String SEED_PASSWORD_ENV = "OJ_SEED_PASSWORD";
    private static final String DEFAULT_SEED_PASSWORD = "ptit@2026";

    public static void main(String[] args) {
        forceUtf8Output();

        boolean noDatabase = false;
        for (String arg : args) {
            if (arg.equals("--no-db")) {
                noDatabase = true;
            } else {
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
                System.out.println("Chế độ --no-db: tài khoản và bài nộp chỉ nằm trong RAM, tắt chương trình là mất.");
                service = JudgeService.inMemory(dataDir);
            } else {
                MySqlDatabaseManager database = connectToMySql();
                if (database == null) return;
                service = JudgeService.mysql(dataDir, database);
            }
            service.loadData();
            prepareAccounts(service);
            System.out.printf("Đã nạp %d bài tập, %d tài khoản.%n",
                    service.getProblems().size(), service.getUsers().size());
            openLoginWindow(service);
        } catch (ProblemLoadException e) {
            System.out.println("Lỗi nạp đề bài: " + e.getMessage());
        } catch (DataAccessException e) {
            System.out.println("Lỗi cơ sở dữ liệu: " + e.getMessage());
        }
    }

    /** Mo cua so dang nhap, dung kieu giao dien cua he dieu hanh. */
    private static void openLoginWindow(JudgeService service) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // khong doi duoc giao dien he dieu hanh thi dung giao dien mac dinh cua Swing
        }
        LoginView loginView = new LoginView(service);
        loginView.setVisible(true);
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

    /** Lan chay dau tien (chua co tai khoan nao): tao cac tai khoan mau de dang nhap thu. */
    private static void prepareAccounts(JudgeService service) {
        String seedPassword = System.getenv(SEED_PASSWORD_ENV);
        if (seedPassword == null || seedPassword.trim().isEmpty()) {
            seedPassword = DEFAULT_SEED_PASSWORD;
        }
        if (service.getAuth().seedSampleUsersIfEmpty(seedPassword)) {
            System.out.println("Đã tạo 5 tài khoản mẫu: admin, gvthanh, hiennm, lananh, tuanpv.");
            System.out.println("  Mật khẩu mẫu: " + seedPassword
                    + "   (đặt biến môi trường " + SEED_PASSWORD_ENV + " để dùng mật khẩu khác)");
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
