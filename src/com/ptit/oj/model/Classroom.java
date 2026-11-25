package com.ptit.oj.model;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Lop hoc (Module 3): mot giang vien phu trach va nhieu hoc sinh.
 *
 * Hoc sinh tu tham gia bang MA THAM GIA (joinCode) sinh ngau nhien, khong phai bang ma lop:
 * ma lop danh so tuan tu (CLS-001, CLS-002...) nen ai cung doan duoc.
 *
 * Cac tap thanh vien dung CopyOnWriteArraySet: nhieu request cung doc trong luc mot request
 * khac them / xoa thanh vien ma khong bi ConcurrentModificationException, va van giu thu tu them.
 */
public class Classroom extends Entity {

    /** Lop nay duoc luu xuong o dia (StateStore): giu nguyen so nay de ban luu cu van doc duoc khi them truong. */
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String organizationId;   // "" neu lop khong thuoc to chuc nao
    private final String courseId;         // "" neu lop chua gan khoa hoc
    private final String teacherId;
    private final Set<String> studentIds = new CopyOnWriteArraySet<>();
    /**
     * Hoc sinh duoc giang vien moi bang ten dang nhap nhung CHUA dong y. Chua phai thanh vien:
     * khong thay bai cua lop, va giang vien chua xem duoc phan tich hoc tap cua ho.
     */
    private final Set<String> invitedIds = new CopyOnWriteArraySet<>();
    /** Hoc sinh da bi giang vien xoa khoi lop: khong tu tham gia lai duoc. */
    private final Set<String> removedIds = new CopyOnWriteArraySet<>();
    private final String joinCode = newJoinCode();

    /** Bo cac ky tu de nham (0/O, 1/I/L) de doc cho nhau khong sai. */
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String newJoinCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return code.toString();
    }

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
    public Set<String> getStudentIds() { return studentIds; }
    public Set<String> getRemovedIds() { return removedIds; }
    public Set<String> getInvitedIds() { return invitedIds; }

    /** Ma cac bai giang vien chon rieng cho lop nay luyen tap, theo thu tu them. */
    private final List<String> practiceProblemIds = new CopyOnWriteArrayList<>();

    public List<String> getPracticeProblemIds() { return practiceProblemIds; }
    public String getJoinCode() { return joinCode; }

    /** Giang vien phu trach lop. */
    public boolean isTaughtBy(String userId) {
        return teacherId.equals(userId);
    }

    /** Moi nguoi thuoc lop: giang vien va hoc sinh. */
    public boolean hasMember(String userId) {
        return isTaughtBy(userId) || studentIds.contains(userId);
    }
}
