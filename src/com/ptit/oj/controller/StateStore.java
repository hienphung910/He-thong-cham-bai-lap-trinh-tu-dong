package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.model.Assignment;
import com.ptit.oj.model.ChatMessage;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Meeting;
import com.ptit.oj.model.Notification;
import com.ptit.oj.model.Organization;
import com.ptit.oj.model.ProblemComment;
import com.ptit.oj.model.ProctorEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Luu du lieu cua cac module mo rong xuong o dia de KHOI DONG LAI KHONG MAT: to chuc, lop hoc (thanh
 * vien, loi moi, bai luyen tap), bai tap giao, lich hoc, ky thi (ca ai da vao thi / da nop), nhat ky
 * giam sat, thong bao, chat, binh luan va cau hinh. Cac du lieu nay van song trong RAM khi may chu
 * chay; lop nay chi chup lai va nap lai.
 *
 * Cach lam:
 *  - Moi request lam thay doi du lieu (khong phai GET) danh dau "co thay doi" (markDirty). Mot luong
 *    nen cu SAVE_EVERY_SECONDS giay kiem tra mot lan, co thay doi thi chup toan bo trang thai va ghi
 *    ra file. May chu dung (deploy lai, Ctrl+C) cung ghi mot lan cuoi. Mat toi da vai giay cuoi neu
 *    may chu bi tat dot ngot.
 *  - Ghi ra file tam roi doi ten (khong bao gio con file do dang), ban truoc do giu lai thanh .bak.
 *  - Dung co che tuan tu hoa cua Java. Luc doc chi cho phep cac lop du lieu cua he thong va lop
 *    chuan cua Java (ObjectInputFilter), khong nap lop la.
 *
 * Luu y khi sua cac lop trong model/: THEM truong moi thi ban luu cu van doc duoc (truong moi nhan
 * null / 0 / false - neu la tap hop thi phai tu khoi tao lai); DOI KIEU hoac doi ten truong thi ban
 * luu cu khong doc duoc nua, may chu se bao loi, giu file cu lai voi duoi .bad va bat dau tu trong.
 *
 * Ban sao trong MySQL (MySqlStateBlob, khi chay voi CSDL): ghi toi da moi DB_SAVE_EVERY_MS mot lan va
 * mot lan cuoi khi may chu dung. Khoi dong ma file tren o dia mat (Volume bi xoa / hong) thi nap tu do.
 *
 * Phien dang nhap KHONG duoc luu (token nam tren dia la rui ro): sau khi khoi dong lai moi nguoi
 * dang nhap lai.
 */
public final class StateStore {

    private static final int SAVE_EVERY_SECONDS = 3;
    private static final String ALLOWED_CLASSES =
            "com.ptit.oj.model.*;java.util.**;java.time.*;java.lang.*;"
          + "maxdepth=64;maxbytes=268435456;!*";

    private final Platform platform;
    private final Path file;
    private volatile boolean dirty;
    /** Ban sao trong MySQL; null = chi luu file (che do --no-db). */
    private com.ptit.oj.dao.MySqlStateBlob database;
    private static final long DB_SAVE_EVERY_MS = 30_000;
    private long lastDbSave;
    /** Co thay doi chua chep sang MySQL. */
    private boolean dbDirty;

    /** Bat ban sao trong MySQL. Goi truoc load(). */
    public void setDatabase(com.ptit.oj.dao.MySqlStateBlob database) {
        this.database = database;
    }
    private ScheduledExecutorService timer;

    public StateStore(Platform platform, Path file) {
        this.platform = platform;
        this.file = file;
    }

    /** Goi sau moi thay doi du lieu; viec ghi do luong nen lam, khong lam cham request. */
    public void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------------ nap

