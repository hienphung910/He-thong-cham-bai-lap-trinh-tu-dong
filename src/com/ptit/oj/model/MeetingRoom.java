package com.ptit.oj.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trang thai "dang dien ra" cua mot phong hoc (Module 14, 17, 18, 19):
 * ai dang co mat, ma nguon dang giang, bang trang, ai dang gio tay.
 * Trinh duyet hoi lai trang thai nay moi 1-2 giay (polling).
 */
public class MeetingRoom {

    /** userId -> lan cuoi trinh duyet cua nguoi do hoi trang thai (mili giay). */
    private final Map<String, Long> lastSeen = new LinkedHashMap<>();
    private final Set<String> raisedHands = new LinkedHashSet<>();
    private final List<Stroke> strokes = new ArrayList<>();
    private String code = "";
    private String language = "Java";
    private int codeVersion = 0;           // tang 1 moi lan co nguoi sua ma
    private boolean collaborative = false; // false: chi chu phong sua (live coding); true: ca lop cung sua

    public Map<String, Long> getLastSeen() { return lastSeen; }
    public Set<String> getRaisedHands() { return raisedHands; }
    public List<Stroke> getStrokes() { return strokes; }

    public String getCode() { return code; }
    public String getLanguage() { return language; }
    public int getCodeVersion() { return codeVersion; }

    public void updateCode(String code, String language) {
        this.code = code;
        this.language = language;
        this.codeVersion++;
    }

    public boolean isCollaborative() { return collaborative; }
    public void setCollaborative(boolean collaborative) { this.collaborative = collaborative; }
}
