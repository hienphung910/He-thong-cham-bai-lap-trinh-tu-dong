package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Check;
import com.ptit.oj.controller.ContentGuard;
import com.ptit.oj.controller.CommunityService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.ChatMessage;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.ForumPost;
import com.ptit.oj.model.Meeting;
import com.ptit.oj.model.Notification;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.ProblemComment;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 29 (thong bao), 30 (chat) va binh luan duoi bai tap:
 *   /api/notifications, /api/chat/{kenh}, /api/problems/{ma}/comments, /api/comments, /api/admin/comments
 *
 * Ten kenh chat: "general" (ca he thong), "class-<maLop>".
 */
public class CommunityApi extends ApiModule {

    private final CommunityService community;

    public CommunityApi(Platform platform) {
        super(platform);
        this.community = platform.getCommunity();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        return handleNotifications(req) || handleChat(req) || handleComments(req);
    }

    // ----------------------------------------------------------- thong bao

    private boolean handleNotifications(Request req) throws IOException {
        if (req.is("GET", "notifications")) {
            User user = req.user();
            List<String> items = new ArrayList<>();
            for (Notification n : community.notificationsOf(user.getId())) {
                items.add(Json.obj().put("id", n.getId()).put("title", n.getTitle()).put("body", n.getBody())
                        .put("createdAt", time(n.getCreatedAt())).put("read", n.isRead())
                        .put("link", n.getLink()).build());
            }
            req.send(Json.obj().put("unread", community.unreadCount(user.getId()))
                    .putRaw("items", Json.array(items)).build());
            return true;
        }
        if (req.is("POST", "notifications", "read")) {
            community.markAllRead(req.user().getId());
            req.sendOk();
            return true;
        }
        // Xoa het thong bao cua minh; ?only=read thi chi xoa nhung thong bao da doc.
        if (req.is("DELETE", "notifications")) {
            int removed = community.deleteNotifications(req.user().getId(), "read".equals(req.query("only")));
            req.send(Json.obj().put("ok", true).put("removed", removed).build());
            return true;
        }
        if (req.is("POST", "notifications", "*", "read")) {
            community.setRead(req.user().getId(), req.arg(0), true);
            req.sendOk();
            return true;
        }
        if (req.is("POST", "notifications", "*", "unread")) {
            community.setRead(req.user().getId(), req.arg(0), false);
            req.sendOk();
            return true;
        }
        if (req.is("DELETE", "notifications", "*")) {
            community.deleteNotification(req.user().getId(), req.arg(0));
            req.sendOk();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- chat

    private boolean handleChat(Request req) throws IOException {
        if (req.is("GET", "chat", "*")) {
            User user = req.user();
            String channel = checkChannel(req.arg(0), user);
            int after = req.queryInt("after", 0);
            boolean moderator = canModerate(user, channel);
            List<String> items = new ArrayList<>();
            for (ChatMessage m : community.messages(channel, after)) {
                boolean mine = m.getSenderId().equals(user.getId());
                items.add(Json.obj().put("id", m.getId()).put("seq", m.getSeq()).put("senderName", m.getSenderName())
                        .put("senderRole", m.getSenderRole()).put("text", m.getText())
                        .put("sentAt", time(m.getSentAt()))
                        .put("mine", mine).put("canDelete", mine || moderator).build());
            }
            // revision doi = co tin bi xoa: trinh duyet tai lai ca kenh (xem CommunityService.chatRevision).
            req.send(Json.obj().put("revision", community.chatRevision(channel)).put("canModerate", moderator)
                    .putRaw("items", Json.array(items)).build());
            return true;
        }
        // Xoa mot tin: nguoi gui xoa tin cua minh; nguoi quan ly kenh xoa tin bat ky.
        if (req.is("DELETE", "chat", "*", "*")) {
            User user = req.user();
            String channel = checkChannel(req.arg(0), user);
            ChatMessage m = community.findMessage(channel, req.arg(1));
            if (!m.getSenderId().equals(user.getId()) && !canModerate(user, channel)) {
                throw ApiException.forbidden("Bạn chỉ xoá được tin nhắn của chính mình");
            }
            community.deleteMessage(channel, m.getId());
            req.sendOk();
            return true;
        }
        // Xoa toan bo lich su kenh: chi nguoi quan ly kenh.
        if (req.is("DELETE", "chat", "*")) {
            User user = req.user();
            String channel = checkChannel(req.arg(0), user);
            if (!canModerate(user, channel)) {
                throw ApiException.forbidden("Chỉ quản trị viên hoặc giảng viên của lớp mới xoá được lịch sử kênh này");
            }
            int removed = community.clearChannel(channel);
            platform.getSystem().log(user.getUsername(), "Xoá lịch sử chat", channel + " (" + removed + " tin)", true);
            req.send(Json.obj().put("ok", true).put("removed", removed).build());
            return true;
        }
        if (req.is("POST", "chat", "*")) {
            User user = req.user();
            String channel = checkChannel(req.arg(0), user);
            // Moi nguoi toi da 30 tin mot phut: du de tro chuyen, khong du de lam ngap kenh.
            if (platform.getLimiter().retryAfterSeconds("chat:" + user.getId(), 60_000L, 30) > 0) {
                throw new ApiException(429, "Bạn gửi tin quá nhanh. Hãy đợi một chút rồi gửi tiếp");
            }
            platform.getLimiter().hit("chat:" + user.getId(), 60_000L);
            // Cung bo kiem duyet voi binh luan duoi bai: tu bay, link, ma nguon loi giai, spam, tam khoa (ContentGuard).
            String text = req.body().getString("text");
            platform.getGuard().check(user, ContentGuard.Kind.CHAT, channel, text);
            community.postMessage(channel, user, text);
            req.sendOk();
            return true;
        }
        return false;
    }

    /**
     * Ai quan ly duoc mot kenh (xoa tin cua nguoi khac, xoa het lich su): quan tri vien moi kenh; giang
     * vien phu trach lop voi kenh lop va kenh buoi hoc cua lop do. Kenh chung chi quan tri vien.
     */
    private boolean canModerate(User user, String channel) {
        if (User.ROLE_ADMIN.equals(user.getRoleCode())) return true;
        String classroomId = null;
        if (channel.startsWith("class-")) classroomId = channel.substring("class-".length());
        if (channel.startsWith("meeting-")) {
            classroomId = platform.getMeetings().find(channel.substring("meeting-".length())).getClassroomId();
        }
        if (classroomId == null) return false;
        return platform.getSchool().findClassroom(classroomId).isTaughtBy(user.getId());
    }

    /** Kiem tra nguoi dung duoc vao kenh nay: kenh lop / buoi hoc chi danh cho thanh vien cua lop. */
    private String checkChannel(String channel, User user) {
        if (channel.equals("general")) return channel;
        if (channel.startsWith("class-")) {
            Classroom classroom = platform.getSchool().findClassroom(channel.substring("class-".length()));
            platform.getSchool().requireMember(user, classroom);
            return "class-" + classroom.getId();
        }
        if (channel.startsWith("meeting-")) {
            Meeting meeting = platform.getMeetings().find(channel.substring("meeting-".length()));
            platform.getSchool().requireMember(user, platform.getSchool().findClassroom(meeting.getClassroomId()));
            return channel;
        }
        throw ApiException.notFound("Không có kênh chat " + channel);
    }

    // ------------------------------------------------- binh luan duoi bai tap

    /**
     *   GET / POST /api/problems/{maBai}/comments   xem, gui binh luan duoi mot bai (loc tu bay va ma nguon loi giai)
     *   DELETE     /api/comments/{id}               tac gia hoac nguoi quan ly binh luan xoa
     *   GET        /api/admin/comments              cac binh luan minh quan ly duoc, moi nhat truoc
     */
    private boolean handleComments(Request req) throws IOException {
        if (req.is("GET", "problems", "*", "comments")) {
            User user = req.user();
            Problem problem = visibleProblem(req.arg(0), user);
            List<String> items = new ArrayList<>();
            for (ProblemComment c : community.commentsOf(problem.getId())) items.add(commentJson(c, user, null));
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "problems", "*", "comments")) {
            User user = req.user();
            Problem problem = visibleProblem(req.arg(0), user);
            String text = req.body().getString("text");
            // Khong con nguoi duyet: gioi han tan suat va loc noi dung ngay luc gui.
            if (platform.getLimiter().retryAfterSeconds("comment:" + user.getId(), COMMENT_WINDOW_MS, COMMENT_LIMIT) > 0) {
                throw new ApiException(429, "Bạn bình luận quá nhanh. Hãy đợi vài phút rồi gửi tiếp");
            }
            // Giang vien va quan tri vien duoc dua ma va link vao binh luan (goi y, giai thich); hoc sinh thi khong.
            platform.getGuard().check(user, ContentGuard.Kind.COMMENT, problem.getId(), text);
            ProblemComment created = community.addComment(problem.getId(), user, text);
            platform.getLimiter().hit("comment:" + user.getId(), COMMENT_WINDOW_MS);
            req.send(commentJson(created, user, null));
            return true;
        }
        if (req.is("DELETE", "comments", "*")) {
            User user = req.user();
            ProblemComment comment = community.findComment(req.arg(0));
            boolean own = comment.getAuthorId().equals(user.getId());
            if (!own && !canModerate(user, comment)) throw ApiException.forbidden("Bạn chỉ xoá được bình luận của chính mình");
            community.deleteComment(comment.getId());
            // Nguoi quan ly xoa binh luan cua nguoi khac: bao cho tac gia biet.
            if (!own) {
                community.notifyUser(comment.getAuthorId(), "Bình luận của bạn đã bị xoá",
                        "Bình luận của bạn ở bài " + comment.getProblemId() + " đã bị " + user.getFullName() + " xoá.",
                        "Problems/" + comment.getProblemId());
            }
            req.sendOk();
            return true;
        }
        if (req.is("GET", "admin", "comments")) {
            // Quan tri vien: moi binh luan. Giang vien: chi binh luan duoi bai trong kho rieng cua minh.
            User admin = req.staff();
            List<String> items = new ArrayList<>();
            for (ProblemComment c : community.getComments()) {
                if (!canModerate(admin, c)) continue;
                String title = judge.findProblem(c.getProblemId()).map(Problem::getTitle).orElse(c.getProblemId());
                items.add(commentJson(c, admin, title));
            }
            req.send(Json.array(items));
            return true;
        }
        return false;
    }

    /** Bai trong duong dan, kem kiem tra nguoi goi mo duoc bai do (cung quy tac voi danh sach bai va nop bai). */
    private Problem visibleProblem(String problemId, User user) {
        Problem problem = judge.findProblem(problemId)
                .orElseThrow(() -> ApiException.notFound("Không có bài " + problemId));
        if (!platform.accessFor(user).canOpen(problem)) {
            throw ApiException.notFound("Không có bài " + problemId);
        }
        return problem;
    }

    /**
     * Ai xoa duoc binh luan cua nguoi khac: quan tri vien voi moi bai; giang vien voi bai trong KHO RIENG
     * cua minh (binh luan duoi bai kho chung chi quan tri vien xoa).
     */
    private boolean canModerate(User user, ProblemComment comment) {
        if (isAdmin(user)) return true;
        if (isStudent(user)) return false;
        Problem problem = judge.findProblem(comment.getProblemId()).orElse(null);
        return problem != null && !problem.isShared() && user.canEditProblem(problem);
    }

    /** Moi nguoi toi da chung nay binh luan trong 10 phut. */
    private static final int COMMENT_LIMIT = 10;
    private static final long COMMENT_WINDOW_MS = 10 * 60_000L;

    /** problemTitle != null: kem ten bai, dung cho trang quan ly binh luan. */
    private String commentJson(ProblemComment c, User viewer, String problemTitle) {
        Json.Obj json = Json.obj()
                .put("id", c.getId())
                .put("problemId", c.getProblemId())
                .put("authorName", c.getAuthorName())
                .put("authorRole", c.getAuthorRole())
                .put("text", c.getText())
                .put("createdAt", time(c.getCreatedAt()))
                .put("status", c.getStatus())
                .put("mine", c.getAuthorId().equals(viewer.getId()))
                .put("canDelete", c.getAuthorId().equals(viewer.getId()) || canModerate(viewer, c));
        if (problemTitle != null) json.put("problemTitle", problemTitle);
        return json.build();
    }

}
