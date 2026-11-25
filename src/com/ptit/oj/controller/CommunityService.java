package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.ChatMessage;
import com.ptit.oj.model.ForumPost;
import com.ptit.oj.model.Notification;
import com.ptit.oj.model.ProblemComment;
import com.ptit.oj.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thong bao, chat va dien dan (Module 29, 30, 31).
 * Cac ham deu synchronized vi nhieu request doc/ghi cung luc tu cac luong khac nhau.
 */
public class CommunityService {

    private static final int MAX_NOTIFICATIONS_SHOWN = 50;

    private final List<Notification> notifications = new ArrayList<>();
    /** Tran so thong bao va so tin nhan moi kenh giu trong RAM; qua tran thi bo cai cu nhat. */
    private static final int MAX_NOTIFICATIONS = 20_000;
    private static final int MAX_MESSAGES_PER_CHANNEL = 500;

    /** Ten kenh -> cac tin nhan gan nhat theo thu tu gui (toi da MAX_MESSAGES_PER_CHANNEL). */
    private final Map<String, List<ChatMessage>> channels = new HashMap<>();
    /** Ten kenh -> so thu tu cua tin nhan cuoi cung da gui (tang dan, khong giam khi tin cu bi bo). */
    private final Map<String, Long> lastSeq = new HashMap<>();
    private final Repository<ForumPost> posts = new InMemoryRepository<>();
    private final Repository<ProblemComment> comments = new InMemoryRepository<>();
    /** Moi nguoi chi duoc co toi da chung nay binh luan dang cho duyet, de khong lam ngap hang cho cua quan tri vien. */
    private static final int MAX_PENDING_PER_USER = 10;

    // ----------------------------------------------------------- thong bao

    public synchronized void notifyUser(String userId, String title, String body) {
        notifyUser(userId, title, body, "");
    }

    /** link: trang mo ra khi bam vao thong bao (xem Notification.link); "" neu khong co. */
    public synchronized void notifyUser(String userId, String title, String body, String link) {
        notifications.add(new Notification(IdGenerator.next("NTF"), userId, title, body, link));
        if (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.subList(0, notifications.size() - MAX_NOTIFICATIONS).clear();
        }
    }

    public synchronized void notifyMany(Collection<String> userIds, String title, String body) {
        notifyMany(userIds, title, body, "");
    }

    public synchronized void notifyMany(Collection<String> userIds, String title, String body, String link) {
        for (String userId : userIds) notifyUser(userId, title, body, link);
    }

    /** Thong bao cua mot nguoi, moi nhat truoc, toi da 50 dong. */
    public synchronized List<Notification> notificationsOf(String userId) {
        List<Notification> mine = new ArrayList<>();
        for (int i = notifications.size() - 1; i >= 0 && mine.size() < MAX_NOTIFICATIONS_SHOWN; i--) {
            if (notifications.get(i).getUserId().equals(userId)) mine.add(notifications.get(i));
        }
        return mine;
    }

    public synchronized int unreadCount(String userId) {
        int count = 0;
        for (Notification n : notifications) {
            if (n.getUserId().equals(userId) && !n.isRead()) count++;
        }
        return count;
    }

    public synchronized void markAllRead(String userId) {
        for (Notification n : notifications) {
            if (n.getUserId().equals(userId)) n.markRead();
        }
    }

    // ---------------------------------------------------------------- chat

    public synchronized ChatMessage postMessage(String channel, User sender, String text) {
        ChatMessage message = new ChatMessage(IdGenerator.next("MSG"), channel,
                sender.getFullName(), sender.getRole(), Check.text(text, "Tin nhắn", 2000));
        long seq = lastSeq.merge(channel, 1L, Long::sum);
        message.setSeq(seq);
        List<ChatMessage> list = channels.computeIfAbsent(channel, k -> new ArrayList<>());
        list.add(message);
        if (list.size() > MAX_MESSAGES_PER_CHANNEL) list.subList(0, list.size() - MAX_MESSAGES_PER_CHANNEL).clear();
        return message;
    }

