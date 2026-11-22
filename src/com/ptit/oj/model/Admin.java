package com.ptit.oj.model;

/**
 * Tai khoan quan tri. Day la vai tro DUY NHAT duoc sua KHO BAI CHUNG:
 * canEditProblem() cua User tra ve false, Teacher chi nhan bai cua minh, con Admin
 * ghi de thanh true voi moi bai (polymorphism - noi goi khong can biet dang cam vai tro nao).
 */
public class Admin extends User {

    public Admin(String id, String username, String fullName) {
        super(id, username, fullName);
    }

    @Override
    public String getRole() { return "Quản trị viên"; }

    @Override
    public String getRoleCode() { return ROLE_ADMIN; }

    /** Bai do Admin tao di vao kho chung. */
    @Override
    public boolean canCreateProblem() { return true; }

    /** Admin sua duoc moi bai: ca kho chung lan kho rieng cua giang vien. */
    @Override
    public boolean canEditProblem(Problem problem) { return true; }
}
