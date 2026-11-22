package com.ptit.oj.model;

/** Thong tin lien he cua phu huynh mot hoc sinh (Module 32). Phu huynh khong co tai khoan dang nhap. */
public class ParentContact extends Entity {

    private final String studentId;
    private final String fullName;
    private final String phone;
    private final String email;

    public ParentContact(String id, String studentId, String fullName, String phone, String email) {
        super(id);
        this.studentId = studentId;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
    }

    public String getStudentId() { return studentId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
}
