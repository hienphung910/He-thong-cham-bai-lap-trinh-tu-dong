package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.MeetingService;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.controller.SchoolService;
import com.ptit.oj.model.Attendance;
import com.ptit.oj.model.ChatMessage;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Meeting;
import com.ptit.oj.model.MeetingRoom;
import com.ptit.oj.model.Recording;
import com.ptit.oj.model.Stroke;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Module 14-20 va 37: buoi hoc, phong hoc truc tuyen (mo phong bang polling),
 * live coding, cung lap trinh, bang trang, gio tay, diem danh, ban ghi.
 *
 *   /api/meetings                      - danh sach, tao, bat dau, ket thuc
 *   /api/meetings/{id}/room            - trang thai phong (trinh duyet hoi lai moi 1.5 giay)
 *   /api/meetings/{id}/room/code|strokes|hand|collab
 *   /api/meetings/{id}/attendance      - diem danh
 *   /api/recordings                    - ban ghi noi dung buoi hoc
 */
public class MeetingApi extends ApiModule {

    private final MeetingService meetings;
    private final SchoolService school;

    public MeetingApi(Platform platform) {
        super(platform);
        this.meetings = platform.getMeetings();
        this.school = platform.getSchool();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        return handleMeetings(req);
    }

    // ------------------------------------------------------- quan ly buoi hoc

    private boolean handleMeetings(Request req) throws IOException {
        if (req.is("GET", "meetings")) {
            User user = req.user();
            Map<String, User> users = usersById();
            List<String> items = new ArrayList<>();
            for (Meeting m : meetings.ofClassrooms(school.classroomsOf(user))) {
                items.add(meetingJson(m, user, users).build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "meetings")) {
            User host = req.staff();
            Json.Value body = req.body();
            Classroom classroom = school.findClassroom(body.getString("classroomId"));
            school.requireManager(host, classroom);
            Meeting created = meetings.schedule(classroom, body.getString("title"),
                    parseTime(body.getString("startAt")), (int) body.getLong("durationMin", 60),
                    host, body.getBoolean("recorded", false));
            // Co the dan link ngay luc len lich, hoac de trong roi dan sau khi toi ngay hoc.
            meetings.setLink(created, body.getString("link", ""));
            platform.getCommunity().notifyMany(classroom.getStudentIds(),
                    "Buổi học mới: " + created.getTitle(),
                    classroom.getName() + " học lúc " + time(created.getStartAt()).replace('T', ' '),
                    "Meetings");
            req.sendOk();
            return true;
        }
        // Giang vien dan link phong hoc vao mot buoi da len lich (gui link rong de go link).
        if (req.is("PUT", "meetings", "*", "link")) {
            Meeting m = managedMeeting(req);
            boolean hadLink = !m.getLink().isEmpty();
            meetings.setLink(m, req.body().getString("link", ""));
            if (!hadLink && !m.getLink().isEmpty()) {
                Classroom classroom = school.findClassroom(m.getClassroomId());
                platform.getCommunity().notifyMany(classroom.getStudentIds(),
                        "Đã có link phòng học: " + m.getTitle(),
                        classroom.getName() + " học lúc " + time(m.getStartAt()).replace('T', ' ')
                                + ". Vào Lịch học để tham gia khi tới giờ.",
                        "Meetings");
            }
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "meetings", "*")) {
            meetings.delete(managedMeeting(req).getId());
            req.sendOk();
            return true;
        }
        return false;
    }

    /** Buoi hoc trong duong dan, kem kiem tra nguoi goi quan ly duoc lop cua buoi hoc do. */
    private Meeting managedMeeting(Request req) {
        User user = req.staff();
        Meeting m = meetings.find(req.arg(0));
        school.requireManager(user, school.findClassroom(m.getClassroomId()));
        return m;
    }

    private Json.Obj meetingJson(Meeting m, User viewer, Map<String, User> users) {
        Classroom classroom = school.findClassroom(m.getClassroomId());
        return Json.obj()
                .put("id", m.getId())
                .put("classroomId", m.getClassroomId())
                .put("classroomName", classroom.getName())
                .put("title", m.getTitle())
                .put("startAt", time(m.getStartAt()))
                .put("durationMin", m.getDurationMin())
                .put("hostName", nameOf(users, m.getHostId()))
                .put("status", m.getStatus())
                .put("recorded", m.isRecorded())
                .put("canManage", school.canManage(viewer, classroom))
                .put("endAt", time(m.getEndAt()))
                .put("phase", m.getPhase())
                .put("hasLink", !m.getLink().isEmpty())
                // Nguoi quan ly lop luon thay link de sua; hoc sinh chi nhan link khi da toi gio hoc.
                .put("link", school.canManage(viewer, classroom) || "OPEN".equals(m.getPhase()) ? m.getLink() : "");
    }

}
