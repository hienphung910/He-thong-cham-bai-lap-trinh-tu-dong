package com.ptit.oj.controller.api;

import com.ptit.oj.controller.ApiException;
import com.ptit.oj.controller.Check;
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
 * Module 29 (thong bao), 30 (chat), 31 (dien dan):
 *   /api/notifications, /api/announcements, /api/chat/{kenh}, /api/forum
 *
 * Ten kenh chat: "general" (ca he thong), "class-<maLop>", "meeting-<maBuoiHoc>".
 */
public class CommunityApi extends ApiModule {

    private final CommunityService community;

    public CommunityApi(Platform platform) {
        super(platform);
        this.community = platform.getCommunity();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        return handleNotifications(req) || handleChat(req) || handleForum(req) || handleComments(req);
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
        // Giang vien / quan tri gui thong bao: toi mot lop (classroomId) hoac toi tat ca moi nguoi.
        if (req.is("POST", "announcements")) {
            User sender = req.staff();
            Json.Value body = req.body();
            String title = Check.text(body.getString("title"), "Tiêu đề", 150);
            String text = Check.text(body.getString("body"), "Nội dung", 2000);
            String classroomId = body.getString("classroomId", "");

            List<String> receivers = new ArrayList<>();
            if (classroomId.isEmpty()) {
                if (!isAdmin(sender)) throw ApiException.forbidden("Chỉ quản trị viên mới gửi được cho toàn hệ thống");
                for (User u : judge.getUsers()) receivers.add(u.getId());
            } else {
                Classroom classroom = platform.getSchool().findClassroom(classroomId);
                platform.getSchool().requireManager(sender, classroom);
                receivers.addAll(classroom.getStudentIds());
            }
            community.notifyMany(receivers, title, text + " — " + sender.getFullName());
            req.send(Json.obj().put("sent", receivers.size()).build());
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
            List<String> items = new ArrayList<>();
            for (ChatMessage m : community.messages(channel, after)) {
                items.add(Json.obj().put("id", m.getId()).put("senderName", m.getSenderName())
                        .put("senderRole", m.getSenderRole()).put("text", m.getText())
                        .put("sentAt", time(m.getSentAt())).build());
            }
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "chat", "*")) {
            User user = req.user();
            String channel = checkChannel(req.arg(0), user);
            community.postMessage(channel, user, req.body().getString("text"));
            req.sendOk();
            return true;
        }
        return false;
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
     *   GET / POST /api/problems/{maBai}/comments   xem, gui binh luan duoi mot bai
     *   DELETE     /api/comments/{id}               tac gia hoac quan tri vien xoa
     *   GET        /api/admin/comments              quan tri vien: toan bo binh luan, cho duyet truoc
     *   POST       /api/admin/comments/{id}/approve quan tri vien duyet
     */
    private boolean handleComments(Request req) throws IOException {
        if (req.is("GET", "problems", "*", "comments")) {
            User user = req.user();
            Problem problem = visibleProblem(req.arg(0), user);
            List<String> items = new ArrayList<>();
            for (ProblemComment c : community.commentsOf(problem.getId(), user)) items.add(commentJson(c, user, null));
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "problems", "*", "comments")) {
            User user = req.user();
            Problem problem = visibleProblem(req.arg(0), user);
            ProblemComment created = community.addComment(problem.getId(), user, req.body().getString("text"));
            req.send(commentJson(created, user, null));
            return true;
        }
        if (req.is("DELETE", "comments", "*")) {
            User user = req.user();
            ProblemComment comment = community.findComment(req.arg(0));
            boolean own = comment.getAuthorId().equals(user.getId());
            if (!isAdmin(user) && !own) throw ApiException.forbidden("Bạn chỉ xoá được bình luận của chính mình");
            community.deleteComment(comment.getId());
            // Quan tri vien tu choi mot binh luan dang cho duyet: bao cho tac gia biet.
            if (!own && !comment.isApproved()) {
                community.notifyUser(comment.getAuthorId(), "Bình luận không được duyệt",
                        "Bình luận của bạn ở bài " + comment.getProblemId() + " không được quản trị viên duyệt.",
                        "Problems/" + comment.getProblemId());
            }
            req.sendOk();
            return true;
        }
        if (req.is("GET", "admin", "comments")) {
            User admin = req.admin();
            List<String> pending = new ArrayList<>();
            List<String> approved = new ArrayList<>();
            for (ProblemComment c : community.getComments()) {
                String title = judge.findProblem(c.getProblemId()).map(Problem::getTitle).orElse(c.getProblemId());
                (c.isApproved() ? approved : pending).add(commentJson(c, admin, title));
            }
            pending.addAll(approved);
            req.send(Json.array(pending));
            return true;
        }
        if (req.is("POST", "admin", "comments", "*", "approve")) {
            req.admin();
            ProblemComment comment = community.findComment(req.arg(0));
            if (!comment.isApproved()) {
                community.approveComment(comment);
                community.notifyUser(comment.getAuthorId(), "Bình luận đã được duyệt",
                        "Bình luận của bạn ở bài " + comment.getProblemId() + " đã hiện cho mọi người.",
                        "Problems/" + comment.getProblemId());
            }
            req.sendOk();
            return true;
        }
        return false;
    }

    /** Bai trong duong dan, kem kiem tra nguoi goi mo duoc bai do (cung quy tac voi danh sach bai va nop bai). */
    private Problem visibleProblem(String problemId, User user) {
        Problem problem = judge.findProblem(problemId)
                .orElseThrow(() -> ApiException.notFound("Không có bài " + problemId));
        if (!problem.isShared() && !user.canEditProblem(problem)
                && !platform.publishedProblemIds().contains(problem.getId())) {
            throw ApiException.notFound("Không có bài " + problemId);
        }
        return problem;
    }

    /** problemTitle != null: kem ten bai, dung cho trang duyet cua quan tri vien. */
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
                .put("canDelete", isAdmin(viewer) || c.getAuthorId().equals(viewer.getId()));
        if (problemTitle != null) json.put("problemTitle", problemTitle);
        return json.build();
    }

    // ------------------------------------------------------------ dien dan

    private boolean handleForum(Request req) throws IOException {
        if (req.is("GET", "forum")) {
            req.user();
            List<String> items = new ArrayList<>();
            for (ForumPost p : community.getPosts()) items.add(postJson(p, false));
            req.send(Json.array(items));
            return true;
        }
        if (req.is("POST", "forum")) {
            User author = req.user();
            Json.Value body = req.body();
            ForumPost created = community.createPost(author, body.getString("title"),
                    body.getString("body"), body.getString("problemId"));
            req.send(postJson(created, true));
            return true;
        }
        if (req.is("GET", "forum", "*")) {
            req.user();
            req.send(postJson(community.findPost(req.arg(0)), true));
            return true;
        }
        if (req.is("POST", "forum", "*", "replies")) {
            User author = req.user();
            ForumPost post = community.findPost(req.arg(0));
            community.reply(post, author, req.body().getString("body"));
            req.send(postJson(post, true));
            return true;
        }
        // Xoa chu de: chinh tac gia, hoac giang vien / quan tri vien.
        if (req.is("DELETE", "forum", "*")) {
            User user = req.user();
            ForumPost post = community.findPost(req.arg(0));
            if (isStudent(user) && !post.getAuthorId().equals(user.getId())) {
                throw ApiException.forbidden("Bạn chỉ xoá được chủ đề của chính mình");
            }
            community.deletePost(post.getId());
            req.sendOk();
            return true;
        }
        return false;
    }

    private String postJson(ForumPost p, boolean detail) {
        Json.Obj o = Json.obj().put("id", p.getId()).put("title", p.getTitle())
                .put("authorId", p.getAuthorId()).put("authorName", p.getAuthorName())
                .put("problemId", p.getProblemId()).put("createdAt", time(p.getCreatedAt()))
                .put("replyCount", p.getReplies().size());
        if (detail) {
            List<String> replies = new ArrayList<>();
            for (ForumPost.Reply r : p.getReplies()) {
                replies.add(Json.obj().put("authorName", r.getAuthorName()).put("body", r.getBody())
                        .put("createdAt", time(r.getCreatedAt())).build());
            }
            o.put("body", p.getBody()).putRaw("replies", Json.array(replies));
        }
        return o.build();
    }
}
