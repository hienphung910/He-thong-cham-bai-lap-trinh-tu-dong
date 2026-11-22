package com.ptit.oj.model;

public class Teacher extends User {

    private final String department;

    public Teacher(String id, String username, String fullName, String department) {
        super(id, username, fullName);
        this.department = department;
    }

    public String getDepartment() { return department; }

    @Override
    public String getRole() { return "Giảng viên"; }

    @Override
    public String getRoleCode() { return ROLE_TEACHER; }

    /** Giang vien tao duoc bai, nhung bai do nam trong KHO RIENG cua giang vien (xem Problem.getOwner()). */
    @Override
    public boolean canCreateProblem() { return true; }

    /** Giang vien chi sua duoc bai do chinh minh tao; bai kho chung va bai cua nguoi khac thi khong. */
    @Override
    public boolean canEditProblem(Problem problem) {
        return getUsername().equals(problem.getOwner());
    }
}
