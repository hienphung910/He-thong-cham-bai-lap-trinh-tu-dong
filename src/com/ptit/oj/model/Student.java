package com.ptit.oj.model;

public class Student extends User {

    private String studentCode;
    private String className;

    public Student(String id, String username, String fullName, String studentCode, String className) {
        super(id, username, fullName);
        this.studentCode = studentCode;
        this.className = className;
    }

    public String getStudentCode() { return studentCode; }
    public String getClassName() { return className; }

    @Override
    public String getRole() { return "Sinh viên"; }

    @Override
    public String getRoleCode() { return ROLE_STUDENT; }
}
