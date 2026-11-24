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
        return handleMeetings(req) || handleRoom(req) || handleAttendance(req) || handleRecordings(req);
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
        if (req.is("POST", "meetings", "*", "start")) {
            meetings.start(managedMeeting(req));
            req.sendOk();
            return true;
        }
        if (req.is("POST", "meetings", "*", "end")) {
            meetings.end(managedMeeting(req));
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

    /** Buoi hoc trong duong dan, kem kiem tra nguoi goi la thanh vien cua lop. */
    private Meeting joinedMeeting(Request req, User user) {
        Meeting m = meetings.find(req.arg(0));
        school.requireMember(user, school.findClassroom(m.getClassroomId()));
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

    // -------------------------------------------------------------- phong hoc

    private boolean handleRoom(Request req) throws IOException {
        // Trinh duyet goi lai endpoint nay lien tuc: vua bao "toi con o day", vua lay trang thai moi.
        if (req.is("GET", "meetings", "*", "room")) {
            User user = req.user();
            Meeting m = joinedMeeting(req, user);
            String json;
            // Khoa chung voi cac ham sua phong cua MeetingService: khong ai sua bang trang
            // trong luc dang doi no thanh JSON.
            synchronized (meetings) {
                MeetingRoom room = meetings.enter(m, user);
                json = roomJson(m, room, user, req.queryInt("strokes", 0), req.queryInt("board", -1));
            }
            req.send(json);
            return true;
        }
        if (req.is("PUT", "meetings", "*", "room", "code")) {
            User user = req.user();
            Meeting m = joinedMeeting(req, user);
            Json.Value body = req.body();
            meetings.updateCode(m, user, body.getString("code"), body.getString("language"));
            req.sendOk();
            return true;
        }
        // Chu phong bat / tat che do "ca lop cung lap trinh".
        if (req.is("POST", "meetings", "*", "room", "collab")) {
            User user = req.user();
            Meeting m = joinedMeeting(req, user);
            if (!m.getHostId().equals(user.getId())) {
                throw ApiException.forbidden("Chỉ chủ phòng mới đổi được chế độ lập trình");
            }
            meetings.setCollaborative(m, req.body().getBoolean("on", false));
            req.sendOk();
            return true;
        }
        if (req.is("POST", "meetings", "*", "room", "strokes")) {
            User user = req.user();
            Meeting m = joinedMeeting(req, user);
            Json.Value body = req.body();
            meetings.addStroke(m, body.getString("color"), body.getString("points"));
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "meetings", "*", "room", "strokes")) {
            meetings.clearBoard(managedMeeting(req));
            req.sendOk();
            return true;
        }
        if (req.is("POST", "meetings", "*", "room", "hand")) {
            User user = req.user();
            meetings.toggleHand(joinedMeeting(req, user), user);
            req.sendOk();
            return true;
        }
        return false;
    }

    /**
     * Trang thai phong gui ve trinh duyet. knownStrokes la so net ve trinh duyet da co:
     * chi gui cac net moi sau do de moi lan hoi khong phai tai lai ca bang.
     */
    private String roomJson(Meeting m, MeetingRoom room, User viewer, int knownStrokes, int knownBoard) {
        Map<String, User> users = usersById();
        List<String> participants = new ArrayList<>();
        for (String userId : meetings.onlineUserIds(room)) {
            User u = users.get(userId);
            if (u == null) continue;
            participants.add(userJson(u)
                    .put("host", m.getHostId().equals(userId))
                    .put("handRaised", room.getRaisedHands().contains(userId))
                    .build());
        }

        List<Stroke> all = room.getStrokes();
        // Bang da bi xoa (trinh duyet dang giu phien ban bang cu, hoac so net tren may chu it hon)
        // -> gui lai tu dau. knownBoard = -1: trinh duyet cu khong gui phien ban, chi dua vao so net.
        boolean cleared = (knownBoard >= 0 && knownBoard != room.getBoardVersion()) || knownStrokes > all.size();
        int from = cleared ? 0 : Math.max(0, knownStrokes);
        return Json.obj()
                .putRaw("meeting", meetingJson(m, viewer, users).build())
                .putRaw("participants", Json.array(participants))
                .put("code", room.getCode())
                .put("language", room.getLanguage())
                .put("codeVersion", room.getCodeVersion())
                .put("collaborative", room.isCollaborative())
                .put("canEditCode", meetings.canEditCode(m, room, viewer))
                .put("isHost", m.getHostId().equals(viewer.getId()))
                .put("handRaised", room.getRaisedHands().contains(viewer.getId()))
                .put("strokesFrom", from)
                .put("strokeCount", all.size())
                .put("boardVersion", room.getBoardVersion())
                .putRaw("strokes", strokesJson(all.subList(from, all.size())))
                .build();
    }

    private String strokesJson(List<Stroke> strokes) {
        List<String> items = new ArrayList<>();
        for (Stroke s : strokes) {
            items.add(Json.obj().put("color", s.getColor()).put("points", s.getPoints()).build());
        }
        return Json.array(items);
    }

    // -------------------------------------------------------------- diem danh

    private boolean handleAttendance(Request req) throws IOException {
        if (req.is("GET", "meetings", "*", "attendance")) {
            Meeting m = managedMeeting(req);
            Classroom classroom = school.findClassroom(m.getClassroomId());
            Map<String, User> users = usersById();
            Map<String, Attendance> marks = meetings.attendanceOf(m.getId());
            List<String> rows = new ArrayList<>();
            for (String studentId : classroom.getStudentIds()) {
                User student = users.get(studentId);
                if (student == null) continue;
                Attendance a = marks.get(studentId);
                // Chua co dong diem danh: buoi hoc da ket thuc thi tinh la vang, chua thi de trong.
                String status = a != null ? a.getStatus()
                        : Meeting.ENDED.equals(m.getStatus()) ? Attendance.ABSENT : "";
                rows.add(userJson(student).put("status", status)
                        .put("joinedAt", a == null ? null : time(a.getJoinedAt())).build());
            }
            req.send(Json.obj().put("title", m.getTitle()).put("status", m.getStatus())
                    .putRaw("rows", Json.array(rows)).build());
            return true;
        }
        if (req.is("PUT", "meetings", "*", "attendance")) {
            Meeting m = managedMeeting(req);
            Json.Value body = req.body();
            String studentId = body.getString("userId");
            if (!school.findClassroom(m.getClassroomId()).getStudentIds().contains(studentId)) {
                throw ApiException.badRequest("Người này không phải học sinh của lớp");
            }
            meetings.mark(m, studentId, body.getString("status"));
            req.sendOk();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- ban ghi

    private boolean handleRecordings(Request req) throws IOException {
        if (req.is("GET", "recordings")) {
            User user = req.user();
            List<String> items = new ArrayList<>();
            for (Recording r : meetings.getRecordings()) {
                if (canWatch(user, r)) items.add(recordingJson(r, false));
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("GET", "recordings", "*")) {
            User user = req.user();
            Recording r = meetings.findRecording(req.arg(0));
            if (!canWatch(user, r)) throw ApiException.forbidden("Bạn không thuộc lớp của bản ghi này");
            req.send(recordingJson(r, true));
            return true;
        }
        return false;
    }

    /** Chi thanh vien cua lop (hoac quan tri vien) moi xem duoc ban ghi cua lop do. */
    private boolean canWatch(User user, Recording r) {
        for (Classroom c : school.classroomsOf(user)) {
            if (c.getId().equals(r.getClassroomId())) return true;
        }
        return false;
    }

    private String recordingJson(Recording r, boolean detail) {
        Json.Obj o = Json.obj()
                .put("id", r.getId())
                .put("meetingId", r.getMeetingId())
                .put("title", r.getTitle())
                .put("durationMin", r.getDurationMin())
                .put("createdAt", time(r.getCreatedAt()))
                .put("strokeCount", r.getStrokes().size())
                .put("chatCount", r.getChat().size());
        if (detail) {
            List<String> chat = new ArrayList<>();
            for (ChatMessage msg : r.getChat()) {
                chat.add(Json.obj().put("senderName", msg.getSenderName()).put("text", msg.getText())
                        .put("sentAt", time(msg.getSentAt())).build());
            }
            o.put("code", r.getCode()).put("language", r.getLanguage())
             .putRaw("strokes", strokesJson(r.getStrokes()))
             .putRaw("chat", Json.array(chat));
        }
        return o.build();
    }
}
