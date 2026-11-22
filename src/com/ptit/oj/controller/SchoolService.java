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

    public boolean canManage(User user, Classroom classroom) {
        return isAdmin(user) || classroom.isTaughtBy(user.getId());
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

    /** Hoc sinh tu tham gia lop bang ma lop. */
    public synchronized Classroom join(String classroomId, User student) {
        Classroom classroom = findClassroom(classroomId);
        if (classroom.isTaughtBy(student.getId())) {
            throw ApiException.badRequest("Bạn đang dạy lớp này");
        }
        classroom.getStudentIds().add(student.getId());
        return classroom;
    }

    public synchronized void addStudent(Classroom classroom, User student) {
        if (!User.ROLE_STUDENT.equals(student.getRoleCode())) {
            throw ApiException.badRequest(student.getUsername() + " không phải tài khoản học sinh");
        }
        classroom.getStudentIds().add(student.getId());
    }

    public synchronized void removeStudent(Classroom classroom, String studentId) {
        classroom.getStudentIds().remove(studentId);
    }

    /** Them tro giang: phai la tai khoan giang vien va khong phai giang vien chinh. */
    public synchronized void addAssistant(Classroom classroom, User assistant) {
        if (!User.ROLE_TEACHER.equals(assistant.getRoleCode())) {
            throw ApiException.badRequest("Trợ giảng phải là tài khoản giảng viên");
        }
        if (classroom.getTeacherId().equals(assistant.getId())) {
            throw ApiException.badRequest("Người này đã là giảng viên phụ trách lớp");
        }
        classroom.getAssistantIds().add(assistant.getId());
    }

    public synchronized void removeAssistant(Classroom classroom, String userId) {
        classroom.getAssistantIds().remove(userId);
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
