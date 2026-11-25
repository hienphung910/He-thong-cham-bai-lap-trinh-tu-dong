package com.ptit.oj.controller;

import com.ptit.oj.model.User;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cua kiem duyet cho tin nhan chat va binh luan duoi bai, goi TRUOC khi luu. Gom cac lop ma cac nen tang
 * chat lon (Discord AutoMod, Twitch AutoMod, YouTube live chat) cung dung:
 *
 *  1. Dang bi tam khoa thi khong gui duoc gi.
 *  2. Chong spam (khong tinh la vi pham): gui trung lap, gui don dap, lap mot ky tu, qua nhieu bieu tuong,
 *     viet hoa toan bo, qua nhieu dong.
 *  3. Noi dung (TINH la vi pham): tu bay (moi nguoi), link khong phai trang tai lieu, ma nguon loi giai - ke
 *     ca khi tach ra nhieu tin lien tiep (hoc sinh).
 *  4. Leo thang: vi pham 3 lan trong 10 phut thi bi tam khoa chat va binh luan 10 phut; bi khoa lan nua
 *     trong 24 gio thi 1 gio, roi 24 gio. Moi lan bi chan deu bao con bao nhieu lan nua thi bi khoa.
 *
 * Quan tri vien khong bi chan spam va khong bi tam khoa (van bi chan tu bay). Giang vien duoc gui link va
 * ma nguon. Du lieu chi giu trong RAM: khoi dong lai may chu la xoa het vi pham va tam khoa.
 */
public final class ContentGuard {

    public enum Kind { CHAT, COMMENT }

    // ---- leo thang
    private static final long STRIKE_WINDOW_MS = 10 * 60_000L;
    private static final int STRIKES_TO_MUTE = 3;
    private static final long[] MUTE_STEPS_MS = { 10 * 60_000L, 60 * 60_000L, 24 * 60 * 60_000L };
    private static final long MUTE_HISTORY_MS = 24 * 60 * 60_000L;

    // ---- spam
    /** Gui don dap: toi da 5 tin trong 10 giay (ngoai gioi han 30 tin / phut san co). */
    private static final int BURST_LIMIT = 5;
    private static final long BURST_WINDOW_MS = 10_000L;
    /** Cung mot noi dung: chat trong 2 phut, binh luan trong 30 phut. */
    private static final long CHAT_DUPLICATE_MS = 2 * 60_000L;
    private static final long COMMENT_DUPLICATE_MS = 30 * 60_000L;
    /**
     * Lap mot ky tu lien tiep: ky hieu / bieu tuong tu 10 lan ("!!!!!!!!!!", "😂😂😂..."), chu cai tu 20 lan
     * (du rong cho du lieu thu kieu "aaaaaaaab"). Chu so khong tinh (10^9 = 1000000000).
     */
    private static final int MAX_REPEATED_SYMBOL = 10;
    private static final int MAX_REPEATED_LETTER = 20;
    /** Moobot chan tu 12 bieu tuong cam xuc. */
    private static final int MAX_EMOJI = 12;
    /**
     * Viet hoa toan bo (Moobot: tu 15 ky tu, tren 60% chu hoa): tu 6 tu va 15 chu cai, 80% so tu viet hoa het.
     * Tu co so / gach duoi (MAX_N, A1) khong dem; mot cau co vai thuat ngu BFS, TLE, WA van chu yeu la chu thuong.
     */
    private static final int CAPS_MIN_WORDS = 6;
    private static final int CAPS_MIN_LETTERS = 15;
    private static final double CAPS_RATIO = 0.8;
    /** Vortex mac dinh 10 dong; de rong hon cho hoc sinh trich de bai / test. */
    private static final int MAX_LINES = 15;

    // ---- ma nguon tach ra nhieu tin
    private static final long CHAT_CODE_WINDOW_MS = 3 * 60_000L;
    private static final long COMMENT_CODE_WINDOW_MS = 30 * 60_000L;
    private static final int MAX_SPLIT_CODE_LINES = 2 * CommentFilter.MAX_CODE_LINES;

