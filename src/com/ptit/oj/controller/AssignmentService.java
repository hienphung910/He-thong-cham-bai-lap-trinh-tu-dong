package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Assignment;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Nghiep vu giao bai tap cho lop (Module 13).
 * Tien do cua hoc sinh KHONG luu rieng: tinh tu lich su nop bai (bai nao da AC).
 */
public class AssignmentService {

    private final Repository<Assignment> assignments = new InMemoryRepository<>();

    public Assignment create(Classroom classroom, String title, String description,
                             List<String> problemIds, LocalDateTime dueAt, User creator) {
        if (problemIds.isEmpty()) throw ApiException.badRequest("Bài tập giao phải có ít nhất một bài");
        if (dueAt == null) throw ApiException.badRequest("Thiếu hạn nộp");
        Assignment assignment = new Assignment(IdGenerator.next("ASG"), classroom.getId(),
                Check.text(title, "Tên bài tập giao", 150),
                Check.optional(description, "Mô tả", 2000),
                problemIds, dueAt, creator.getFullName());
        assignments.save(assignment);
        return assignment;
    }

    public Assignment find(String id) {
        return assignments.findById(id).orElseThrow(() -> ApiException.notFound("Không có bài tập giao " + id));
    }

    /** Bai tap giao cua cac lop cho truoc, han nop gan nhat dung truoc. */
    public List<Assignment> ofClassrooms(List<Classroom> classrooms) {
        List<Assignment> result = new ArrayList<>();
        for (Assignment a : assignments.findAll()) {
            for (Classroom c : classrooms) {
                if (c.getId().equals(a.getClassroomId())) result.add(a);
            }
        }
        result.sort((a, b) -> a.getDueAt().compareTo(b.getDueAt()));
        return result;
    }

    public void delete(String id) {
        assignments.deleteById(id);
    }
}
