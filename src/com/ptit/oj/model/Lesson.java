package com.ptit.oj.model;

import java.util.List;

/** Mot bai giang trong giao trinh cua khoa hoc: noi dung + cac bai tap luyen kem theo. */
public class Lesson {

    private final String title;
    private final String content;
    private final List<String> problemIds;

    public Lesson(String title, String content, List<String> problemIds) {
        this.title = title;
        this.content = content;
        this.problemIds = problemIds;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public List<String> getProblemIds() { return problemIds; }
}
