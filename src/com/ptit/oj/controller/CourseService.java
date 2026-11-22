package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Course;
import com.ptit.oj.model.Document;
import com.ptit.oj.model.Lesson;

import java.util.ArrayList;
import java.util.List;

/** Nghiep vu khoa hoc, giao trinh va tai lieu hoc tap (Module 4, 36). */
public class CourseService {

    private final Repository<Course> courses = new InMemoryRepository<>();
    private final Repository<Document> documents = new InMemoryRepository<>();

    // ------------------------------------------------------------ khoa hoc

    public Course createCourse(String title, String description, String level) {
        Course course = new Course(IdGenerator.next("CRS"),
                Check.text(title, "Tên khóa học", 150),
                Check.optional(description, "Mô tả", 1000),
                Check.optional(level, "Trình độ", 50));
        courses.save(course);
        return course;
    }

    public List<Course> getCourses() { return courses.findAll(); }

    public Course findCourse(String id) {
        return courses.findById(id).orElseThrow(() -> ApiException.notFound("Không có khóa học " + id));
    }

    /** Ten khoa hoc de hien thi; "" neu ma rong hoac khoa hoc da bi xoa. */
    public String courseTitle(String id) {
        return courses.findById(id).map(Course::getTitle).orElse("");
    }

    /** Them mot bai giang vao cuoi giao trinh. */
    public synchronized Lesson addLesson(Course course, String title, String content, List<String> problemIds) {
        Lesson lesson = new Lesson(Check.text(title, "Tên bài giảng", 150),
                Check.optional(content, "Nội dung", 20_000), problemIds);
        course.getLessons().add(lesson);
        return lesson;
    }

    public void deleteCourse(String id) {
        if (!courses.deleteById(id)) throw ApiException.notFound("Không có khóa học " + id);
    }

    // ------------------------------------------------------------ tai lieu

    public Document addDocument(String courseId, String title, String kind, String content, String authorName) {
        String cleanKind = Document.KIND_LINK.equals(kind) ? Document.KIND_LINK : Document.KIND_TEXT;
        String cleanContent = Check.text(content, "Nội dung tài liệu", 50_000);
        if (cleanKind.equals(Document.KIND_LINK)
                && !cleanContent.startsWith("http://") && !cleanContent.startsWith("https://")) {
            throw ApiException.badRequest("Đường link phải bắt đầu bằng http:// hoặc https://");
        }
        Document doc = new Document(IdGenerator.next("DOC"), courseId == null ? "" : courseId.trim(),
                Check.text(title, "Tên tài liệu", 150), cleanKind, cleanContent, authorName);
        documents.save(doc);
        return doc;
    }

    /** Tai lieu cua mot khoa hoc; courseId rong = tat ca tai lieu. */
    public List<Document> getDocuments(String courseId) {
        List<Document> result = new ArrayList<>();
        for (Document d : documents.findAll()) {
            if (courseId == null || courseId.isEmpty() || d.getCourseId().equals(courseId)) result.add(d);
        }
        return result;
    }

    public void deleteDocument(String id) {
        if (!documents.deleteById(id)) throw ApiException.notFound("Không có tài liệu " + id);
    }
}