    /**
     * Cac tin nhan cua mot kenh co so thu tu LON HON "after". Trinh duyet gui after = so thu tu
     * (seq) cua tin cuoi cung no da nhan, lan dau gui 0 de lay cac tin gan nhat.
     */
    public synchronized List<ChatMessage> messages(String channel, long after) {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage m : channels.getOrDefault(channel, Collections.<ChatMessage>emptyList())) {
            if (m.getSeq() > after) result.add(m);
        }
        return result;
    }

    // ------------------------------------------------- binh luan duoi bai tap

    /**
     * Gui binh luan duoi mot bai. Binh luan cua quan tri vien hien ngay; cua nguoi khac
     * o trang thai cho duyet cho toi khi quan tri vien duyet.
     */
    public synchronized ProblemComment addComment(String problemId, User author, String text) {
        boolean admin = User.ROLE_ADMIN.equals(author.getRoleCode());
        if (!admin) {
            int pending = 0;
            for (ProblemComment c : comments.findAll()) {
                if (!c.isApproved() && c.getAuthorId().equals(author.getId())) pending++;
            }
            if (pending >= MAX_PENDING_PER_USER) {
                throw ApiException.badRequest("Bạn đang có " + pending
                        + " bình luận chờ duyệt. Hãy chờ quản trị viên duyệt rồi gửi tiếp");
            }
        }
        ProblemComment comment = new ProblemComment(IdGenerator.next("CMT"), problemId, author,
                Check.text(text, "Bình luận", 1000), admin ? ProblemComment.APPROVED : ProblemComment.PENDING);
        comments.save(comment);
        return comment;
    }

    /** Binh luan nguoi xem duoc thay duoi mot bai: da duyet, cong voi binh luan cho duyet cua chinh ho. Cu truoc, moi sau. */
    public List<ProblemComment> commentsOf(String problemId, User viewer) {
        List<ProblemComment> result = new ArrayList<>();
        for (ProblemComment c : comments.findAll()) {
            if (!c.getProblemId().equals(problemId)) continue;
            if (c.isApproved() || c.getAuthorId().equals(viewer.getId())) result.add(c);
        }
        return result;
    }

    /** Toan bo binh luan cho trang duyet cua quan tri vien, moi nhat truoc. */
    public List<ProblemComment> getComments() {
        List<ProblemComment> all = comments.findAll();
        Collections.reverse(all);
        return all;
    }

    public ProblemComment findComment(String id) {
        return comments.findById(id).orElseThrow(() -> ApiException.notFound("Không có bình luận " + id));
    }

    public synchronized void approveComment(ProblemComment comment) {
        comment.approve();
    }

    public void deleteComment(String id) {
        comments.deleteById(id);
    }

    // ------------------------------------------------------------ dien dan

    public ForumPost createPost(User author, String title, String body, String problemId) {
        ForumPost post = new ForumPost(IdGenerator.next("POST"),
                Check.text(title, "Tiêu đề", 200), Check.text(body, "Nội dung", 10_000),
                author.getId(), author.getFullName(), problemId == null ? "" : problemId.trim().toUpperCase());
        posts.save(post);
        return post;
    }

    /** Chu de moi nhat dung truoc. */
    public List<ForumPost> getPosts() {
        List<ForumPost> all = posts.findAll();
        Collections.reverse(all);
        return all;
    }

    public ForumPost findPost(String id) {
        return posts.findById(id).orElseThrow(() -> ApiException.notFound("Không có chủ đề " + id));
    }

    public synchronized void reply(ForumPost post, User author, String body) {
        post.getReplies().add(new ForumPost.Reply(author.getFullName(), Check.text(body, "Câu trả lời", 10_000)));
    }

    public void deletePost(String id) {
        posts.deleteById(id);
    }
}
