package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Organization;
import com.ptit.oj.model.ParentContact;
import com.ptit.oj.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Nghiep vu ve to chuc, lop hoc va phu huynh (Module 2, 3, 32, 35).
 *
 * Quy tac phan quyen cua lop hoc nam o day:
 *  - quan ly lop = quan tri vien, giang vien phu trach hoac tro giang cua lop do,
 *  - thanh vien  = nguoi quan ly + hoc sinh cua lop.
 */
public class SchoolService {

    private final Repository<Organization> organizations = new InMemoryRepository<>();
    private final Repository<Classroom> classrooms = new InMemoryRepository<>();
    private final Repository<ParentContact> parents = new InMemoryRepository<>();

    // ------------------------------------------------------------ to chuc

    public Organization createOrganization(String name, String address, String description) {
        Organization org = new Organization(IdGenerator.next("ORG"),
                Check.text(name, "Tên tổ chức", 150),
                Check.optional(address, "Địa chỉ", 200),
                Check.optional(description, "Mô tả", 500));
        organizations.save(org);
        return org;
    }

    public List<Organization> getOrganizations() { return organizations.findAll(); }

    /** Ten to chuc de hien thi; "" neu lop khong thuoc to chuc nao. */
    public String organizationName(String id) {
        return organizations.findById(id).map(Organization::getName).orElse("");
    }

    public void deleteOrganization(String id) {
        for (Classroom c : classrooms.findAll()) {
            if (c.getOrganizationId().equals(id)) {
                throw new ApiException(409, "Tổ chức đang có lớp " + c.getName() + ", không xoá được");
            }
        }
        if (!organizations.deleteById(id)) throw ApiException.notFound("Không có tổ chức " + id);
    }

    // ------------------------------------------------------------- lop hoc

    public Classroom createClassroom(String name, String organizationId, String courseId, User teacher) {
        String orgId = organizationId == null ? "" : organizationId.trim();
        if (!orgId.isEmpty() && !organizations.findById(orgId).isPresent()) {
            throw ApiException.badRequest("Không có tổ chức " + orgId);
        }
        Classroom classroom = new Classroom(IdGenerator.next("CLS"), Check.text(name, "Tên lớp", 100),
                orgId, courseId == null ? "" : courseId.trim(), teacher.getId());
        classrooms.save(classroom);
        return classroom;
    }

    public List<Classroom> getClassrooms() { return classrooms.findAll(); }

