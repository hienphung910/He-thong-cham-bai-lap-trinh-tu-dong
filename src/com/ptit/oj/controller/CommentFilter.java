package com.ptit.oj.controller;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bo loc binh luan duoi bai tap, thay cho viec nguoi duyet tung binh luan. Binh luan qua duoc bo loc
 * thi hien ngay; khong qua thi bi tu choi kem ly do de nguoi viet sua lai.
 *
 * Hai phan:
 *  1. TU BAY BA - tieng Viet (co dau, khong dau, viet tat kieu "đm", "vcl") va tieng Anh. Co xu ly cac
 *     cach ne thuong gap: keo dai chu ("vclllll"), chen dau cham / dau sao giua cac chu ("đ.m", "f*ck").
 *  2. MA NGUON LOI GIAI - cho phep trich vai dong de hoi ("tai sao for (int i = 0; i <= n; i++) lai sai?"),
 *     tu choi khi binh luan la ca mot chuong trinh: co khung chuong trinh (#include, int main, import...),
 *     vua doc du lieu vua in ket qua, hoac co qua MAX_CODE_LINES dong ma.
 *
 * Bo loc tu bay chi bat duoc cac tu trong danh sach: quan tri vien bo sung them tu o Quan tri -> He thong,
 * va binh luan lot luoi van xoa duoc bang tay. Day la bo loc theo mau, KHONG hieu ngu canh.
 */
public final class CommentFilter {

    /** So dong ma toi da duoc trich trong mot binh luan. */
    public static final int MAX_CODE_LINES = 3;

    private CommentFilter() { }

    // ------------------------------------------------------------------ tu bay

    /**
     * Tu / cum tu CO DAU: chi khop khi viet dung dau, vi bo dau di thi trung voi tu binh thuong
     * ("lồn" - "lon", "cặc" - "các", "buồi" - "buổi", "đéo" - "đeo").
     */
    private static final List<String> ACCENTED = Arrays.asList(
            "địt", "đụ", "đéo", "lồn", "lìn", "cặc", "kặc", "buồi", "đĩ", "điếm",
            "đù má", "đù mẹ", "đậu má", "vãi lồn", "vãi cả lồn", "óc chó", "mẹ mày", "má mày", "con mẹ mày",
            "thằng chó", "đồ chó", "chó đẻ", "chó chết", "mất dạy", "khốn nạn", "đồ ngu", "thằng ngu", "con ngu",
            "ngu như chó", "ngu như bò", "ngu vãi");

    /** Tu / cum tu KHONG DAU (da bo dau, đ -> d): viet tat va cach go khong dau khong trung voi tu binh thuong. */
    private static final List<String> PLAIN = Arrays.asList(
            "dm", "dmm", "dcm", "dcmm", "dkm", "dkmm", "vcl", "vkl", "vl", "vcc", "clgt", "cmm", "dit", "djt",
            "ditme", "dume", "duma", "loz", "cailon", "concac",
            "dit me", "dit con me", "du ma", "du me", "con cac", "cai lon", "vai lon", "oc cho", "me may",
            "thang cho", "cho de", "mat day", "khon nan",
            "bitch", "asshole", "bastard", "cunt", "pussy", "whore", "slut", "faggot", "nigger", "nigga", "stfu");

    /** Goc tu tieng Anh: mot tu chua goc nay la bi chan ("fucking", "bullshit", "motherfucker"). */
    private static final List<String> STEMS = Arrays.asList("fuck", "shit", "bitch");

    /**
     * Chu cai chen ky tu la o giua: f*ck, f**k, sh*t, sh!t. Khong tinh dau cham / gach duoi / gach ngang,
     * vi "f_k" hay "f.k" la ten bien, ten truong binh thuong trong binh luan ve lap trinh.
     */
    private static final Pattern MASKED = Pattern.compile(
            "(?<![a-z])f[*@#]+c?k|(?<![a-z])sh[*@#!1]+t(?![a-z])|(?<![a-z])b[*@#!1]+tch");

    /** Cac chu cai don ngan nhau bang dau cham / sao / gach: "d.m", "v.c.l", "f.u.c.k". */
    private static final Pattern SPELLED = Pattern.compile("(?<![a-z0-9])[a-z](?:[.*_\\-]+[a-z])+(?![a-z0-9])");

    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * @param extraWords tu cam quan tri vien bo sung (co dau hoac khong dau), co the rong
     * @return tu bay tim thay (da chuan hoa), hoac null neu khong co
     */
    public static String findProfanity(String text, Collection<String> extraWords) {
        if (text == null || text.trim().isEmpty()) return null;
        String lower = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String folded = fold(lower);

        String accented = joined(lower);
        String plain = joined(folded);
        String squeezed = joined(squeeze(folded));     // "vclllll" -> "vcl"

        Set<String> accentedWords = new LinkedHashSet<>(ACCENTED);
        Set<String> plainWords = new LinkedHashSet<>(PLAIN);
        for (String extra : extraWords) {
            String w = Normalizer.normalize(extra == null ? "" : extra, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
            w = SEPARATORS.matcher(w).replaceAll(" ").trim();
            if (w.length() < 2) continue;
            // Tu bo sung khong dau thi so ca khi nguoi viet go co dau hay khong dau; co dau thi phai dung dau.
            if (w.equals(fold(w))) plainWords.add(w);
            else accentedWords.add(w);
        }

        for (String w : accentedWords) {
            if (accented.contains(" " + w + " ")) return w;
        }
        for (String w : plainWords) {
            // Chi so voi ban da gop chu lap khi ban than tu cam khong co chu lap: gop "cmm" thanh "cm"
            // roi di tim thi "10 cm" cung bi chan oan.
            if (plain.contains(" " + w + " ") || (squeeze(w).equals(w) && squeezed.contains(" " + w + " "))) return w;
        }
        for (String token : squeezed.trim().split(" ")) {
            for (String stem : STEMS) {
                if (token.contains(stem)) return stem;
            }
        }
        Matcher masked = MASKED.matcher(folded);
        if (masked.find()) return masked.group();
        // "đ.m", "v.c.l": ghep cac chu cai don lai roi so voi danh sach.
        Matcher spelled = SPELLED.matcher(folded);
        while (spelled.find()) {
            String word = squeeze(spelled.group().replaceAll("[^a-z]", ""));
            if (plainWords.contains(word)) return spelled.group();
            for (String stem : STEMS) {
                if (word.contains(stem)) return spelled.group();
            }
        }
        return null;
    }

    /** Bo dau tieng Viet: "đéo" -> "deo". */
    private static String fold(String lower) {
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.replace('đ', 'd');
    }

    /** Cac tu cach nhau dung mot dau cach, co dau cach o hai dau: de tim " tu " ma khong dinh vao tu khac. */
    private static String joined(String text) {
        return " " + SEPARATORS.matcher(text).replaceAll(" ").trim() + " ";
    }

    /** Gop cac chu lap lien tiep thanh mot: "fuuuck" -> "fuck", "dmmm" -> "dm". */
    private static String squeeze(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (i > 0 && ch == text.charAt(i - 1) && Character.isLetter(ch)) continue;
            out.append(ch);
        }
        return out.toString();
    }

    // -------------------------------------------------------------- ma nguon

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

    /**
     * @return ly do tu choi neu binh luan chua ma nguon vuot muc "trich de hoi", null neu duoc phep
     */
    public static String findSolutionCode(String text) {
        if (text == null) return null;
        if (PROGRAM_FRAME.matcher(text).find()) {
            return "có khung của một chương trình hoàn chỉnh (#include, hàm main, import...)";
        }
        List<String> codeLines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.length() >= 3 && isCodeLine(t)) codeLines.add(t);
        }
        // Co dong ma, lai vua doc du lieu vua in ket qua: do la mot loi giai chay duoc, du chi vai dong
        // (bai de bang Python chi can 2 dong). Nhac ten ham trong mot cau hoi binh thuong thi khong tinh.
        if (!codeLines.isEmpty() && READS_INPUT.matcher(text).find() && WRITES_OUTPUT.matcher(text).find()) {
            return "vừa đọc dữ liệu vừa in kết quả, tức là một lời giải chạy được";
        }
        if (codeLines.size() > MAX_CODE_LINES) {
            return "có " + codeLines.size() + " dòng mã (chỉ được trích tối đa " + MAX_CODE_LINES + " dòng)";
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
}