    /** Mot tin da gui qua duoc bo loc, de so voi cac tin sau. */
    private static final class Sent {
        final Kind kind;
        final String scope;
        final long at;
        final String text;
        final CommentFilter.CodeStats code;

        Sent(Kind kind, String scope, long at, String text, CommentFilter.CodeStats code) {
            this.kind = kind;
            this.scope = scope;
            this.at = at;
            this.text = text;
            this.code = code;
        }
    }

    private final SystemService system;
    private final Map<String, Deque<Long>> strikes = new HashMap<>();
    private final Map<String, Long> mutedUntil = new HashMap<>();
    private final Map<String, Deque<Long>> muteHistory = new HashMap<>();
    private final Map<String, Deque<Long>> attempts = new HashMap<>();
    private final Map<String, Deque<Sent>> recent = new HashMap<>();

    public ContentGuard(SystemService system) {
        this.system = system;
    }

    /**
     * Kiem tra mot tin nhan / binh luan truoc khi luu.
     * @param scope kenh chat ("class-CLS-001") hoac ma bai (binh luan)
     * @throws ApiException 400 neu noi dung bi chan, 429 neu gui qua nhanh hoac dang bi tam khoa
     */
    public synchronized void check(User user, Kind kind, String scope, String text) {
        if (text == null || text.trim().isEmpty()) return;      // de Check.text bao "khong duoc de trong"
        String id = user.getId();
        boolean admin = User.ROLE_ADMIN.equals(user.getRoleCode());
        boolean student = User.ROLE_STUDENT.equals(user.getRoleCode());
        long now = System.currentTimeMillis();
        String noun = kind == Kind.CHAT ? "Tin nhắn" : "Bình luận";

        if (!admin) {
            long left = mutedSeconds(id, now);
            if (left > 0) {
                throw new ApiException(429, "Bạn đang bị tạm khoá chat và bình luận vì vi phạm nhiều lần. Hãy thử lại sau "
                        + duration(left * 1000));
            }
            // Dem moi lan bam gui (ca lan bi chan) de chan gui don dap.
            Deque<Long> tries = attempts.computeIfAbsent(id, k -> new ArrayDeque<>());
            prune(tries, now - BURST_WINDOW_MS);
            tries.addLast(now);
            if (kind == Kind.CHAT && tries.size() > BURST_LIMIT) {
                throw new ApiException(429, "Bạn gửi quá nhanh. Hãy đợi vài giây rồi gửi tiếp");
            }
            String spam = spamReason(id, kind, student, text, now);
            if (spam != null) throw ApiException.badRequest(noun + " " + spam);
        }

        String badWord = CommentFilter.findProfanity(text, system.bannedWords());
        if (badWord != null) {
            throw violation(user, admin, now, noun + " có từ ngữ không phù hợp (\"" + badWord + "\")");
        }
        CommentFilter.CodeStats code = CommentFilter.codeStats(text);
        if (student) {
            String link = CommentFilter.findForbiddenLink(text);
            if (link != null) {
                throw violation(user, false, now, noun + " có liên kết không được phép (\"" + shorten(link)
                        + "\"). Học sinh chỉ được gửi link tài liệu như cppreference.com, cplusplus.com, docs.python.org, wikipedia.org");
            }
            String reason = CommentFilter.findSolutionCode(text);
            if (reason == null) reason = splitCodeReason(id, kind, scope, code, now);
            if (reason != null) {
                throw violation(user, false, now, noun + " chứa mã nguồn lời giải: " + reason
                        + ". Hãy hỏi về ý tưởng, hoặc chỉ trích tối đa " + CommentFilter.MAX_CODE_LINES + " dòng mã cần hỏi");
            }
        }
        if (!admin) remember(id, new Sent(kind, scope, now, dedupeKey(text), code));
    }

