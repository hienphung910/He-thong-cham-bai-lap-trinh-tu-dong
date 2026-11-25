package com.ptit.oj.controller;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bo loc noi dung cho binh luan duoi bai tap va phong chat, thay cho viec nguoi duyet tung tin.
 * Qua duoc bo loc thi hien ngay; khong qua thi bi tu choi kem ly do de nguoi viet sua lai.
 *
 * Ba phan:
 *  1. TU BAY BA tieng Viet (co dau, khong dau, teencode) va tieng Anh. Truoc khi so voi danh sach, van ban
 *     duoc chuan hoa de go cac cach lach pho bien (cach lam theo cac thu vien loc tu bay nhu "obscenity"):
 *       - ky tu vo hinh chen giua chu (zero-width, soft hyphen...),
 *       - chu "gia" trong giong chu Latin: chu toan khoang (ｆｕｃｋ), chu toan hoc (𝐟𝐮𝐜𝐤), chu khoanh tron,
 *         chu Kirin / Hy Lap / chu hoa nho (сặс, fυck),
 *       - dau phu chong them de lam roi chu ("zalgo"),
 *       - keo dai chu (vclllll, lồnnnn),
 *       - so va ky hieu thay chu (sh1t, l0n, $hit) va che bot chu (f*ck, đ*t),
 *       - tach tung chu bang dau cach hay dau cham (f u c k, đ.m, l.ồ.n).
 *     Cac tu ma ban khong dau trung voi tu binh thuong ("lồn" - "lon", "cặc" - "các") chi bi chan khi viet
 *     dung dau, hoac khi bi nguy trang (l0n, c.a.c, lоn voi chu o Kirin): khong ai viet "lon nuoc" nhu vay.
 *  2. MA NGUON LOI GIAI - cho phep trich vai dong de hoi, tu choi khi la ca mot chuong trinh.
 *  3. LIEN KET - hoc sinh chi duoc gui link toi trang tai lieu, khong gui link trang dan ma (pastebin...).
 *
 * Day la bo loc theo mau, KHONG hieu ngu canh: quan tri vien bo sung tu o Quan tri -> He thong, va noi dung
 * lot luoi van xoa duoc bang tay.
 */
public final class CommentFilter {

    /** So dong ma toi da duoc trich trong mot binh luan / tin nhan. */
    public static final int MAX_CODE_LINES = 3;

    private CommentFilter() { }

    // ================================================================ danh sach tu

    /**
     * Tu / cum tu CO DAU: chi khop khi viet dung dau, vi bo dau di thi trung voi tu binh thuong
     * ("lồn" - "lon", "cặc" - "các", "buồi" - "buổi", "đéo" - "đeo").
     */
    private static final List<String> ACCENTED = Arrays.asList(
            // địt, đụ, đéo va bien the (nguon: blue-eyes-vn/vietnamese-offensive-words, behitek/vietnam-sensitive-words,
            // tu dien chuan hoa ViLexNorm)
            "địt", "địx", "đỵt", "đuỵt", "đýt", "đụ", "đú má", "đú mẹ", "đìu má", "đù má", "đù mẹ", "đậu má",
            "đéo", "đếu", "đíu", "đếch", "đếk", "đệch", "đệt", "đết", "đách", "đél", "đel", "đell", "éo",
            // lồn, cặc, buồi, đĩ
            "lồn", "lìn", "nồn", "lôz", "lòn", "lỏn", "cặc", "kặc", "cặk", "kẹc", "cẹc",
            "buồi", "puồi", "boài", "buồj", "buồl", "đầu buồi", "đầu bòi",
            "đĩ", "đỹ", "đuỹ", "đũy", "đuỷ", "đủy", "điếm", "phò", "fò",
            // tuc tiu khac
            "nứng", "chịch", "bú cu", "dâm đãng", "dâm tặc", "vếu", "chổng mông", "banh háng", "húp sò",
            "cứt", "cức", "cớt", "ăn cứt",
            "đờ mờ", "đê mờ", "đê ca mờ", "đờ cờ mờ", "cờ lờ gờ tờ", "vờ lờ",
            "vãi lồn", "vãi cả lồn", "cái lồn", "vãi đái", "vãi cứt", "mặt lồn", "óc lồn",
            // chui cha me
            "mẹ mày", "má mày", "con mẹ mày", "con mẹ nó", "mẹ cha mày", "bà cha mày", "mả mẹ", "mả cha",
            "tổ cha", "tổ sư", "tiên sư", "mẹ kiếp",
            // lang ma, xuc pham
            "óc chó", "não chó", "óc lợn", "óc trâu", "não lợn", "mặt chó", "láo chó", "chó má",
            "thằng chó", "đồ chó", "chó đẻ", "chó chết", "mất dạy", "mất dzạy", "mứt zại", "khốn nạn", "khúm lạn",
            "khốn kiếp", "đồ khốn", "thằng khốn", "con khốn", "súc vật", "súc sinh",
            "đồ ngu", "thằng ngu", "con ngu", "ngu học", "ngu si", "ngu dốt", "ngu như chó", "ngu như bò",
            "ngu như lợn", "ngu vãi", "đần độn", "thằng đần", "con đần", "thiểu năng",
            "đồ rác", "câm mồm", "im mồm", "cút đi", "đồ điên", "thằng điên", "con điên",
            "đồ đĩ", "con đĩ", "đấm mày", "đập mày", "giết mày",
            // mieu thi vung mien, gioi tinh
            "bê đê", "pê đê", "xăng pha nhớt", "tàu khựa", "ba que");

