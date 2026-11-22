package com.ptit.oj.model;

/** Lop nguoi dung truu tuong - cha cua Student / Teacher / Admin. */
public abstract class User extends Entity {

    /** Ma vai tro luu trong cot users.role cua CSDL. */
    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_ADMIN = "ADMIN";

    private final String username;
    private final String fullName;

    protected User(String id, String username, String fullName) {
        super(id);
        this.username = username;
        this.fullName = fullName;
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }

    /** Ten vai tro de hien thi cho nguoi dung doc. */
    public abstract String getRole();

    /**
     * Ma vai tro dung cho CSDL va cho viec phan quyen (STUDENT / TEACHER / ADMIN).
     * Tach khoi getRole() vi ten hien thi la tieng Viet, khong nen dem di so sanh.
     */
    public abstract String getRoleCode();

    /** Co duoc tao bai tap khong. Hoc sinh: khong. Giang vien va quan tri vien ghi de thanh true. */
    public boolean canCreateProblem() { return false; }

    /**
     * Co duoc sua bai nay khong (doi chu de, do kho, them / xoa test, xem test an).
     * Mac dinh la khong; Admin sua duoc moi bai, Teacher chi sua bai trong kho cua minh.
     */
    public boolean canEditProblem(Problem problem) { return false; }

    /** Co duoc dua bai nay vao ky thi, bai tap giao, bai hoc khong: bai kho chung hoac bai minh sua duoc. */
    public boolean canUseProblem(Problem problem) {
        return problem.isShared() || canEditProblem(problem);
    }
}
