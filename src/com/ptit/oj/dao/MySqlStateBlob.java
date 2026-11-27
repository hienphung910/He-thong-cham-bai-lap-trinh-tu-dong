package com.ptit.oj.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Ban sao du lieu lop hoc, ky thi, chat... (StateStore) trong MySQL, ben canh file tren o dia (Volume).
 *
 * File tren Volume ghi nhanh (vai giay mot lan) nhung chi nam o mot cho: Volume bi xoa / hong la mat. Ban
 * trong MySQL ghi thua hon (StateStore quyet dinh) va nam cung cho voi tai khoan, bai nop - duoc nha cung
 * cap CSDL sao luu. Khoi dong ma khong co file thi nap lai tu day.
 */
public class MySqlStateBlob {

    private static final String NAME = "platform";
    private final MySqlDatabaseManager database;

    public MySqlStateBlob(MySqlDatabaseManager database) {
        this.database = database;
        try (Connection c = database.open(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS app_state ("
                    + " name VARCHAR(64) NOT NULL PRIMARY KEY,"
                    + " data LONGBLOB NOT NULL,"
                    + " saved_at DATETIME(6) NOT NULL"
                    + ") ENGINE=InnoDB");
        } catch (SQLException e) {
            throw new DataAccessException("Không tạo được bảng app_state", e);
        }
    }

    public void save(byte[] data) {
        try (Connection c = database.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO app_state (name, data, saved_at) VALUES (?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE data = VALUES(data), saved_at = VALUES(saved_at)")) {
            ps.setString(1, NAME);
            ps.setBytes(2, data);
            ps.setObject(3, LocalDateTime.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Không lưu được bản sao dữ liệu lớp học vào MySQL", e);
        }
    }

    public Optional<byte[]> load() {
        try (Connection c = database.open();
             PreparedStatement ps = c.prepareStatement("SELECT data FROM app_state WHERE name = ?")) {
            ps.setString(1, NAME);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không đọc được bản sao dữ liệu lớp học từ MySQL", e);
        }
    }
}