    /** So giay con bi tam khoa (0 neu khong bi). */
    public synchronized long mutedSeconds(String userId) {
        return mutedSeconds(userId, System.currentTimeMillis());
    }

    private long mutedSeconds(String userId, long now) {
        Long until = mutedUntil.get(userId);
        if (until == null) return 0;
        if (until <= now) {
            mutedUntil.remove(userId);
            return 0;
        }
        return (until - now + 999) / 1000;
    }

    // ------------------------------------------------------------------ vi pham

    /** Ghi mot lan vi pham; du so lan thi tam khoa. Tra ve loi de nem ra, kem canh bao con may lan. */
    private ApiException violation(User user, boolean admin, long now, String message) {
        if (admin) return ApiException.badRequest(message + ". Hãy sửa lại rồi gửi");
        String id = user.getId();
        Deque<Long> list = strikes.computeIfAbsent(id, k -> new ArrayDeque<>());
        prune(list, now - STRIKE_WINDOW_MS);
        list.addLast(now);
        int left = STRIKES_TO_MUTE - list.size();
        if (left > 0) {
            return ApiException.badRequest(message + ". Hãy sửa lại rồi gửi. Vi phạm thêm " + left
                    + " lần trong 10 phút sẽ bị tạm khoá chat và bình luận");
        }
        list.clear();
        Deque<Long> history = muteHistory.computeIfAbsent(id, k -> new ArrayDeque<>());
        prune(history, now - MUTE_HISTORY_MS);
        long length = MUTE_STEPS_MS[Math.min(history.size(), MUTE_STEPS_MS.length - 1)];
        history.addLast(now);
        mutedUntil.put(id, now + length);
        system.log(user.getUsername(), "Tạm khoá chat và bình luận",
                duration(length) + " vì vi phạm " + STRIKES_TO_MUTE + " lần trong 10 phút", false);
        return new ApiException(429, message + ". Bạn đã vi phạm " + STRIKES_TO_MUTE
                + " lần nên bị tạm khoá chat và bình luận " + duration(length));
    }

    // ------------------------------------------------------------------ spam

