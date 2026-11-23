package com.ptit.oj.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Khoa hoc (Module 4): thong tin chung + giao trinh la danh sach bai giang theo thu tu. */
public class Course extends Entity {

    private final String title;
    private final String description;
    private final String level;            // vi du "Cơ bản", "Nâng cao"
    private final List<Lesson> lessons = new CopyOnWriteArrayList<>();   // doc duoc trong luc request khac them bai giang

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
