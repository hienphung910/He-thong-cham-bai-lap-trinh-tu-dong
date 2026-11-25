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
 *  - quan ly lop = quan tri vien hoac giang vien phu trach lop do,
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

    /** Quan ly lop (bai luyen tap, bai giao, lich hoc, moi hoc sinh...): giang vien phu trach va quan tri vien. */
    public boolean canManage(User user, Classroom classroom) {
        return isAdmin(user) || classroom.isTaughtBy(user.getId());
    }

    /**
     * Giang vien phu trach lop (hoac quan tri vien). Hien trung voi canManage vi lop chi con mot
     * giang vien; giu ten rieng cho cac viec khong the hoan tac: xoa lop, xoa hoc sinh khoi lop.
     */
    public boolean isOwner(User user, Classroom classroom) {
        return isAdmin(user) || classroom.getTeacherId().equals(user.getId());
    }

    public void requireOwner(User user, Classroom classroom) {
        if (!isOwner(user, classroom)) {
            throw ApiException.forbidden("Chỉ giảng viên phụ trách lớp mới làm được việc này");
        }
    }

    /** Ma cua moi hoc sinh trong cac lop nguoi nay phu trach. */
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

    /** Go mot tai khoan khoi moi lop (hoc sinh, loi moi) - khi tai khoan bi xoa hoac doi vai tro. */
    public synchronized void removeEverywhere(String userId) {
        for (Classroom c : classrooms.findAll()) {
            c.getStudentIds().remove(userId);
            c.getInvitedIds().remove(userId);
            c.getPendingIds().remove(userId);
            c.getRemovedIds().remove(userId);
        }
    }

    public void requireManager(User user, Classroom classroom) {
        if (!canManage(user, classroom)) {
            throw ApiException.forbidden("Chỉ giảng viên phụ trách lớp mới làm được việc này");
        }
    }

    public void requireMember(User user, Classroom classroom) {
        if (!isAdmin(user) && !classroom.hasMember(user.getId())) {
            throw ApiException.forbidden("Bạn không phải thành viên của " + classroom.getName());
        }
    }

    /** So yeu cau vao lop dang cho duyet toi da cua mot lop / cua mot hoc sinh: chan viec gui yeu cau hang loat. */
    private static final int MAX_PENDING_PER_CLASS = 200;
    private static final int MAX_PENDING_PER_STUDENT = 10;

    /**
     * Hoc sinh xin vao lop bang ma tham gia do giang vien cung cap. Chi tao YEU CAU cho giang vien
     * duyet (approveRequest); rieng hoc sinh da duoc giang vien moi san thi vao lop luon, vi loi moi
     * chinh la su dong y cua giang vien. Nguoi goi xem classroom.getStudentIds() de biet da vao hay con cho.
     */
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
        if (classroom.getStudentIds().contains(student.getId())) {
            throw ApiException.badRequest("Bạn đã ở trong lớp " + classroom.getName());
        }
        if (classroom.getInvitedIds().remove(student.getId())) {
            classroom.getStudentIds().add(student.getId());
            return classroom;
        }
        if (classroom.getPendingIds().contains(student.getId())) {
            throw ApiException.badRequest("Bạn đã gửi yêu cầu vào lớp " + classroom.getName() + ", hãy chờ giảng viên duyệt");
        }
        if (classroom.getPendingIds().size() >= MAX_PENDING_PER_CLASS) {
            throw new ApiException(409, "Lớp này đang có quá nhiều yêu cầu chờ duyệt. Hãy thử lại sau");
        }
        if (requestsOf(student).size() >= MAX_PENDING_PER_STUDENT) {
            throw new ApiException(409, "Bạn đang có quá nhiều yêu cầu vào lớp chờ duyệt. Hãy huỷ bớt hoặc chờ giảng viên duyệt");
        }
        classroom.getPendingIds().add(student.getId());
        return classroom;
    }

    /** Giang vien duyet yeu cau vao lop: hoc sinh thanh thanh vien. */
    public synchronized void approveRequest(Classroom classroom, String studentId) {
        if (!classroom.getPendingIds().remove(studentId)) {
            throw ApiException.notFound("Không có yêu cầu vào lớp này");
        }
        classroom.getStudentIds().add(studentId);
    }

    /** Giang vien tu choi yeu cau, hoac hoc sinh tu huy yeu cau cua minh. Hoc sinh gui lai duoc. */
    public synchronized void dropRequest(Classroom classroom, String studentId) {
        if (!classroom.getPendingIds().remove(studentId)) {
            throw ApiException.notFound("Không có yêu cầu vào lớp này");
        }
    }

    /** Cac lop nguoi nay dang xin vao va chua duoc duyet. */
    public List<Classroom> requestsOf(User user) {
        List<Classroom> pending = new ArrayList<>();
        for (Classroom c : classrooms.findAll()) {
            if (c.getPendingIds().contains(user.getId())) pending.add(c);
        }
        return pending;
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
        if (classroom.getPendingIds().contains(student.getId())) {
            throw ApiException.badRequest(student.getUsername() + " đang xin vào lớp này. Hãy duyệt ở mục Yêu cầu vào lớp");
        }
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
        classroom.getPendingIds().remove(studentId);
        if (classroom.getStudentIds().remove(studentId)) classroom.getRemovedIds().add(studentId);
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

    /** Nap lai to chuc va lop hoc da luu (StateStore) - chi goi luc khoi dong. */
    synchronized void restore(List<Organization> savedOrganizations, List<Classroom> savedClassrooms) {
        for (Organization o : savedOrganizations) organizations.save(o);
        for (Classroom c : savedClassrooms) classrooms.save(c);
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