    /**
     * Nap trang thai da luu (neu co) vao cac service. Goi MOT lan luc khoi dong, truoc khi mo may chu.
     * @return true neu da nap duoc du lieu tu o dia
     */
    public synchronized boolean load() {
        Path backup = backupFile();
        for (Path candidate : new Path[] {file, backup}) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                apply(read(candidate));
                System.out.println("Đã nạp dữ liệu lớp học, kỳ thi... từ " + candidate.toAbsolutePath());
                return true;
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                Path bad = candidate.resolveSibling(candidate.getFileName() + ".bad-"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                System.err.println("Không đọc được " + candidate + " (" + e + "). Đã giữ lại thành " + bad.getFileName());
                try {
                    Files.move(candidate, bad, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // khong doi ten duoc thi lan ghi sau se ghi de; khong lam gi hon duoc
                }
            }
        }
        // Khong co file (Volume moi / bi xoa): lay ban sao trong MySQL neu co.
        if (database != null) {
            try {
                java.util.Optional<byte[]> saved = database.load();
                if (saved.isPresent()) {
                    apply(read(saved.get()));
                    System.out.println("Đã nạp dữ liệu lớp học, kỳ thi... từ bản sao trong MySQL");
                    dirty = true;       // ghi lai ra file ngay o lan luu dau
                    return true;
                }
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                System.err.println("Không đọc được bản sao dữ liệu lớp học trong MySQL: " + e);
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(Path source) throws IOException, ClassNotFoundException {
        return read(Files.readAllBytes(source));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(ALLOWED_CLASSES));
            return (Map<String, Object>) in.readObject();
        }
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> state) {
        // Bo dem ma (CLS-001, CON-002...) truoc tien: ma moi sinh ra khong duoc trung ma da luu.
        IdGenerator.restore((Map<String, Integer>) state.getOrDefault("idCounters", new LinkedHashMap<String, Integer>()));
        platform.getSchool().restore(
                (List<Organization>) state.getOrDefault("organizations", new ArrayList<Organization>()),
                (List<Classroom>) state.getOrDefault("classrooms", new ArrayList<Classroom>()));
        platform.getAssignments().restore((List<Assignment>) state.getOrDefault("assignments", new ArrayList<Assignment>()));
        platform.getMeetings().restore((List<Meeting>) state.getOrDefault("meetings", new ArrayList<Meeting>()));
        platform.getContests().restore(
                (List<Contest>) state.getOrDefault("contests", new ArrayList<Contest>()),
                (Map<String, List<ProctorEvent>>) state.getOrDefault("proctorEvents", new LinkedHashMap<String, List<ProctorEvent>>()));
        platform.getCommunity().restore(
                (List<Notification>) state.getOrDefault("notifications", new ArrayList<Notification>()),
                (Map<String, List<ChatMessage>>) state.getOrDefault("chatChannels", new LinkedHashMap<String, List<ChatMessage>>()),
                (Map<String, Long>) state.getOrDefault("chatLastSeq", new LinkedHashMap<String, Long>()),
                (List<ProblemComment>) state.getOrDefault("comments", new ArrayList<ProblemComment>()));
        platform.getSystem().restoreSettings((Map<String, String>) state.getOrDefault("settings", new LinkedHashMap<String, String>()));
    }

    // ------------------------------------------------------------------ luu

    /** Chup toan bo trang thai hien tai. Moi service tu sao chep du lieu cua minh duoi khoa cua chinh no. */
    private Map<String, Object> snapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("savedAt", LocalDateTime.now().toString());
        state.put("idCounters", IdGenerator.snapshot());
        state.put("organizations", new ArrayList<>(platform.getSchool().getOrganizations()));
        state.put("classrooms", new ArrayList<>(platform.getSchool().getClassrooms()));
        state.put("assignments", new ArrayList<>(platform.getAssignments().getAll()));
        state.put("meetings", new ArrayList<>(platform.getMeetings().all()));
        state.put("contests", new ArrayList<>(platform.getContests().getContests()));
        state.put("proctorEvents", platform.getContests().proctorEventsSnapshot());
        state.put("notifications", platform.getCommunity().notificationsSnapshot());
        state.put("chatChannels", platform.getCommunity().channelsSnapshot());
        state.put("chatLastSeq", platform.getCommunity().lastSeqSnapshot());
        state.put("comments", new ArrayList<>(platform.getCommunity().getComments()));
        state.put("settings", new LinkedHashMap<>(platform.getSystem().getSettings()));
        return state;
    }

    /** Ghi ngay (bo qua co dirty). Tra ve false neu khong ghi duoc. */
    public synchronized boolean saveNow() {
        return saveNow(false);
    }

    /** forceDb: chep sang MySQL ngay (luc may chu dung), khong cho du DB_SAVE_EVERY_MS. */
    private synchronized boolean saveNow(boolean forceDb) {
        dirty = false;      // ha co TRUOC khi chup: thay doi xay ra trong luc ghi se duoc ghi o lan sau
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 16);
            try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
                out.writeObject(snapshot());
            }
            dbDirty = true;
            copyToDatabase(buffer.toByteArray(), forceDb);
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, buffer.toByteArray());
            if (Files.isRegularFile(file)) {
                Files.copy(file, backupFile(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            dirty = true;       // thu lai o lan sau
            System.err.println("Không lưu được dữ liệu lớp học, kỳ thi... ra " + file + ": " + e);
            return false;
        }
    }

    /** Chep ban chup sang MySQL, toi da moi DB_SAVE_EVERY_MS mot lan (tru khi force). Loi thi thu lai lan sau. */
    private void copyToDatabase(byte[] bytes, boolean force) {
        if (database == null || !dbDirty) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastDbSave < DB_SAVE_EVERY_MS) return;
        try {
            database.save(bytes);
            lastDbSave = now;
            dbDirty = false;
        } catch (RuntimeException e) {
            System.err.println("Không chép được dữ liệu lớp học sang MySQL (sẽ thử lại): " + e.getMessage());
        }
    }

    private Path backupFile() {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    /** Bat dau tu luu dinh ky, va luu mot lan cuoi khi may chu dung. */
    public synchronized void start() {
        if (timer != null) return;
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "state-store");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleWithFixedDelay(() -> {
            if (dirty) saveNow();
            else if (dbDirty) saveNow(false);   // file da moi, chi con thieu ban sao MySQL (bi gian cach)
        }, SAVE_EVERY_SECONDS, SAVE_EVERY_SECONDS, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dirty || dbDirty) saveNow(true);
        }, "state-store-shutdown"));
    }
}