    /**
     * Cum tu binh thuong co chua tu cam, bo ra truoc khi so: "éo le" (trai ngang), "uốn éo", "quả óc chó"
     * (qua hach), "phò mã". Cach cac bo loc lon tranh "van de Scunthorpe": danh sach cho phep cho tung cum tu,
     * thay vi bo han tu cam.
     */
    private static final List<String> ALLOWED_PHRASES = Arrays.asList(
            "éo le", "uốn éo", "quả óc chó", "hạt óc chó", "dầu óc chó", "bánh óc chó", "sữa óc chó",
            "phò mã", "phò tá", "phò trợ");

    /** Tu / cum tu KHONG DAU (da bo dau, đ -> d) ma khong trung voi tu binh thuong: so khop ca tu. */
    private static final List<String> PLAIN = Arrays.asList(
            // viet tat, teencode
            "dm", "dmm", "dcm", "dcmm", "dkm", "dkmm", "dmcs", "dmml", "dmconcho", "vcl", "vkl", "vl", "vcc",
            "vler", "vlz", "vloz", "vlon", "vlol", "vailol", "vailoz", "clgt", "clm", "clmm", "clg", "ccj", "cdj",
            "cmm", "cmn", "cmnr", "cmnl", "dit", "djt", "djtme", "ditme", "ditmemay", "ditconme", "ditconmemay",
            "disme", "dismia", "dume", "duma", "dmay", "condime", "condimemay", "condicho", "dilon", "diloz", "dilol",
            "codilo", "loz", "lozz", "cailoz", "matlon", "matloz", "thangmatlon", "tml", "cailon", "concac", "concak",
            "caiconcac", "nungcak", "bucak", "buscu", "daubuoi", "caidaubuoi", "dauboi", "occho", "sucvat",
            "sucsinh", "khonnan", "matday", "thieunang", "bede", "buedue", "vlxx", "segg", "parky",
            "dit me", "dit con me", "dis me", "du ma", "du me", "con cac", "vai lon", "oc cho", "me may",
            "mat day", "khon nan", "suc vat", "suc sinh", "thang ngu", "cam mom", "im mom",
            // go Telex ma bo go khong doi ra chu co dau
            "ddm", "ddit", "ddijt", "ddeos", "loonf", "cawcj",
            // tieng Anh
            "fuk", "fck", "fcking", "fking", "fucking", "fucker", "fucked", "motherfucker", "phuck", "fvck",
            "bullshit", "shitty", "bitches", "asshole", "ass hole", "bastard", "cunt", "dick", "dickhead", "pussy",
            "whore", "slut", "faggot", "fag", "nigger", "nigga", "retard", "retarded", "stfu", "gtfo", "wtf",
            "dafuq", "kys", "twat", "wanker", "jerk off", "son of a bitch", "piss off", "dumbass", "jackass");

    /**
     * Goc tu tieng Anh: mot tu CHUA goc nay la bi chan ("fucking", "bullshit", "motherfucker"). Tu viet kieu
     * camelCase duoc tach truoc khi so, de ten bien "hashIterator", "pushItem", "bitCheck" khong bi bat.
     */
    private static final List<String> STEMS = Arrays.asList("fuck", "fvck", "shit", "bitch");

    /**
     * Cum tu bay tieng Viet viet lien (khong dau, khong cach): bat ca khi noi them vao chuoi dai hon
     * ("ditmemayconcho"). Chi dung cum dai va de nhan ra; viet tat 2-3 chu thi khong bao gio so kieu nay.
     */
    private static final List<String> VN_STEMS = Arrays.asList("ditme", "ditcon", "concac", "cailon", "daubuoi", "matlon");

    /**
     * Tu ma ban khong dau TRUNG voi tu binh thuong nen khong chan khi viet thuong ("lon nuoc", "cac ban",
     * "buoi hoc", "deo kinh", "du lich", "di hoc"), nhung chan khi bi NGUY TRANG: thay so (l0n, c4c),
     * tach chu (l.o.n, c a c), che chu (l*n) hay dung chu Kirin / toan khoang - khong ai viet tu binh
     * thuong nhu vay.
     */
    private static final List<String> DISGUISED_ONLY = Arrays.asList(
            "lon", "lin", "cac", "kac", "buoi", "deo", "deu", "dech", "du", "di", "diem", "cut", "nung");

    // ================================================================ chuan hoa

