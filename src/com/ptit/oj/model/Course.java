package com.ptit.oj.model;

import java.util.ArrayList;
import java.util.List;

/** Khoa hoc (Module 4): thong tin chung + giao trinh la danh sach bai giang theo thu tu. */
public class Course extends Entity {

    private final String title;
    private final String description;
    private final String level;            // vi du "Cơ bản", "Nâng cao"
    private final List<Lesson> lessons = new ArrayList<>();

    public Course(String id, String title, String description, String level) {
        super(id);
        this.title = title;
        this.description = description;
        this.level = level;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLevel() { return level; }
    public List<Lesson> getLessons() { return lessons; }
}
