package com.ptit.oj.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Quan ly ket noi toi MySQL 8.
 *
 * Nguyen tac:
 *  - KHONG giu mot Connection dung chung toan cuc. Moi thao tac lay mot ket noi
 *    roi dong ngay bang try-with-resources, nen HttpServer chay nhieu thread song song
 *    khong bao gio dam nhau tren cung mot ket noi. "Dong" la tra ket noi ve hang cho
 *    (connection pool) de lan sau dung lai, khong phai mo lai tu dau.
 *  - KHONG BAO GIO ghi mat khau ra log hay ra thong bao loi (xem maskUrl()).
 *  - Moi SQLException duoc boc thanh DataAccessException voi thong bao tieng Viet
 *    de nguoi dung biet phai sua gi, thay vi doc stack trace cua driver.
 */
public class MySqlDatabaseManager {

    private final String url;
    private final String user;
    private final String password;

    public MySqlDatabaseManager(String url, String user, String password) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu URL kết nối MySQL");
        }
        if (user == null || user.trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu tên đăng nhập MySQL");
        }
        this.url = url.trim();
        this.user = user.trim();
        this.password = password == null ? "" : password;
        loadDriver();
    }

    private void loadDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new DataAccessException(
                    "Không tìm thấy trình điều khiển MySQL (mysql-connector-j). "
                  + "Hãy tải mysql-connector-j vào thư mục lib/ theo hướng dẫn trong README, "
                  + "hoặc chạy: mvn -q dependency:copy-dependencies -DoutputDirectory=lib", e);
        }
    }

    // ------------------------------------------------------------ connection pool

    /**
     * Ket noi dang ranh, san sang dung lai. Mo ket noi moi toi MySQL tu xa (Aiven, qua TLS) ton vai chuc den
     * vai tram mili giay; giu lai de lan sau dung ngay. Moi ket noi van chi do MOT luong dung tai mot thoi
     * diem (lay ra khoi hang, dong thi tra lai), nen nguyen tac "khong dung chung ket noi" o tren van dung.
     */
    private final java.util.concurrent.ConcurrentLinkedDeque<Idle> idle = new java.util.concurrent.ConcurrentLinkedDeque<>();
    /** Toi da so ket noi ranh giu lai; luc cao diem van mo them duoc, dong thi dong han phan du. */
    private static final int MAX_IDLE = 10;
    /** Ket noi ranh lau hon muc nay thi kiem tra con song truoc khi dung (may chu MySQL co the da cat). */
    private static final long VALIDATE_AFTER_MS = 30_000;
    /** Ket noi ranh qua lau thi dong han (MySQL tu cat ket noi im lang sau wait_timeout). */
    private static final long MAX_IDLE_MS = 5 * 60_000;

    private static final class Idle {
        final Connection connection;
        final long since;

        Idle(Connection connection, long since) {
            this.connection = connection;
            this.since = since;
        }
    }

    /**
     * Lay mot ket noi (dung lai ket noi ranh neu co). Nguoi goi PHAI dong bang try-with-resources: close() tra
     * ket noi ve hang cho chu khong ngat. Ket noi mac dinh o che do autocommit; noi nao can transaction thi tu
     * tat - luc tra ve, giao dich do dang bi rollback va autocommit duoc bat lai.
     */
    public Connection open() {
        long now = System.currentTimeMillis();
        Idle candidate;
        while ((candidate = idle.pollFirst()) != null) {
            Connection c = candidate.connection;
            long idleFor = now - candidate.since;
            if (idleFor > MAX_IDLE_MS) { closeQuietly(c); continue; }
            try {
                if (idleFor > VALIDATE_AFTER_MS && !c.isValid(2)) { closeQuietly(c); continue; }
                return pooled(c);
            } catch (SQLException e) {
                closeQuietly(c);
            }
        }
        try {
            return pooled(DriverManager.getConnection(url, user, password));
        } catch (SQLException e) {
            throw new DataAccessException(friendlyMessage(e), e);
        }
    }

    /** Boc ket noi that: close() tra ve hang cho; goi bat ky ham nao sau khi da close() deu bao loi. */
    private Connection pooled(Connection real) {
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("toString")) return "PooledConnection@" + Integer.toHexString(System.identityHashCode(proxy));
                    if (name.equals("close")) {
                        if (closed.compareAndSet(false, true)) release(real);
                        return null;
                    }
                    if (name.equals("isClosed")) return closed.get() || real.isClosed();
                    if (closed.get()) throw new SQLException("Kết nối đã được trả lại");
                    try {
                        return method.invoke(real, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private void release(Connection real) {
        try {
            if (real.isClosed()) return;
            if (!real.getAutoCommit()) {
                real.rollback();        // giao dich chua commit thi bo, khong de lot sang nguoi dung sau
                real.setAutoCommit(true);
            }
            if (idle.size() < MAX_IDLE) {
                idle.offerFirst(new Idle(real, System.currentTimeMillis()));
                return;
            }
        } catch (SQLException e) {
            // ket noi hong: dong han ben duoi
        }
        closeQuietly(real);
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
            // bo qua
        }
    }

    /** Dong moi ket noi ranh - goi khi tat may chu. */
    public void shutdown() {
        Idle i;
        while ((i = idle.pollFirst()) != null) closeQuietly(i.connection);
    }

    /** Kiem tra ket noi bang SELECT 1 - goi luc khoi dong de bao loi som va rõ ràng. */
    public void testConnection() {
        try (Connection c = open();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            if (!rs.next() || rs.getInt(1) != 1) {
                throw new DataAccessException("MySQL trả về kết quả không mong đợi cho SELECT 1");
            }
        } catch (SQLException e) {
            throw new DataAccessException(friendlyMessage(e), e);
        }
    }

    /** URL da che phan nhay cam - dung khi in ra man hinh hoac ghi log. */
    public String getSafeUrl() {
        return maskUrl(url);
    }

    public String getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "MySqlDatabaseManager{user=" + user + ", url=" + getSafeUrl() + "}";
    }

    // ------------------------------------------------------------- tien ich

    /**
     * Xoa moi tham so co the chua thong tin nhay cam (password, user) khoi URL
     * truoc khi in ra. Chi giu lai phan host/port/ten CSDL.
     */
    public static String maskUrl(String jdbcUrl) {
        if (jdbcUrl == null) return "";
        int question = jdbcUrl.indexOf('?');
        String base = question < 0 ? jdbcUrl : jdbcUrl.substring(0, question);
        return question < 0 ? base : base + "?...";
    }

    /** Bien loi cua driver thanh cau tieng Viet noi ro phai sua gi. */
    private String friendlyMessage(SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        String raw = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        String where = " (" + getSafeUrl() + ", user=" + user + ")";

        if ("28000".equals(state) || raw.contains("access denied")) {
            return "MySQL từ chối đăng nhập" + where
                 + ". Kiểm tra lại " + DatabaseConfig.ENV_USER + " / " + DatabaseConfig.ENV_PASSWORD
                 + ", và tài khoản đã được GRANT quyền trên CSDL này chưa.";
        }
        if ("42000".equals(state) || raw.contains("unknown database")) {
            return "MySQL không có cơ sở dữ liệu này" + where
                 + ". Hãy chạy database/mysql/01_create_database.sql trước.";
        }
        if ("08S01".equals(state) || state.startsWith("08") || raw.contains("communications link failure")
                || raw.contains("connection refused")) {
            return "Không kết nối được tới máy chủ MySQL" + where
                 + ". Kiểm tra dịch vụ MySQL đã chạy chưa và đúng host/port trong "
                 + DatabaseConfig.ENV_URL + " chưa.";
        }
        return "Lỗi khi làm việc với MySQL" + where + ": " + e.getMessage();
    }
}
