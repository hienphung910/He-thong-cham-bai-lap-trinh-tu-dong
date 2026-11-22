package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Attendance;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Meeting;
import com.ptit.oj.model.MeetingRoom;
import com.ptit.oj.model.Recording;
import com.ptit.oj.model.Stroke;
import com.ptit.oj.model.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Buoi hoc truc tuyen, phong hoc, diem danh va ban ghi (Module 14-20, 37).
 *
 * Phong hoc la ban MO PHONG chay bang polling: trinh duyet hoi trang thai phong
 * moi 1-2 giay thay vi giu ket noi realtime (HttpServer cua JDK khong co WebSocket).
 */
public class MeetingService {

    /** Qua 10 giay khong hoi trang thai thi coi nhu da roi phong. */
    private static final long ONLINE_WINDOW_MS = 10_000;
    /** Vao phong sau moc bat dau (Meeting.getEffectiveStart) qua 10 phut thi tinh la di muon. */
    private static final long LATE_AFTER_MIN = 10;
    private static final int MAX_STROKES = 2000;
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern POINTS = Pattern.compile("[0-9., ]{1,20000}");

    private final Repository<Meeting> meetings = new InMemoryRepository<>();
    private final Repository<Attendance> attendance = new InMemoryRepository<>();
    private final Repository<Recording> recordings = new InMemoryRepository<>();
    private final Map<String, MeetingRoom> rooms = new HashMap<>();
    private final CommunityService community;

    public MeetingService(CommunityService community) {
        this.community = community;
    }

    // ------------------------------------------------------- quan ly buoi hoc

    public Meeting schedule(Classroom classroom, String title, LocalDateTime startAt,
                            int durationMin, User host, boolean recorded) {
        if (startAt == null) throw ApiException.badRequest("Thiếu thời gian bắt đầu");
        if (durationMin < 5 || durationMin > 600) {
            throw ApiException.badRequest("Thời lượng phải từ 5 đến 600 phút");
        }
        Meeting meeting = new Meeting(IdGenerator.next("MTG"), classroom.getId(),
                Check.text(title, "Tên buổi học", 150), startAt, durationMin, host.getId(), recorded);
        meetings.save(meeting);
        return meeting;
    }

    public Meeting find(String id) {
        return meetings.findById(id).orElseThrow(() -> ApiException.notFound("Không có buổi học " + id));
    }

    /** Buoi hoc cua cac lop cho truoc, buoi moi nhat dung truoc. */
    public List<Meeting> ofClassrooms(List<Classroom> classrooms) {
        List<Meeting> result = new ArrayList<>();
        for (Meeting m : meetings.findAll()) {
            for (Classroom c : classrooms) {
                if (c.getId().equals(m.getClassroomId())) result.add(m);
            }
        }
        result.sort((a, b) -> b.getStartAt().compareTo(a.getStartAt()));
        return result;
    }

    public synchronized void start(Meeting meeting) {
        if (Meeting.ENDED.equals(meeting.getStatus())) {
            throw ApiException.badRequest("Buổi học đã kết thúc");
        }
        if (!meeting.isLive()) meeting.setStartedAt(LocalDateTime.now());
        meeting.setStatus(Meeting.LIVE);
    }

    /** Ket thuc buoi hoc; neu buoi hoc bat ghi thi luu lai noi dung thanh mot ban ghi. */
    public synchronized void end(Meeting meeting) {
        if (!meeting.isLive()) throw ApiException.badRequest("Buổi học chưa bắt đầu hoặc đã kết thúc");
        meeting.setStatus(Meeting.ENDED);
        if (!meeting.isRecorded()) return;

        MeetingRoom room = room(meeting);
        long minutes = Math.max(1, Duration.between(meeting.getStartedAt(), LocalDateTime.now()).toMinutes());
        recordings.save(new Recording(IdGenerator.next("REC"), meeting, minutes,
                room.getCode(), room.getLanguage(), new ArrayList<>(room.getStrokes()),
                community.messages(chatChannel(meeting), 0)));
    }

    public void delete(String id) {
        meetings.deleteById(id);
    }

    /** Ten kenh chat cua phong hoc. */
    public static String chatChannel(Meeting meeting) {
        return "meeting-" + meeting.getId();
    }

    // -------------------------------------------------------------- phong hoc

    public synchronized MeetingRoom room(Meeting meeting) {
        return rooms.computeIfAbsent(meeting.getId(), k -> new MeetingRoom());
    }