    private String spamReason(String id, Kind kind, boolean student, String text, long now) {
        String key = dedupeKey(text);
        long window = kind == Kind.CHAT ? CHAT_DUPLICATE_MS : COMMENT_DUPLICATE_MS;
        Deque<Sent> mine = recent.get(id);
        if (mine != null) {
            for (Sent s : mine) {
                if (s.kind == kind && s.at >= now - window && s.text.equals(key)) {
                    return "này bạn vừa gửi rồi. Đừng gửi lặp lại cùng một nội dung";
                }
            }
        }
        if (!student) return null;

        // Doan trong dau `...` / ```...``` la ma hoac du lieu thu: khong xet lap ky tu va viet hoa.
        String prose = text.replaceAll("(?s)```.*?```", " ").replaceAll("`[^`\\n]*`", " ");
        int[] cps = prose.codePoints().toArray();
        int run = 1;
        int emoji = 0;
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            if (i > 0 && cp == cps[i - 1] && !Character.isDigit(cp) && !Character.isWhitespace(cp)) {
                run++;
                int limit = Character.isLetter(cp) ? MAX_REPEATED_LETTER : MAX_REPEATED_SYMBOL;
                if (run >= limit) return "lặp một ký tự quá nhiều lần";
            } else {
                run = 1;
            }
            if (isEmoji(cp)) emoji++;
        }
        if (emoji > MAX_EMOJI) return "có quá nhiều biểu tượng cảm xúc (tối đa " + MAX_EMOJI + ")";
        // Dem theo TU chu khong theo chu cai: tieng Viet toan tu ngan ("BÀI NÀY KHÓ QUÁ"), con mot cau co vai
        // thuat ngu viet hoa ("Em bị TLE và WA ở BFS") van chu yeu la tu viet thuong.
        int letters = 0;
        int words = 0;
        int upperWords = 0;
        for (String word : prose.split("[^\\p{L}\\p{N}_]+")) {
            if (word.length() < 2 || !word.matches("\\p{L}+")) continue;
            words++;
            letters += word.length();
            if (word.equals(word.toUpperCase(java.util.Locale.ROOT))) upperWords++;
        }
        if (words >= CAPS_MIN_WORDS && letters >= CAPS_MIN_LETTERS && upperWords >= words * CAPS_RATIO) {
            return "viết hoa gần như toàn bộ. Hãy viết thường như bình thường";
        }
        String[] lines = text.split("\\r?\\n", -1);
        if (lines.length > MAX_LINES) return "quá dài (tối đa " + MAX_LINES + " dòng)";
        int blank = 0;
        for (String line : lines) {
            blank = line.trim().isEmpty() ? blank + 1 : 0;
            if (blank >= 3) return "có quá nhiều dòng trống";
        }
        return null;
    }

    private static boolean isEmoji(int cp) {
        int type = Character.getType(cp);
        return type == Character.OTHER_SYMBOL || (cp >= 0x1F000 && cp <= 0x1FAFF);
    }

    /** Khoa so trung lap: chuan hoa nhu bo loc tu bay, gop khoang trang, bo dau cau o cuoi. */
    private static String dedupeKey(String text) {
        return CommentFilter.normalize(text).replaceAll("\\s+", " ").replaceAll("[\\p{P}\\s]+$", "").trim();
    }

    // ------------------------------------------------------------------ ma nguon tach tin

    /**
     * Hoc sinh tach mot loi giai ra nhieu tin lien tiep de moi tin duoi 3 dong. Cong cac dong ma cua cac tin
     * gan day trong cung kenh chat (3 phut) / cung bai (30 phut) voi tin nay.
     */
    private String splitCodeReason(String id, Kind kind, String scope, CommentFilter.CodeStats code, long now) {
        if (code.getCodeLines() == 0) return null;
        long window = kind == Kind.CHAT ? CHAT_CODE_WINDOW_MS : COMMENT_CODE_WINDOW_MS;
        int lines = code.getCodeLines();
        boolean reads = code.readsInput();
        boolean writes = code.writesOutput();
        int messages = 1;
        Deque<Sent> mine = recent.get(id);
        if (mine != null) {
            for (Sent s : mine) {
                if (s.kind != kind || !s.scope.equals(scope) || s.at < now - window) continue;
                if (s.code.getCodeLines() == 0) continue;
                lines += s.code.getCodeLines();
                reads |= s.code.readsInput();
                writes |= s.code.writesOutput();
                messages++;
            }
        }
        if (messages < 2) return null;
        if (lines > MAX_SPLIT_CODE_LINES) {
            return "các tin gần đây cộng lại có " + lines + " dòng mã (không được chia một lời giải ra nhiều tin)";
        }
        if (reads && writes) {
            return "các tin gần đây ghép lại vừa đọc dữ liệu vừa in kết quả, tức là một lời giải chạy được";
        }
        return null;
    }

    // ------------------------------------------------------------------ tien ich

    private void remember(String id, Sent sent) {
        Deque<Sent> mine = recent.computeIfAbsent(id, k -> new ArrayDeque<>());
        mine.addLast(sent);
        long oldest = sent.at - Math.max(COMMENT_DUPLICATE_MS, COMMENT_CODE_WINDOW_MS);
        for (Iterator<Sent> it = mine.iterator(); it.hasNext();) {
            if (it.next().at < oldest) it.remove();
        }
        while (mine.size() > 30) mine.pollFirst();
    }

    private static void prune(Deque<Long> times, long oldest) {
        while (!times.isEmpty() && times.peekFirst() < oldest) times.pollFirst();
    }

    private static String duration(long ms) {
        long minutes = (ms + 59_999) / 60_000;
        if (minutes < 60) return minutes + " phút";
        long hours = (minutes + 59) / 60;
        return hours + " giờ";
    }

    private static String shorten(String s) {
        return s.length() <= 60 ? s : s.substring(0, 60) + "...";
    }
}
