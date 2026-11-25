package com.ptit.oj.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Noi luu file minh chung giang vien gui kem khi tu dang ky (anh the, quyet dinh... ) de quan tri
 * vien xem truoc khi duyet. Moi tai khoan toi da mot file, nam trong data/evidence/<ma tai khoan>.<duoi>.
 *
 * Endpoint dang ky la cong khai nen file gui len KHONG dang tin:
 *  - chi nhan PNG, JPG, WEBP, PDF va nhan dang theo NOI DUNG file, khong tin ten hay kieu trinh duyet gui;
 *  - gioi han 1 MB (giao dien tu thu nho anh truoc khi gui);
 *  - ten file tren dia do may chu dat tu ma tai khoan, khong dung ten nguoi dung gui;
 *  - chi quan tri vien doc lai duoc, qua API co kiem tra quyen.
 * File bi xoa ngay khi tai khoan duoc duyet hoac bi tu choi.
 */
public class EvidenceStore {

    public static final int MAX_BYTES = 1024 * 1024;

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private static final String[][] TYPES = {
            {"png", "image/png"}, {"jpg", "image/jpeg"}, {"webp", "image/webp"}, {"pdf", "application/pdf"},
    };

    /** Mot file minh chung da kiem tra: loai file va noi dung. */
    public static final class Evidence {
        private final String extension;
        private final String contentType;
        private final byte[] bytes;

        Evidence(String extension, String contentType, byte[] bytes) {
            this.extension = extension;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        public String getExtension() { return extension; }
        public String getContentType() { return contentType; }
        public byte[] getBytes() { return bytes; }
        public String toBase64() { return Base64.getEncoder().encodeToString(bytes); }
    }

    private final Path dir;

    public EvidenceStore(Path dir) {
        this.dir = dir;
    }

    /**
     * Giai ma chuoi base64 trinh duyet gui len va kiem tra file. Nem ApiException 400 neu khong phai
     * base64, qua lon, hoac khong phai PNG / JPG / WEBP / PDF.
     */
    public static Evidence decode(String base64) {
        String raw = base64 == null ? "" : base64.trim();
        // Chap nhan ca dang "data:image/png;base64,...." cho de dung.
        if (raw.startsWith("data:")) {
            int comma = raw.indexOf(',');
            raw = comma < 0 ? "" : raw.substring(comma + 1);
        }
        if (raw.isEmpty()) throw ApiException.badRequest("File minh chứng rỗng");
        // 4 ky tu base64 = 3 byte: chan file qua lon truoc khi giai ma.
        if (raw.length() > (MAX_BYTES / 3 + 2) * 4L) {
            throw ApiException.badRequest("File minh chứng quá lớn (tối đa 1 MB)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("File minh chứng không đọc được");
        }
        if (bytes.length > MAX_BYTES) throw ApiException.badRequest("File minh chứng quá lớn (tối đa 1 MB)");
        String extension = sniff(bytes);
        if (extension == null) {
            throw ApiException.badRequest("Minh chứng phải là ảnh PNG, JPG, WEBP hoặc file PDF");
        }
        return new Evidence(extension, contentTypeOf(extension), bytes);
    }

    /** Nhan dang loai file theo cac byte dau; null neu khong thuoc loai nao duoc nhan. */
    private static String sniff(byte[] b) {
        if (startsWith(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "png";
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(b, '%', 'P', 'D', 'F', '-')) return "pdf";
        if (b.length >= 12 && startsWith(b, 'R', 'I', 'F', 'F')
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        if (b.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((b[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    private static String contentTypeOf(String extension) {
        for (String[] type : TYPES) {
            if (type[0].equals(extension)) return type[1];
        }
        return "application/octet-stream";
    }

    /** Luu (hoac thay) file minh chung cua mot tai khoan. */
    public synchronized void save(String userId, Evidence evidence) throws IOException {
        requireSafeId(userId);
        Files.createDirectories(dir);
        delete(userId);
        Files.write(dir.resolve(userId + "." + evidence.getExtension()), evidence.getBytes());
    }

    public synchronized boolean exists(String userId) {
        return pathOf(userId) != null;
    }

    public synchronized Optional<Evidence> find(String userId) {
        Path path = pathOf(userId);
        if (path == null) return Optional.empty();
        String name = path.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.') + 1);
        try {
            return Optional.of(new Evidence(extension, contentTypeOf(extension), Files.readAllBytes(path)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Xoa file minh chung cua tai khoan (neu co). */
    public synchronized void delete(String userId) {
        if (userId == null || !SAFE_ID.matcher(userId).matches()) return;
        for (String[] type : TYPES) {
            try {
                Files.deleteIfExists(dir.resolve(userId + "." + type[0]));
            } catch (IOException e) {
                // Khong xoa duoc thi bo qua: file chi con la rac, khong ai doc duoc khi tai khoan da het cho duyet.
            }
        }
    }

    private Path pathOf(String userId) {
        if (userId == null || !SAFE_ID.matcher(userId).matches()) return null;
        for (String[] type : TYPES) {
            Path path = dir.resolve(userId + "." + type[0]);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    private static void requireSafeId(String userId) {
        if (userId == null || !SAFE_ID.matcher(userId).matches()) {
            throw new IllegalArgumentException("Mã tài khoản không hợp lệ để lưu minh chứng");
        }
    }
}