    /**
     * Goi moi lan trinh duyet hoi trang thai phong: ghi nhan nguoi nay dang co mat,
     * va diem danh tu dong cho hoc sinh o lan dau vao phong.
     */
    public synchronized MeetingRoom enter(Meeting meeting, User user) {
        if (!meeting.isLive()) throw ApiException.badRequest("Buổi học chưa mở hoặc đã kết thúc");
        MeetingRoom room = room(meeting);
        room.getLastSeen().put(user.getId(), System.currentTimeMillis());

        boolean isStudent = User.ROLE_STUDENT.equals(user.getRoleCode());
        if (isStudent && !attendance.findById(meeting.getId() + ":" + user.getId()).isPresent()) {
            long minutesLate = Duration.between(meeting.getEffectiveStart(), LocalDateTime.now()).toMinutes();
            String status = minutesLate > LATE_AFTER_MIN ? Attendance.LATE : Attendance.PRESENT;
            attendance.save(new Attendance(meeting.getId(), user.getId(), status, LocalDateTime.now()));
        }
        return room;
    }

    /** Ma cua nhung nguoi con dang o trong phong. */
    public synchronized List<String> onlineUserIds(MeetingRoom room) {
        long now = System.currentTimeMillis();
        List<String> online = new ArrayList<>();
        for (Map.Entry<String, Long> e : room.getLastSeen().entrySet()) {
            if (now - e.getValue() <= ONLINE_WINDOW_MS) online.add(e.getKey());
        }
        return online;
    }

    /** Live coding: chi chu phong sua duoc. Collaborative coding: ai trong phong cung sua duoc. */
    public boolean canEditCode(Meeting meeting, MeetingRoom room, User user) {
        return meeting.getHostId().equals(user.getId()) || room.isCollaborative();
    }

    public synchronized void updateCode(Meeting meeting, User user, String code, String language) {
        MeetingRoom room = room(meeting);
        if (!canEditCode(meeting, room, user)) {
            throw ApiException.forbidden("Giảng viên chưa bật chế độ cùng lập trình");
        }
        if (code == null || code.length() > 100_000) throw ApiException.badRequest("Mã nguồn quá dài");
        room.updateCode(code, language == null ? room.getLanguage() : language);
    }

    public synchronized void addStroke(Meeting meeting, String color, String points) {
        if (color == null || !COLOR.matcher(color).matches()
                || points == null || !POINTS.matcher(points).matches()) {
            throw ApiException.badRequest("Nét vẽ không hợp lệ");
        }
        MeetingRoom room = room(meeting);
        if (room.getStrokes().size() >= MAX_STROKES) {
            throw ApiException.badRequest("Bảng đã đầy, hãy xoá bảng để vẽ tiếp");
        }
        room.getStrokes().add(new Stroke(color, points));
    }

    public synchronized void clearBoard(Meeting meeting) {
        room(meeting).getStrokes().clear();
    }

    /** Gio tay / ha tay. */
    public synchronized void toggleHand(Meeting meeting, User user) {
        MeetingRoom room = room(meeting);
        if (!room.getRaisedHands().remove(user.getId())) room.getRaisedHands().add(user.getId());
    }

    // -------------------------------------------------------------- diem danh

    /** Diem danh cua mot buoi hoc: userId -> ban ghi diem danh. */
    public Map<String, Attendance> attendanceOf(String meetingId) {
        Map<String, Attendance> result = new HashMap<>();
        for (Attendance a : attendance.findAll()) {
            if (a.getMeetingId().equals(meetingId)) result.put(a.getUserId(), a);
        }
        return result;
    }

    /** Giang vien diem danh tay (sua ket qua tu dong hoac danh dau vang). */
    public synchronized void mark(Meeting meeting, String userId, String status) {
        if (!Attendance.PRESENT.equals(status) && !Attendance.LATE.equals(status)
                && !Attendance.ABSENT.equals(status)) {
            throw ApiException.badRequest("Trạng thái điểm danh không hợp lệ");
        }
        Attendance current = attendance.findById(meeting.getId() + ":" + userId).orElse(null);
        if (current == null) {
            attendance.save(new Attendance(meeting.getId(), userId, status, null));
        } else {
            current.setStatus(status);
        }
    }

    // ---------------------------------------------------------------- ban ghi

    public List<Recording> getRecordings() { return recordings.findAll(); }

    public Recording findRecording(String id) {
        return recordings.findById(id).orElseThrow(() -> ApiException.notFound("Không có bản ghi " + id));
    }
}