    public Classroom findClassroom(String id) {
        return classrooms.findById(id == null ? "" : id.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Không có lớp " + id));
    }

    /** Cac lop ma nguoi nay duoc thay: quan tri thay het, nguoi khac chi thay lop minh thuoc ve. */
    public List<Classroom> classroomsOf(User user) {
        List<Classroom> mine = new ArrayList<>();
        for (Classroom c : classrooms.findAll()) {
            if (isAdmin(user) || c.hasMember(user.getId())) mine.add(c);
        }
        return mine;
    }

    /** Viec hang ngay cua lop (bai luyen tap, bai giao, lich hoc, moi hoc sinh): giang vien phu trach va tro giang. */
    public boolean canManage(User user, Classroom classroom) {
        return isAdmin(user) || classroom.isTaughtBy(user.getId());
    }

    /**
     * Viec chi GIANG VIEN PHU TRACH (va quan tri vien) duoc lam: xoa lop, them / go tro giang,
     * xoa hoc sinh khoi lop. Tro giang khong lam duoc cac viec nay.
     */
    public boolean isOwner(User user, Classroom classroom) {
        return isAdmin(user) || classroom.getTeacherId().equals(user.getId());
    }

    public void requireOwner(User user, Classroom classroom) {
        if (!isOwner(user, classroom)) {
            throw ApiException.forbidden("Chỉ giảng viên phụ trách lớp mới làm được việc này");
        }
    }

    /** Ma cua moi hoc sinh trong cac lop nguoi nay day (phu trach hoac tro giang). */
    public java.util.Set<String> studentIdsTaughtBy(String userId) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Classroom c : classrooms.findAll()) {
            if (c.isTaughtBy(userId)) ids.addAll(c.getStudentIds());
        }
        return ids;
    }

    /** Cac lop nguoi nay la giang vien phu trach. */
    public List<Classroom> classroomsLedBy(String userId) {
        List<Classroom> led = new ArrayList<>();
        for (Classroom c : classrooms.findAll()) {
            if (c.getTeacherId().equals(userId)) led.add(c);
        }
        return led;
    }

    /** Go mot tai khoan khoi moi lop (hoc sinh, tro giang, loi moi) - khi tai khoan bi xoa hoac doi vai tro. */
    public synchronized void removeEverywhere(String userId) {
        for (Classroom c : classrooms.findAll()) {
            c.getStudentIds().remove(userId);
            c.getAssistantIds().remove(userId);
            c.getInvitedIds().remove(userId);
            c.getRemovedIds().remove(userId);
        }
    }

    public void requireManager(User user, Classroom classroom) {
        if (!canManage(user, classroom)) {
            throw ApiException.forbidden("Chỉ giảng viên hoặc trợ giảng của lớp mới làm được việc này");
        }
    }

    public void requireMember(User user, Classroom classroom) {
        if (!isAdmin(user) && !classroom.hasMember(user.getId())) {
            throw ApiException.forbidden("Bạn không phải thành viên của " + classroom.getName());
        }
    }

    /** Hoc sinh tu tham gia lop bang ma tham gia do giang vien cung cap. */
    public synchronized Classroom join(String joinCode, User student) {
        String code = joinCode == null ? "" : joinCode.trim().toUpperCase();
        Classroom classroom = null;
        for (Classroom c : classrooms.findAll()) {
            if (c.getJoinCode().equals(code)) classroom = c;
        }
        if (classroom == null) throw ApiException.notFound("Mã tham gia không đúng");
        if (classroom.isTaughtBy(student.getId())) {
            throw ApiException.badRequest("Bạn đang dạy lớp này");
        }
        if (!User.ROLE_STUDENT.equals(student.getRoleCode())) {
            throw ApiException.badRequest("Chỉ tài khoản học sinh mới tham gia lớp bằng mã");
        }
        if (classroom.getRemovedIds().contains(student.getId())) {
            throw ApiException.forbidden("Bạn đã bị xoá khỏi lớp này. Hãy liên hệ giảng viên để được thêm lại");
        }
        classroom.getStudentIds().add(student.getId());
        return classroom;
    }

    /**
     * Giang vien moi hoc sinh vao lop bang ten dang nhap. Hoc sinh chi thanh thanh vien sau khi tu
     * dong y (acceptInvite): khong ai bi dua vao lop - va bi xem phan tich hoc tap - ma khong biet.
     * @return false neu hoc sinh da o trong lop hoac da duoc moi roi
     */
    public synchronized boolean invite(Classroom classroom, User student) {
        if (!User.ROLE_STUDENT.equals(student.getRoleCode())) {
            throw ApiException.badRequest(student.getUsername() + " không phải tài khoản học sinh");
        }
        if (classroom.getStudentIds().contains(student.getId())) return false;
        classroom.getRemovedIds().remove(student.getId());
        return classroom.getInvitedIds().add(student.getId());
    }

    public synchronized void acceptInvite(Classroom classroom, User student) {
        if (!classroom.getInvitedIds().remove(student.getId())) {
            throw ApiException.notFound("Bạn không có lời mời vào lớp này");
        }
        classroom.getStudentIds().add(student.getId());
    }

    public synchronized void declineInvite(Classroom classroom, User student) {
        if (!classroom.getInvitedIds().remove(student.getId())) {
            throw ApiException.notFound("Bạn không có lời mời vào lớp này");
        }
    }

    /** Cac lop dang moi nguoi nay. */
    public List<Classroom> invitesOf(User user) {
        List<Classroom> invites = new ArrayList<>();
        for (Classroom c : classrooms.findAll()) {
            if (c.getInvitedIds().contains(user.getId())) invites.add(c);
        }
        return invites;
    }

    /** Dua thang hoc sinh vao lop, khong qua loi moi - chi dung khi tao du lieu mau. */
    public synchronized void addStudent(Classroom classroom, User student) {
        if (!User.ROLE_STUDENT.equals(student.getRoleCode())) {
            throw ApiException.badRequest(student.getUsername() + " không phải tài khoản học sinh");
        }
        classroom.getRemovedIds().remove(student.getId());
        classroom.getStudentIds().add(student.getId());
    }

    public synchronized void removeStudent(Classroom classroom, String studentId) {
        classroom.getInvitedIds().remove(studentId);       // xoa nguoi chua dong y = rut loi moi
        if (classroom.getStudentIds().remove(studentId)) classroom.getRemovedIds().add(studentId);
    }

    /** Them tro giang: phai la tai khoan giang vien va khong phai giang vien chinh. */
    public synchronized void addAssistant(Classroom classroom, User assistant) {
        if (!User.ROLE_TEACHER.equals(assistant.getRoleCode())) {
            throw ApiException.badRequest("Trợ giảng phải là tài khoản giảng viên");
        }
        if (!assistant.isApproved()) {
            throw ApiException.badRequest("Tài khoản giảng viên này đang chờ quản trị viên duyệt");
        }
        if (classroom.getTeacherId().equals(assistant.getId())) {
            throw ApiException.badRequest("Người này đã là giảng viên phụ trách lớp");
        }
        classroom.getAssistantIds().add(assistant.getId());
    }

    public synchronized void removeAssistant(Classroom classroom, String userId) {
        classroom.getAssistantIds().remove(userId);
    }

    /** Them cac bai luyen tap cho lop; bai da co thi bo qua. */
    public synchronized void addPracticeProblems(Classroom classroom, List<String> problemIds) {
        if (classroom.getPracticeProblemIds().size() + problemIds.size() > 300) {
            throw ApiException.badRequest("Mỗi lớp tối đa 300 bài luyện tập");
        }
        for (String id : problemIds) {
            if (!classroom.getPracticeProblemIds().contains(id)) classroom.getPracticeProblemIds().add(id);
        }
    }

    public synchronized void removePracticeProblem(Classroom classroom, String problemId) {
        classroom.getPracticeProblemIds().remove(problemId == null ? "" : problemId.trim().toUpperCase());
    }

    public void deleteClassroom(String id) {
        classrooms.deleteById(id);
    }

    // ----------------------------------------------------------- phu huynh

    public ParentContact addParent(User student, String fullName, String phone, String email) {
        ParentContact parent = new ParentContact(IdGenerator.next("PAR"), student.getId(),
                Check.text(fullName, "Họ tên phụ huynh", 150),
                Check.text(phone, "Số điện thoại", 20),
                Check.optional(email, "Email", 100));
        parents.save(parent);
        return parent;
    }

    public List<ParentContact> getParents() { return parents.findAll(); }

    public ParentContact findParent(String id) {
        return parents.findById(id).orElseThrow(() -> ApiException.notFound("Không có phụ huynh " + id));
    }

    public void deleteParent(String id) {
        if (!parents.deleteById(id)) throw ApiException.notFound("Không có phụ huynh " + id);
    }

    private boolean isAdmin(User user) {
        return User.ROLE_ADMIN.equals(user.getRoleCode());
    }
}
