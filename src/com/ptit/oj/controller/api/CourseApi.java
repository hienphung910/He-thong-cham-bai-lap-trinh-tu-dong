package com.ptit.oj.controller.api;

import com.ptit.oj.controller.CourseService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Course;
import com.ptit.oj.model.Document;
import com.ptit.oj.model.Lesson;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 4 (khoa hoc, giao trinh) va 36 (tai lieu):
 *   /api/courses, /api/courses/{id}/lessons, /api/documents
 * Ai dang nhap cung xem duoc; chi giang vien va quan tri vien duoc them / xoa.
 */
public class CourseApi extends ApiModule {

    private final CourseService courses;

    public CourseApi(Platform platform) {
        super(platform);
        this.courses = platform.getCourses();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "courses")) {
            req.user();
            List<String> items = new ArrayList<>();
            for (Course c : courses.getCourses()) items.add(courseJson(c));
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "courses")) {
            req.staff();
            Json.Value body = req.body();
            Course created = courses.createCourse(body.getString("title"),
                    body.getString("description"), body.getString("level"));
            req.send(courseJson(created));
            return true;
        }
        if (req.is("DELETE", "courses", "*")) {
            req.staff();
            courses.deleteCourse(req.arg(0));
            req.sendOk();
            return true;
        }
        if (req.is("POST", "courses", "*", "lessons")) {
            User user = req.staff();
            Json.Value body = req.body();
            Course course = courses.findCourse(req.arg(0));
            courses.addLesson(course, body.getString("title"), body.getString("content"),
                    problemIds(body, "problemIds", user));
            req.send(courseJson(course));
            return true;
        }

        if (req.is("GET", "documents")) {
            req.user();
            List<String> items = new ArrayList<>();
            for (Document d : courses.getDocuments(req.query("courseId"))) {
                items.add(Json.obj().put("id", d.getId()).put("courseId", d.getCourseId())
                        .put("courseTitle", courses.courseTitle(d.getCourseId()))
                        .put("title", d.getTitle()).put("kind", d.getKind()).put("content", d.getContent())
                        .put("authorName", d.getAuthorName()).put("createdAt", time(d.getCreatedAt()))
                        .build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "documents")) {
            User author = req.staff();
            Json.Value body = req.body();
            courses.addDocument(body.getString("courseId"), body.getString("title"),
                    body.getString("kind"), body.getString("content"), author.getFullName());
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "documents", "*")) {
            req.staff();
            courses.deleteDocument(req.arg(0));
            req.sendOk();
            return true;
        }
        return false;
    }

    private String courseJson(Course c) {
        List<String> lessons = new ArrayList<>();
        for (Lesson l : c.getLessons()) {
            lessons.add(Json.obj().put("title", l.getTitle()).put("content", l.getContent())
                    .putRaw("problems", problemsJson(l.getProblemIds())).build());
        }
        return Json.obj().put("id", c.getId()).put("title", c.getTitle())
                .put("description", c.getDescription()).put("level", c.getLevel())
                .putRaw("lessons", Json.array(lessons)).build();
    }
}