    /**
     * Ky tu khong hien ra man hinh, chen vao giua chu de lach bo loc ("đ​m"): moi ky tu dinh dang
     * (Cf: soft hyphen, zero-width space / joiner, dau dieu khien chieu chu, the ky tu...), dau noi
     * khong hien (CGJ), bo chon bien the, chu "trong" cua Hangul / Khmer / Mong Co va o chu noi trong.
     */
    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\p{Cf}\\u034F\\u115F\\u1160\\u17B4\\u17B5\\u180B-\\u180F\\u2800\\u3164\\uFE00-\\uFE0F\\uFFA0\\uFFF0-\\uFFF8]"
          + "|[\\x{1BCA0}-\\x{1BCA3}\\x{1D173}-\\x{1D17A}\\x{E0000}-\\x{E0FFF}]");

    /**
     * Chu trong giong chu Latin thuong (theo bang "confusables" cua Unicode, UTS #39): chu Kirin, Hy Lap,
     * Armenia va chu hoa nho. Chu toan khoang, chu toan hoc (𝐟), chu khoanh tron (ⓕ) da duoc NFKC dua ve
     * chu Latin truoc buoc nay.
     */
    private static final Map<Integer, Character> CONFUSABLE = new HashMap<>();

    static {
        confusable("авгеёкмнопрстухьіїјѕһԁӏԛԝүҝ", "abreekmhonpctyxbiijshdlqwyk");   // Kirin
        confusable("αβγεηικνορτυχωϲϳ", "abyeniknoptuxwcj");                         // Hy Lap
        confusable("օսհցոզ", "ouhgnq");                                             // Armenia
        confusable("ıȷɑɡɩᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀꜱᴛᴜᴠᴡʏᴢƒ", "ijagiabcdefghijklmnoprstuvwyzf");  // chu Latin dac biet
        confusable("ð", "đ");                                                        // eth Iceland trong nhu đ
    }

    private static void confusable(String from, String to) {
        if (from.codePointCount(0, from.length()) != to.length()) {
            throw new IllegalStateException("Bảng chữ giống nhau bị lệch: " + from);
        }
        int[] codes = from.codePoints().toArray();
        for (int i = 0; i < codes.length; i++) CONFUSABLE.put(codes[i], to.charAt(i));
    }

    /**
     * Chuan hoa de so sanh (KHONG dung de luu): NFKC (chu toan khoang / toan hoc / khoanh tron -> chu thuong),
     * bo ky tu vo hinh, chu thuong, doi chu trong giong Latin, roi bo cac dau phu chong them.
     */
    static String normalize(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = INVISIBLE.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);
        StringBuilder mapped = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            Character latin = CONFUSABLE.get(cp);
            if (latin != null) mapped.append(latin.charValue());
            else if (cp >= 0x1F150 && cp <= 0x1F169) mapped.append((char) ('a' + cp - 0x1F150));   // 🅐 chu tron nen den
            else if (cp >= 0x1F170 && cp <= 0x1F189) mapped.append((char) ('a' + cp - 0x1F170));   // 🅰 chu vuong nen den
            else if (cp >= 0x1F1E6 && cp <= 0x1F1FF) mapped.append((char) ('a' + cp - 0x1F1E6));   // 🇦 chu cai cua co
            else mapped.appendCodePoint(cp);
        });
        s = Normalizer.normalize(mapped, Normalizer.Form.NFC);
        // Sau NFC moi chu tieng Viet, ke ca co dau thanh, la MOT ky tu dung san; dau phu con sot lai la dau
        // chong them vao de lam roi chu ("lồ̃̃n") - bo di.
        return s.replaceAll("\\p{M}+", "");
    }

    /** Chuan hoa nhe: chi NFC va chu thuong - de biet mot tu co bi nguy trang hay khong. */
    private static String light(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /** Bo dau tieng Viet: "đéo" -> "deo". */
    private static String fold(String lower) {
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.replace('đ', 'd');
    }

    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Cac tu cach nhau dung mot dau cach, co dau cach o hai dau: de tim " tu " ma khong dinh vao tu khac. */
    private static String joined(String text) {
        return " " + SEPARATORS.matcher(text).replaceAll(" ").trim() + " ";
    }

    /** Gop cac chu lap lien tiep thanh mot: "fuuuck" -> "fuck", "dmmm" -> "dm", "lồnnn" -> "lồn". */
    private static String squeeze(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (i > 0 && ch == text.charAt(i - 1) && Character.isLetter(ch)) continue;
            out.append(ch);
        }
        return out.toString();
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) return false;
        }
        return true;
    }

    private static String lettersOnly(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) out.append(s.charAt(i));
        }
        return out.toString();
    }

    // ================================================================ tu bay

    /** Danh sach da gop tu bo sung cua quan tri vien, tach theo cach so khop. */
    private static final class Words {
        final Set<String> accented = new LinkedHashSet<>(ACCENTED);
        final Set<String> plain = new LinkedHashSet<>(PLAIN);
        /** Ban viet lien (bo dau cach) cua moi tu co dau / khong dau, de so voi chu da bi tach roi. */
        final Map<String, String> accentedSolid = new HashMap<>();
        final Map<String, String> plainSolid = new HashMap<>();

        Words(Collection<String> extraWords) {
            for (String extra : extraWords) {
                String w = normalize(extra == null ? "" : extra);
                w = SEPARATORS.matcher(w).replaceAll(" ").trim();
                if (w.length() < 2) continue;
                // Tu bo sung khong dau thi so ca khi nguoi viet go co dau hay khong dau; co dau thi phai dung dau.
                if (w.equals(fold(w))) plain.add(w);
                else accented.add(w);
            }
            for (String w : accented) accentedSolid.put(w.replace(" ", ""), w);
            for (String w : plain) plainSolid.put(w.replace(" ", ""), w);
            for (String w : DISGUISED_ONLY) plainSolid.putIfAbsent(w, w);
        }

        /**
         * Mot chuoi chu VIET LIEN (da ghep lai tu chu bi tach / che / thay so) co phai tu bay khong.
         * strict = false: chuoi co the la chu binh thuong (vd. ghep tu "d m" trong mot bieu thuc) nen bo
         * qua tu ngan khong dau.
         */
        String solid(String letters, boolean strict) {
            if (letters.length() < 2) return null;
            String squeezed = squeeze(letters);
            for (String s : new String[] { letters, squeezed }) {
                String hit = accentedSolid.get(s);
                if (hit != null) return hit;
            }
            String folded = fold(letters);
            String foldedSqueezed = squeeze(folded);
            boolean shortAscii = folded.length() < 3 && isAscii(letters);
            for (String s : new String[] { folded, foldedSqueezed }) {
                String hit = plainSolid.get(s);
                if (hit != null && (strict || !shortAscii)) return hit;
            }
            for (String stem : STEMS) {
                if (foldedSqueezed.contains(stem)) return stem;
            }
            return null;
        }
    }

    /**
     * @param extraWords tu cam quan tri vien bo sung (co dau hoac khong dau), co the rong
     * @return tu bay tim thay, hoac null neu khong co
     */
    public static String findProfanity(String text, Collection<String> extraWords) {
        if (text == null || text.trim().isEmpty()) return null;
        Words words = new Words(extraWords);
        String lower = normalize(text);
        String hit = findInText(lower, words);
        if (hit == null) hit = findStems(text);
        if (hit == null) hit = findInLetterRuns(lower, words);
        if (hit == null) hit = findInTokens(text, words);
        return hit;
    }

    /** Buoc 1: so ca tu / cum tu tren van ban da chuan hoa (co dau, khong dau, da gop chu lap). */
    private static String findInText(String lower, Words words) {
        String accented = joined(lower);
        String plain = joined(fold(lower));
        for (String phrase : ALLOWED_PHRASES) {
            accented = accented.replace(" " + phrase + " ", " ");
            plain = plain.replace(" " + fold(phrase) + " ", " ");
        }
        String accentedSqueezed = joined(squeeze(accented));
        String plainSqueezed = joined(squeeze(plain));
        // Tu ngan khong dau ("dm", "vl", "dit") con hay la ten bien trong doan code duoc trich ("vl[i]",
        // "dm = 0", "a.vl") hoac don vi do ("1 dm" = 1 de-xi-met): so tu ngan tren ban da bo cac cho do.
        String plainShort = joined(fold(CODE_IDENTIFIER.matcher(UNIT_DM.matcher(lower).replaceAll(" ")).replaceAll(" ")));
        for (String phrase : ALLOWED_PHRASES) plainShort = plainShort.replace(" " + fold(phrase) + " ", " ");
        String plainShortSqueezed = joined(squeeze(plainShort));
        for (String w : words.accented) {
            if (accented.contains(" " + w + " ")) return w;
            if (squeeze(w).equals(w) && accentedSqueezed.contains(" " + w + " ")) return w;
        }
        for (String w : words.plain) {
            boolean shortWord = w.length() <= 4 && !w.contains(" ");
            String in = shortWord ? plainShort : plain;
            String inSqueezed = shortWord ? plainShortSqueezed : plainSqueezed;
            // Chi so voi ban da gop chu lap khi ban than tu cam khong co chu lap: gop "cmm" thanh "cm" roi di
            // tim thi "10 cm" cung bi chan oan.
            if (in.contains(" " + w + " ")) return w;
            if (squeeze(w).equals(w) && inSqueezed.contains(" " + w + " ")) return w;
        }
        for (String token : plainSqueezed.trim().split(" ")) {
            for (String stem : VN_STEMS) {
                if (token.contains(stem)) return stem;
            }
        }
        return null;
    }

    /**
     * Ten bien trong code: chu Latin dung ngay truoc "[", "(", phep gan, hoac ngay sau dau cham truy cap
     * thanh phan ("a.vl"). Khong tinh dau cham cuoi cau, va khong dung cho tu co chu tieng Viet ("đm.").
     */
    private static final Pattern CODE_IDENTIFIER = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])[a-z_][a-z0-9_]*(?=\\s*(?:\\[|\\(|[+\\-*/%]?=(?!=)))"
          + "|(?<=[a-z0-9_)\\]]\\.)[a-z_][a-z0-9_]*(?![\\p{L}\\p{N}_])");

    /** "1 dm", "2,5dm": de-xi-met trong bai doi don vi, khong phai "đm". */
    private static final Pattern UNIT_DM = Pattern.compile("(?<=\\d)\\s*dm(?![\\p{L}\\p{N}_])");

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    /**
     * Goc tu tieng Anh nam trong mot tu. Tu viet kieu camelCase ("hashIterator", "bitCheck") duoc tach tai
     * cho chu hoa bat dau mot tu moi (chu hoa theo sau boi it nhat hai chu thuong); con "FuCk", "fUcK" (xen
     * hoa thuong de lach) thi khong bi tach.
     */
    private static String findStems(String text) {
        String visible = INVISIBLE.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("");
        Matcher word = WORD.matcher(visible);
        while (word.find()) {
            for (String piece : CAMEL_BOUNDARY.split(word.group())) {
                String folded = squeeze(fold(normalize(piece)));
                for (String stem : STEMS) {
                    if (folded.contains(stem)) return stem;
                }
            }
        }
        return null;
    }

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=\\p{Ll})(?=\\p{Lu}\\p{Ll}{2})");

    /**
     * Mot day chu DON cach nhau bang dau cach hay dau cham / gach / sao: "f u c k", "đ.m", "l . ồ . n".
     * Chu don cuoi cung khong duoc dinh vao chu khac ("a bc" khong phai la day).
     */
    private static final Pattern LETTER_RUN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])\\p{L}(?:[^\\p{L}\\p{N}\\r\\n]{1,3}\\p{L}){1,40}(?![\\p{L}\\p{N}])");
    private static final Pattern RUN_LETTER = Pattern.compile("\\p{L}");

    /** Buoc 2: ghep cac chu bi tach roi lai roi so voi danh sach. */
    private static String findInLetterRuns(String lower, Words words) {
        Matcher run = LETTER_RUN.matcher(lower);
        while (run.find()) {
            String text = run.group();
            List<String> letters = new ArrayList<>();
            Matcher m = RUN_LETTER.matcher(text);
            while (m.find()) letters.add(m.group());
            if (letters.size() < 2) continue;
            // Cach bang dau cach, dau phay, phep tinh ("d m", "d, m", "n - m") co the la bien trong mot bieu thuc
            // nen bo qua tu ngan chi co chu Latin; chen dau cham / sao / gach duoi ("d.m", "v*l") la co y tach chu.
            boolean spacedOnly = !text.matches("(?s).*[.*_~^'`|#].*");
            // Chi cach bang dau cach: CA DAY phai ghep thanh tu bay ("f u c k"), de day bien "a b c d m" hay
            // "i j k" khong bi bat oan. Co y chen dau cham / sao thi thu moi doan con ("x.đ.m" van bat).
            if (spacedOnly) {
                StringBuilder all = new StringBuilder();
                for (String letter : letters) all.append(letter);
                if (words.solid(all.toString(), false) != null) return text.trim();
                continue;
            }
            for (int start = 0; start < letters.size(); start++) {
                StringBuilder joinedLetters = new StringBuilder();
                for (int end = start; end < letters.size() && end - start < 12; end++) {
                    joinedLetters.append(letters.get(end));
                    if (end == start) continue;
                    String hit = words.solid(joinedLetters.toString(), !spacedOnly);
                    if (hit != null) return text.trim();
                }
            }
        }
        return null;
    }

    /** Ky tu thay chu thuong gap (leet speak): so 1 co the la i hoac l. */
    private static final Map<Character, String> LEET = new HashMap<>();

    static {
        LEET.put('0', "o");
        LEET.put('1', "il");
        LEET.put('3', "e");
        LEET.put('4', "a");
        LEET.put('5', "s");
        LEET.put('7', "t");
        LEET.put('8', "b");
        LEET.put('6', "g");
        LEET.put('2', "z");
        LEET.put('9', "g");
        LEET.put('@', "ao");
        LEET.put('$', "s");
        LEET.put('!', "il");
        LEET.put('|', "l");
        LEET.put('€', "e");
    }

    /** Ky tu dung de che bot chu: "f*ck", "đ#t", "sh%t". */
    private static final String MASKS = "*#%?";
    /** Dau dung de tach chu ben trong mot tu: "fu.ck", "lồ-n", "c_ặ_c". */
    private static final String INNER_SEPARATORS = ".-_'`~^,:;+=/\\";

    /**
     * Buoc 3: xet tung tu (tach theo khoang trang) co dau hieu nguy trang - chu gia, ky tu vo hinh, so thay
     * chu, chu bi che, dau cham chen giua - roi giai ma va so voi danh sach.
     */
    private static String findInTokens(String text, Words words) {
        for (String raw : text.split("\\s+")) {
            if (raw.isEmpty()) continue;
            String deep = normalize(raw);
            String core = trimNonWord(deep);
            if (core.isEmpty()) continue;
            // Chu gia / ky tu vo hinh: ban chuan hoa sau khac ban chuan hoa nhe.
            boolean disguised = !lettersOnly(deep).equals(lettersOnly(light(raw)));
            if (disguised) {
                String hit = words.solid(lettersOnly(core), true);
                if (hit != null) return raw;
            }
            int letters = lettersOnly(core).length();
            if (letters < 2) continue;
            boolean hasLeet = false;
            boolean hasMask = false;
            boolean hasInner = false;
            boolean other = false;
            for (int i = 0; i < core.length(); i++) {
                char ch = core.charAt(i);
                if (Character.isLetter(ch)) continue;
                if (LEET.containsKey(ch)) hasLeet = true;
                else if (MASKS.indexOf(ch) >= 0) hasMask = true;
                else if (INNER_SEPARATORS.indexOf(ch) >= 0) hasInner = true;
                else other = true;
            }
            if (other) continue;
            if (hasLeet && !hasMask && !hasInner) {
                for (String decoded : leetVariants(core)) {
                    String hit = words.solid(decoded, decoded.length() >= 3);
                    if (hit != null) return raw;
                }
            }
            if (hasMask && !hasInner) {
                String hit = masked(core, words);
                if (hit != null) return raw;
            }
            String viqr = viqrDecode(deep);
            if (viqr != null) {
                String hit = words.solid(viqr, true);
                if (hit != null) return raw;
            }
            if (hasInner && !hasMask) {
                // "fu.ck", "lồ.n": chi tinh khi ghep lai thanh tu dai >= 3 chu, de "s.dm()" kieu ten bien khong bi bat.
                String joinedLetters = lettersOnly(core);
                if (joinedLetters.length() >= 3) {
                    String hit = words.solid(joinedLetters, true);
                    if (hit != null) return raw;
                }
            }
        }
        return null;
    }

    /** Dau go kieu VIQR con sot lai khi bo go loi: ` (huyen), ' (sac), ~ (nga), ^ (mu), ( (trang), + (moc). */
    private static final String VIQR_MARKS = "`'~^(+";
    private static final String VIQR_COMBINING = "\u0300\u0301\u0303\u0302\u0306\u031B";

    /**
     * Dich mot tu go do kieu VIQR ve chu co dau: "lô`n" -> "lồn", "ddi~t" -> "đĩt", "cho'" -> "chó".
     * Moi dau gan vao nguyen am gan nhat dung truoc no. Tra ve null neu tu khong co dau VIQR nao. Dau "~" o
     * cuoi tu bi bo qua: "đi~", "nha~" la cach viet nhi nhanh, khong phai go VIQR.
     */
    private static String viqrDecode(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && !Character.isLetter(token.charAt(start))) start++;
        while (end > start && !Character.isLetter(token.charAt(end - 1)) && "`'".indexOf(token.charAt(end - 1)) < 0) end--;
        String core = token.substring(start, end).replace("dd", "đ");
        boolean marked = false;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < core.length(); i++) {
            char ch = core.charAt(i);
            int mark = VIQR_MARKS.indexOf(ch);
            if (mark < 0) {
                if (!Character.isLetter(ch)) return null;
                out.append(ch);
                continue;
            }
            marked = true;
            int vowel = out.length() - 1;
            while (vowel >= 0 && !isVowel(out.charAt(vowel))) vowel--;
            if (vowel < 0) continue;
            int at = vowel + 1;
            while (at < out.length() && Character.getType(out.charAt(at)) == Character.NON_SPACING_MARK) at++;
            out.insert(at, VIQR_COMBINING.charAt(mark));
        }
        if (!marked) return null;
        String decoded = Normalizer.normalize(out, Normalizer.Form.NFC);
        return lettersOnly(decoded).length() >= 2 ? decoded : null;
    }

    private static boolean isVowel(char ch) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK) return false;
        return "aeiouy".indexOf(fold(String.valueOf(ch)).charAt(0)) >= 0;
    }

    private static String trimNonWord(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && !isWordChar(s.charAt(start))) start++;
        while (end > start && !isWordChar(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }

    /** Dau / cuoi mot tu: giu chu, so va cac ky tu thay chu (vd. "$hit", "5hit"). */
    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '$' || ch == '@' || ch == '|' || ch == '€';
    }

    /** Cac cach doc mot tu co so / ky hieu thay chu: "sh1t" -> "shit", "shlt"; "l0n" -> "lon". */
    private static List<String> leetVariants(String core) {
        List<String> result = new ArrayList<>();
        for (int choice = 0; choice < 2; choice++) {
            StringBuilder out = new StringBuilder(core.length());
            for (int i = 0; i < core.length(); i++) {
                char ch = core.charAt(i);
                String options = LEET.get(ch);
                if (options == null) out.append(ch);
                else out.append(options.charAt(Math.min(choice, options.length() - 1)));
            }
            String v = out.toString();
            if (lettersOnly(v).equals(v) && !result.contains(v)) result.add(v);
        }
        return result;
    }

    /**
     * Tu bi che bot chu ("f*ck", "f**k", "đ*t", "b*tch"): moi ky tu che la dung MOT chu bat ky. Chi xet khi tu
     * dai >= 4, hoac co chu tieng Viet: "c*c", "l*n" ngan va chi co chu Latin rat de la phep nhan trong code.
     */
    private static String masked(String core, Words words) {
        if (core.length() < 4 && isAscii(core)) return null;
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < core.length(); i++) {
            char ch = core.charAt(i);
            if (MASKS.indexOf(ch) >= 0) regex.append("\\p{L}");
            else regex.append(Pattern.quote(String.valueOf(ch)));
        }
        Pattern pattern = Pattern.compile(regex.toString());
        for (String w : words.accentedSolid.keySet()) {
            if (pattern.matcher(w).matches()) return words.accentedSolid.get(w);
        }
        String folded = fold(core);
        StringBuilder foldedRegex = new StringBuilder();
        for (int i = 0; i < folded.length(); i++) {
            char ch = folded.charAt(i);
            if (MASKS.indexOf(ch) >= 0) foldedRegex.append("[a-z]");
            else foldedRegex.append(Pattern.quote(String.valueOf(ch)));
        }
        Pattern foldedPattern = Pattern.compile(foldedRegex.toString());
        for (String w : words.plainSolid.keySet()) {
            if (w.length() >= 3 && foldedPattern.matcher(w).matches()) return words.plainSolid.get(w);
        }
        for (String stem : STEMS) {
            if (foldedPattern.matcher(stem).matches()) return stem;
        }
        return null;
    }

    // ================================================================ ma nguon

    /** Khung cua mot chuong trinh hoan chinh: co la chac chan khong phai "trich vai dong de hoi". */
    private static final Pattern PROGRAM_FRAME = Pattern.compile(
            "#\\s*include\\s*[<\"]|\\bint\\s+main\\s*\\(|\\bvoid\\s+main\\s*\\(|public\\s+static\\s+void\\s+main"
          + "|using\\s+namespace\\s+std|\\bimport\\s+java\\.|\\bimport\\s+sys\\b|if\\s+__name__\\s*==|\\bclass\\s+\\w+\\s*\\{"
          + "|new\\s+Scanner\\s*\\(|require\\s*\\(\\s*['\"]readline['\"]|process\\.stdin");

    private static final Pattern READS_INPUT = Pattern.compile(
            "\\binput\\s*\\(|\\bcin\\s*>>|\\bscanf\\s*\\(|\\bgetline\\s*\\(|sys\\.stdin|\\.next(?:Int|Long|Double|Line)?\\s*\\(|\\breadline\\s*\\(|\\bgets\\s*\\(");

    private static final Pattern WRITES_OUTPUT = Pattern.compile(
            "\\bprint(?:f|ln)?\\s*\\(|\\bcout\\s*<<|System\\.out\\.|console\\.log\\s*\\(|\\bputs\\s*\\(");

    /** Mot dong trong giong ma: ket thuc bang ; { }, la cau lenh dieu khien, goi ham vao / ra, hoac phep gan. */
    private static final Pattern CODE_LINE = Pattern.compile(
            ".*[;{}]\\s*$"
          + "|^\\s*(?:for|while|if|else\\s+if|switch)\\s*\\(.*\\).*"
          + "|^\\s*(?:for|while|if|elif|else|def|class|try|except|with)\\b.*:\\s*$"
          + "|.*(?:\\bcout\\s*<<|\\bcin\\s*>>|\\bprintf\\s*\\(|\\bscanf\\s*\\(|System\\.out\\.|\\bprint\\s*\\(|\\binput\\s*\\(|console\\.log\\s*\\().*"
          + "|^\\s*[A-Za-z_][\\w\\[\\].]*\\s*(?:[+\\-*/%]?=)\\s*[^=].*");

    /** So lieu ma nguon cua mot doan van ban: de ghep nhieu tin lien tiep cua cung mot nguoi. */
    public static final class CodeStats {
        private final int codeLines;
        private final boolean readsInput;
        private final boolean writesOutput;

        CodeStats(int codeLines, boolean readsInput, boolean writesOutput) {
            this.codeLines = codeLines;
            this.readsInput = readsInput;
            this.writesOutput = writesOutput;
        }

        public int getCodeLines() { return codeLines; }
        public boolean readsInput() { return readsInput; }
        public boolean writesOutput() { return writesOutput; }
    }

    public static CodeStats codeStats(String text) {
        int lines = 0;
        for (String line : stripFences(text).split("\\r?\\n")) {
            String t = line.trim();
            if (t.length() >= 3 && isCodeLine(t)) lines++;
        }
        return new CodeStats(lines, READS_INPUT.matcher(text).find(), WRITES_OUTPUT.matcher(text).find());
    }

    /** Bo cac dong ``` cua khoi ma Markdown, de chung khong bi tinh la dong van. */
    private static String stripFences(String text) {
        return text.replaceAll("(?m)^\\s*```\\w*\\s*$", "");
    }

    /**
     * @return ly do tu choi neu van ban chua ma nguon vuot muc "trich de hoi", null neu duoc phep
     */
    public static String findSolutionCode(String text) {
        if (text == null) return null;
        if (PROGRAM_FRAME.matcher(text).find()) {
            return "có khung của một chương trình hoàn chỉnh (#include, hàm main, import...)";
        }
        CodeStats stats = codeStats(text);
        // Co dong ma, lai vua doc du lieu vua in ket qua: do la mot loi giai chay duoc, du chi vai dong
        // (bai de bang Python chi can 2 dong). Nhac ten ham trong mot cau hoi binh thuong thi khong tinh.
        if (stats.codeLines > 0 && stats.readsInput && stats.writesOutput) {
            return "vừa đọc dữ liệu vừa in kết quả, tức là một lời giải chạy được";
        }
        if (stats.codeLines > MAX_CODE_LINES) {
            return "có " + stats.codeLines + " dòng mã (chỉ được trích tối đa " + MAX_CODE_LINES + " dòng)";
        }
        // Nhieu cau lenh don tren mot dong de lach gioi han so dong.
        int semicolons = 0;
        int braces = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ';') semicolons++;
            else if (ch == '{') braces++;
        }
        if (semicolons > 6 || braces > 3) {
            return "có quá nhiều câu lệnh (chỉ được trích một đoạn ngắn)";
        }
        return null;
    }

    private static boolean isCodeLine(String line) {
        if (!CODE_LINE.matcher(line).matches()) return false;
        // Dong co chu tieng Viet co dau la cau van (cau hoi co kem mot doan ma), khong tinh la dong ma.
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) > 0x7F) return false;
        }
        return true;
    }

    // ================================================================ lien ket

    /** Trang dan / chia se ma: hoc sinh dung de gui loi giai ma khong qua bo loc ma nguon. */
    private static final List<String> PASTE_SITES = Arrays.asList(
            "pastebin", "hastebin", "ideone", "codeshare", "dpaste", "justpaste", "controlc", "rentry", "privatebin",
            "pastie", "paste2", "paste.rs", "paste.gg", "sourceb.in", "termbin", "ghostbin", "onlinegdb", "jdoodle",
            "programiz", "codepad", "cpp.sh", "godbolt", "coliru", "wandbox", "tio.run", "rextester", "codepen",
            "jsfiddle", "replit", "repl.it", "glot.io", "paste.ee", "paste.ubuntu", "gist.github",
            "githubusercontent", "github.com", "gitlab.com", "bitbucket", "vjudge", "drive.google", "docs.google",
            "dropbox", "mediafire", "chatgpt.com/share", "chat.openai.com/share",
            // rut gon link: khong biet link that dan toi dau
            "bit.ly", "tinyurl", "t.co/", "is.gd", "cutt.ly", "rb.gy", "s.id/", "shorturl");

    /** Trang tai lieu hoc sinh duoc gui link. */
    private static final List<String> DOC_SITES = Arrays.asList(
            "cppreference.com", "cplusplus.com", "docs.python.org", "python.org", "docs.oracle.com",
            "developer.mozilla.org", "wikipedia.org", "vnoi.info", "cp-algorithms.com", "stackoverflow.com",
            "ptitcodehub.site");

    /**
     * Dia chi web: co giao thuc / www; ten mien co duoi it gap trong code (.com, .vn...); hoac ten mien co duoi
     * de nham voi code (stack.top, node.info, std.io) nhung theo sau la mot duong dan "/". Duoi ".cc" cua file
     * C++ khong tinh.
     */
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s]+"
          + "|\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|vn|edu|gov|xyz|site|online|tk|ru|cn)\\b(?:/[^\\s]*)?"
          + "|\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.(?:io|me|ly|gg|dev|app|info|co|to|link|top|page|ai)/[^\\s]*"
          + "|\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?/[^\\s]*");

    /**
     * Lien ket hoc sinh KHONG duoc gui: moi link khong phai trang tai lieu, va moi nhac toi trang dan ma
     * (ke ca viet tach "pastebin . com", "ideone dot com").
     * @return mo ta link bi chan, hoac null
     */
    public static String findForbiddenLink(String text) {
        if (text == null) return null;
        String lower = normalize(text);
        String compact = lower.replaceAll("\\s*(?:\\(\\s*\\.\\s*\\)|\\[\\s*\\.\\s*\\]|\\bdot\\b|\\bcham\\b|\\bchấm\\b)\\s*", ".")
                .replaceAll("\\s*\\.\\s*", ".");
        for (String site : PASTE_SITES) {
            if (compact.contains(site)) return site;
        }
        Matcher url = URL.matcher(compact);
        while (url.find()) {
            String found = url.group();
            if (!isDocSite(found)) return found;
        }
        return null;
    }

    /** Ten mien cua link co phai (hoac thuoc) mot trang tai lieu duoc phep. So theo ten mien, khong theo chuoi con. */
    private static boolean isDocSite(String url) {
        String host = url.toLowerCase(Locale.ROOT).replaceFirst("^[a-z]+://", "");
        int cut = host.length();
        for (char stop : new char[] { '/', '?', '#', ':' }) {
            int at = host.indexOf(stop);
            if (at >= 0 && at < cut) cut = at;
        }
        host = host.substring(0, cut);
        if (host.startsWith("www.")) host = host.substring(4);
        for (String site : DOC_SITES) {
            if (host.equals(site) || host.endsWith("." + site)) return true;
        }
        return false;
    }
}
