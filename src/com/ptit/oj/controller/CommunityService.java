package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.ChatMessage;
import com.ptit.oj.model.ForumPost;
import com.ptit.oj.model.Notification;
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
    /** Ten kenh -> danh sach tin nhan theo thu tu gui. */
    private final Map<String, List<ChatMessage>> channels = new HashMap<>();
    private final Repository<ForumPost> posts = new InMemoryRepository<>();

    // ----------------------------------------------------------- thong bao

    public synchronized void notifyUser(String userId, String title, String body) {
        notifications.add(new Notification(IdGenerator.next("NTF"), userId, title, body));
    }

    public synchronized void notifyMany(Collection<String> userIds, String title, String body) {
        for (String userId : userIds) notifyUser(userId, title, body);
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
        channels.computeIfAbsent(channel, k -> new ArrayList<>()).add(message);
        return message;
    }

    /**
     * Tin nhan cua mot kenh, bo qua "after" tin dau tien.
     * Trinh duyet gui after = so tin da co de chi nhan ve phan moi.
     */
    public synchronized List<ChatMessage> messages(String channel, int after) {
        List<ChatMessage> all = channels.getOrDefault(channel, Collections.<ChatMessage>emptyList());
        int from = Math.max(0, Math.min(after, all.size()));
        return new ArrayList<>(all.subList(from, all.size()));
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
