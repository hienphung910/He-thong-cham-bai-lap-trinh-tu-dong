package com.ptit.oj.dao;

import com.ptit.oj.model.Submission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bo nho dem truoc kho bai nop MySQL.
 *
 * Vi sao: bang xep hang, thong ke, bao cao, ky thi... can DUYET TOAN BO lich su nop bai. Doc lai ca bang tu
 * MySQL moi lan mo trang (kem ket qua tung test) thi cang nhieu bai nop cang cham. Lop nay doc MOT LAN luc
 * dung lan dau, sau do moi lan ghi deu cap nhat ca CSDL lan ban trong bo nho, nen doc chi con la duyet RAM.
 *
 * Ban trong bo nho KHONG giu ma nguon (Submission.lightCopy()) - phan nang nhat; mo xem mot bai (findById)
 * thi doc thang CSDL de co du ma nguon.
 *
 * Gia dinh: chi MOT may chu ghi vao CSDL nay (dung nhu cach deploy hien tai). Hai may chu cung ghi thi ban
 * trong bo nho cua moi may khong thay bai nop cua may kia.
 */
public class CachedSubmissionRepository implements SubmissionRepository {

    private static final Comparator<Submission> ORDER =
            Comparator.comparing(Submission::getSubmittedAt).thenComparing(Submission::getId);

    private final SubmissionRepository store;
    /** Cu nhat truoc; null = chua nap. */
    private List<Submission> all;
    private final Map<String, Submission> byId = new HashMap<>();

    public CachedSubmissionRepository(SubmissionRepository store) {
        this.store = store;
    }

    /** Nap lan dau. Goi sau khi de bai da nap (bai nop tro toi de bai). */
    private void ensureLoaded() {
        if (all != null) return;
        List<Submission> loaded = new ArrayList<>();
        for (Submission s : store.findAll()) loaded.add(s.lightCopy());
        loaded.sort(ORDER);
        all = loaded;
        for (Submission s : loaded) byId.put(s.getId(), s);
    }

    private void upsert(Submission light) {
        Submission old = byId.put(light.getId(), light);
        if (old != null) all.remove(old);
        // Hau het la bai moi nhat: them vao cuoi; neu khong thi chen dung cho theo thoi gian.
        int at = all.size();
        while (at > 0 && ORDER.compare(all.get(at - 1), light) > 0) at--;
        all.add(at, light);
    }

    // ------------------------------------------------------------ ghi

    @Override
    public void save(Submission submission) {
        saveWithResults(submission);
    }

    @Override
    public void saveWithResults(Submission submission) {
        store.saveWithResults(submission);
        synchronized (this) {
            ensureLoaded();
            upsert(submission.lightCopy());
        }
    }

    /**
     * Bai nop dang cho cham (hang doi cham bat dong bo): chi them vao bo nho de lich su hien "Đang chấm";
     * CSDL chi ghi khi cham xong (saveWithResults), nen khong ton tai ban ghi do dang trong CSDL.
     */
    public synchronized void putPending(Submission submission) {
        ensureLoaded();
        upsert(submission.lightCopy());
    }

    @Override
    public boolean deleteById(String id) {
        boolean deleted = store.deleteById(id);
        synchronized (this) {
            if (all != null) {
                Submission old = byId.remove(id);
                if (old != null) all.remove(old);
            }
        }
        return deleted;
    }

    /** Tai khoan bi xoa: CSDL tu xoa bai nop theo (ON DELETE CASCADE); bo khoi bo nho cho khop. */
    public synchronized void evictUser(String userId) {
        if (all == null) return;
        all.removeIf(s -> {
            boolean mine = s.getAuthor().getId().equals(userId);
            if (mine) byId.remove(s.getId());
            return mine;
        });
    }

    /** Tai khoan doi vai tro (doi tuong User moi): doc lai bai nop cua nguoi do de tro dung doi tuong moi. */
    public void refreshUser(String userId) {
        List<Submission> fresh = store.findByUserId(userId);
        synchronized (this) {
            if (all == null) return;
            evictUser(userId);
            for (Submission s : fresh) upsert(s.lightCopy());
        }
    }

    // ------------------------------------------------------------ doc

    /** Mo xem mot bai: doc thang CSDL de co ma nguon; bai dang cho cham (chua co trong CSDL) thi lay o bo nho. */
    @Override
    public Optional<Submission> findById(String id) {
        Optional<Submission> full = store.findById(id);
        if (full.isPresent()) return full;
        synchronized (this) {
            return Optional.ofNullable(all == null ? null : byId.get(id));
        }
    }

    @Override
    public synchronized List<Submission> findAll() {
        ensureLoaded();
        return new ArrayList<>(all);
    }

    @Override
    public synchronized List<Submission> findAllNewestFirst() {
        ensureLoaded();
        List<Submission> copy = new ArrayList<>(all);
        Collections.reverse(copy);
        return copy;
    }

    @Override
    public synchronized List<Submission> findByUserId(String userId) {
        ensureLoaded();
        List<Submission> mine = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            Submission s = all.get(i);
            if (s.getAuthor().getId().equals(userId)) mine.add(s);
        }
        return mine;
    }

    @Override
    public synchronized int count() {
        ensureLoaded();
        return all.size();
    }
}
