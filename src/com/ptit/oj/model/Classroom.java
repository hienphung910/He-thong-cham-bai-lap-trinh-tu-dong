package com.ptit.oj.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lop hoc (Module 3): mot giang vien phu trach, co the co tro giang va nhieu hoc sinh.
 * Ma lop (id) cung la ma de hoc sinh tu tham gia.
 */
public class Classroom extends Entity {

    private final String name;
    private final String organizationId;   // "" neu lop khong thuoc to chuc nao
    private final String courseId;         // "" neu lop chua gan khoa hoc
    private final String teacherId;
    private final Set<String> assistantIds = new LinkedHashSet<>();
    private final Set<String> studentIds = new LinkedHashSet<>();

    public Classroom(String id, String name, String organizationId, String courseId, String teacherId) {
        super(id);
        this.name = name;
        this.organizationId = organizationId;
        this.courseId = courseId;
        this.teacherId = teacherId;
    }

    public String getName() { return name; }
    public String getOrganizationId() { return organizationId; }
    public String getCourseId() { return courseId; }
    public String getTeacherId() { return teacherId; }
    public Set<String> getAssistantIds() { return assistantIds; }
    public Set<String> getStudentIds() { return studentIds; }

    /** Giang vien phu trach hoac tro giang cua lop. */
    public boolean isTaughtBy(String userId) {
        return teacherId.equals(userId) || assistantIds.contains(userId);
    }

    /** Moi nguoi thuoc lop: giang vien, tro giang, hoc sinh. */
    public boolean hasMember(String userId) {
        return isTaughtBy(userId) || studentIds.contains(userId);
    }
}
