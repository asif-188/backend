package com.globalisor.backend.service;

import com.globalisor.backend.model.Notification;
import com.globalisor.backend.repository.NotificationRepository;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    // In-memory debounce cache to prevent duplicate notifications within 10 seconds
    private final Map<String, Long> recentNotifications = new ConcurrentHashMap<>();

    public void sendNotification(String targetClientId, String title, String message, String type, String relatedId, String priority) {
        sendNotification(targetClientId, title, message, type, relatedId, priority, null);
    }

    public void sendNotification(String targetClientId, String title, String message, String type, String relatedId, String priority, String link) {
        long now = System.currentTimeMillis();

        // Clean up debounce entries older than 30 seconds
        recentNotifications.entrySet().removeIf(entry -> now - entry.getValue() > 30000L);

        // Normalize target: "admin" and "staff" unified operations target to "staff-admin"
        String normalizedTarget = targetClientId;
        if ("admin".equalsIgnoreCase(targetClientId) || "staff".equalsIgnoreCase(targetClientId)) {
            normalizedTarget = "staff-admin";
        }

        String debounceKey = (normalizedTarget != null ? normalizedTarget : "") + "::" +
                             (title != null ? title.trim() : "") + "::" +
                             (message != null ? message.trim() : "") + "::" +
                             (relatedId != null ? relatedId.trim() : "");

        Long lastSent = recentNotifications.put(debounceKey, now);
        if (lastSent != null && (now - lastSent) < 10000L) {
            // Duplicate notification within 10 seconds, skip creating duplicate record
            return;
        }

        // Single Notification per Task in Admin: deduplicate so there is only one notification per task
        boolean isTaskNotif = (type != null && type.toLowerCase().contains("task")) || 
                             (relatedId != null && (relatedId.startsWith("task-") || relatedId.toLowerCase().contains("task")));
        if (isTaskNotif && relatedId != null && !relatedId.trim().isEmpty() && "staff-admin".equalsIgnoreCase(normalizedTarget)) {
            List<Notification> existingList = notificationRepository.findByClientIdAndRelatedId("staff-admin", relatedId);
            if (existingList != null && !existingList.isEmpty()) {
                Notification existing = existingList.get(0);
                existing.setTitle(title);
                existing.setMessage(message);
                existing.setType(type);
                existing.setPriority(priority != null ? priority : existing.getPriority());
                if (link != null) existing.setLink(link);
                existing.setTimestamp(now);
                existing.setReadBy(new ArrayList<>());
                notificationRepository.save(existing);
                for (int i = 1; i < existingList.size(); i++) {
                    try { notificationRepository.delete(existingList.get(i)); } catch (Exception ignored) {}
                }
                chatWebSocketHandler.broadcastNotification(existing);
                return;
            }
        }

        Notification notif = new Notification();
        notif.setId("notif-" + now);
        notif.setClientId(normalizedTarget);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setType(type);
        notif.setRelatedId(relatedId);
        notif.setLink(link);
        notif.setPriority(priority != null ? priority : "Info");
        notif.setTimestamp(now);
        notif.setReadBy(new ArrayList<>());
        
        notificationRepository.save(notif);
        chatWebSocketHandler.broadcastNotification(notif);
    }
}
