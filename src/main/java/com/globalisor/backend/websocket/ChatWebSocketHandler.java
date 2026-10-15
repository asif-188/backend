package com.globalisor.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalisor.backend.model.User;
import com.globalisor.backend.model.Notification;
import com.globalisor.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Active WebSocket sessions
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Map of userId to sessionIds (since a user can have multiple tabs/connections)
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> userSessions = new ConcurrentHashMap<>();

    // Map of userId to presence status
    private final ConcurrentHashMap<String, String> userPresenceStatus = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private com.globalisor.backend.security.EncryptionUtils encryptionUtils;

    @Autowired
    public ChatWebSocketHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private User resolveUser(String userId, String email) {
        if (userId != null && !userId.trim().isEmpty()) {
            Optional<User> uOpt = userRepository.findById(userId);
            if (uOpt.isPresent()) return uOpt.get();
        }
        String searchEmail = (email != null && !email.trim().isEmpty()) ? email : (userId != null && userId.contains("@") ? userId : null);
        if (searchEmail != null) {
            String normSearch = searchEmail.toLowerCase().trim();
            for (User u : userRepository.findAll()) {
                String uEmail = u.getEmail();
                if (uEmail != null) {
                    if (uEmail.equalsIgnoreCase(normSearch)) return u;
                    if (encryptionUtils != null && (uEmail.endsWith("=") || uEmail.length() > 40)) {
                        try {
                            String dec = encryptionUtils.decryptQueryable(uEmail);
                            if (dec != null && dec.equalsIgnoreCase(normSearch)) return u;
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return null;
    }

    private void registerSessionKey(String key, String sessionId) {
        if (key == null || key.trim().isEmpty() || sessionId == null) return;
        userSessions.computeIfAbsent(key.trim(), k -> new CopyOnWriteArrayList<>()).add(sessionId);
    }

    private void unregisterSessionKey(String key, String sessionId) {
        if (key == null || sessionId == null) return;
        CopyOnWriteArrayList<String> active = userSessions.get(key.trim());
        if (active != null) {
            active.remove(sessionId);
            if (active.isEmpty()) {
                userSessions.remove(key.trim());
            }
        }
    }

    // Helper method to get map of online users
    public boolean isUserOnline(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return false;
        String idKey = identifier.trim();
        CopyOnWriteArrayList<String> active = userSessions.get(idKey);
        if (active != null && !active.isEmpty()) return true;
        active = userSessions.get(idKey.toLowerCase());
        return active != null && !active.isEmpty();
    }

    public String getUserStatus(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return "offline";
        String idKey = identifier.trim();
        String st = userPresenceStatus.get(idKey);
        if (st != null && !"offline".equalsIgnoreCase(st)) return st;
        st = userPresenceStatus.get(idKey.toLowerCase());
        return st != null ? st : "offline";
    }

    public void setUserStatus(String userId, String status) {
        if (status == null || userId == null) return;
        userPresenceStatus.put(userId, status);
        userPresenceStatus.put(userId.toLowerCase(), status);
        User u = resolveUser(userId, null);
        if (u != null) {
            u.setOnlineStatus(status.toUpperCase());
            try {
                userRepository.save(u);
            } catch (Exception ignored) {}
            if (u.getId() != null) userPresenceStatus.put(u.getId(), status);
            String uEmail = u.getEmail();
            if (uEmail != null) userPresenceStatus.put(uEmail.toLowerCase().trim(), status);
        }
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "presence");
            event.put("userId", userId);
            if (u != null && u.getId() != null) event.put("realUserId", u.getId());
            if (u != null && u.getEmail() != null) event.put("email", u.getEmail());
            event.put("status", status);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);

        // Parse query params to identify user: e.g. ws://localhost:8080/api/ws/chat?userId=...&role=...&email=...
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null) {
                String userId = getQueryParam(query, "userId");
                String role = getQueryParam(query, "role");
                String email = getQueryParam(query, "email");
                if (userId != null && !userId.trim().isEmpty()) {
                    session.getAttributes().put("userId", userId);
                    if (role != null) session.getAttributes().put("role", role);
                    if (email != null) session.getAttributes().put("email", email);

                    User user = resolveUser(userId, email);
                    String realId = user != null ? user.getId() : null;
                    String realEmail = user != null ? user.getEmail() : email;
                    if (realEmail != null && encryptionUtils != null && (realEmail.endsWith("=") || realEmail.length() > 40)) {
                        try {
                            String dec = encryptionUtils.decryptQueryable(realEmail);
                            if (dec != null && dec.contains("@")) realEmail = dec;
                        } catch (Exception ignored) {}
                    }
                    String realName = user != null ? ((user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "")).trim() : null;

                    if (realId != null) session.getAttributes().put("realUserId", realId);
                    if (realEmail != null) session.getAttributes().put("realEmail", realEmail);

                    // Register session across all aliases
                    registerSessionKey(userId, session.getId());
                    if (realId != null && !realId.equals(userId)) registerSessionKey(realId, session.getId());
                    if (realEmail != null) registerSessionKey(realEmail.toLowerCase().trim(), session.getId());
                    if (email != null && !email.equalsIgnoreCase(realEmail)) registerSessionKey(email.toLowerCase().trim(), session.getId());

                    // Mark online in presence status
                    userPresenceStatus.put(userId, "online");
                    if (realId != null) userPresenceStatus.put(realId, "online");
                    if (realEmail != null) userPresenceStatus.put(realEmail.toLowerCase().trim(), "online");
                    if (email != null) userPresenceStatus.put(email.toLowerCase().trim(), "online");

                    if (user != null) {
                        try {
                            user.setOnlineStatus("ONLINE");
                            user.setLastSeenTime(System.currentTimeMillis());
                            userRepository.save(user);
                        } catch (Exception ignored) {}
                    }

                    // Send immediate presence snapshot of all active users to this connecting client
                    try {
                        Map<String, Object> snapshot = new ConcurrentHashMap<>();
                        snapshot.put("type", "presence_snapshot");
                        snapshot.put("statuses", userPresenceStatus);
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(snapshot)));
                    } catch (Exception ignored) {}

                    // Broadcast online status to all sessions
                    broadcastPresence(userId, realId, realEmail, realName, true, null);
                }
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error for session {}: {}", session.getId(), exception != null ? exception.getMessage() : "unknown");
        cleanupSession(session);
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        cleanupSession(session);
    }

    private void cleanupSession(WebSocketSession session) {
        if (session == null) return;
        sessions.remove(session.getId());

        String userId = (String) session.getAttributes().get("userId");
        String realId = (String) session.getAttributes().get("realUserId");
        String realEmail = (String) session.getAttributes().get("realEmail");
        String email = (String) session.getAttributes().get("email");

        if (userId != null) unregisterSessionKey(userId, session.getId());
        if (realId != null) unregisterSessionKey(realId, session.getId());
        if (realEmail != null) unregisterSessionKey(realEmail.toLowerCase().trim(), session.getId());
        if (email != null) unregisterSessionKey(email.toLowerCase().trim(), session.getId());

        boolean anyActive = isUserOnline(userId) || (realId != null && isUserOnline(realId)) || (realEmail != null && isUserOnline(realEmail)) || (email != null && isUserOnline(email));
        if (!anyActive) {
            Long lastSeen = System.currentTimeMillis();
            if (userId != null) userPresenceStatus.put(userId, "offline");
            if (realId != null) userPresenceStatus.put(realId, "offline");
            if (realEmail != null) userPresenceStatus.put(realEmail.toLowerCase().trim(), "offline");
            if (email != null) userPresenceStatus.put(email.toLowerCase().trim(), "offline");

            User u = resolveUser(userId, realEmail != null ? realEmail : email);
            if (u != null) {
                try {
                    u.setOnlineStatus("OFFLINE");
                    u.setLastSeenTime(lastSeen);
                    userRepository.save(u);
                } catch (Exception e) {
                    log.warn("Failed to update last seen for user {}: {}", userId, e.getMessage());
                }
            }

            // Broadcast offline status to all sessions
            broadcastPresence(userId, realId, realEmail, null, false, lastSeen);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // Parse message type
        Map<String, Object> data = objectMapper.readValue(payload, Map.class);
        String type = (String) data.get("type");

        if ("ping".equals(type)) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            return;
        } else if ("presence_request".equals(type)) {
            Map<String, Object> snapshot = new ConcurrentHashMap<>();
            snapshot.put("type", "presence_snapshot");
            snapshot.put("statuses", userPresenceStatus);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(snapshot)));
            return;
        } else if ("typing".equals(type)) {
            // Broadcast typing event to all sessions so members of group channels see it
            broadcastAll(payload);
        } else if ("call_signal".equals(type)) {
            String targetUserId = (String) data.get("targetUserId");
            if (targetUserId != null) {
                broadcastToUser(targetUserId, payload);
            }
        } else if ("presence".equals(type) || "status_change".equals(type)) {
            String status = (String) data.get("status");
            String userId = (String) session.getAttributes().get("userId");
            if (userId != null && status != null) {
                setUserStatus(userId, status);
            }
        }
    }

    public void broadcastEvent(Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastPresence(String userId, String realUserId, String email, String name, boolean isOnline, Long lastSeen) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "presence");
            event.put("userId", userId);
            if (realUserId != null) event.put("realUserId", realUserId);
            if (email != null) event.put("email", email);
            if (name != null) event.put("name", name);
            event.put("status", isOnline ? "online" : "offline");
            if (lastSeen != null) {
                event.put("lastSeen", lastSeen);
            }
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    private void broadcastPresence(String userId, boolean isOnline, Long lastSeen) {
        broadcastPresence(userId, null, null, null, isOnline, lastSeen);
    }

    public void broadcastMessageNotification(Object message) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "message");
            event.put("message", message);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastReadReceipt(String clientId, String senderRole) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "read_receipt");
            event.put("clientId", clientId);
            event.put("senderRole", senderRole);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastDocumentSync(Object document) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "document_sync");
            event.put("document", document);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastNotification(Notification notification) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "notification");
            event.put("notification", notification);
            String payload = objectMapper.writeValueAsString(event);
            
            String target = notification.getClientId();
            if ("all".equals(target)) {
                broadcastAll(payload);
            } else if ("admin".equals(target) || "staff".equals(target) || "support".equals(target)) {
                broadcastToRole(payload, "admin", "staff");
            } else if (target != null && target.startsWith("chat_")) {
                String remainder = target.substring("chat_".length());
                for (String uId : userSessions.keySet()) {
                    if (remainder.contains(uId)) {
                        broadcastToUser(uId, payload);
                    }
                }
            } else if (target != null && target.startsWith("team_chat_")) {
                String remainder = target.substring("team_chat_".length());
                boolean delivered = false;
                for (String uId : userSessions.keySet()) {
                    if (remainder.contains(uId)) {
                        broadcastToUser(uId, payload);
                        delivered = true;
                    }
                }
                if (!delivered) {
                    String[] parts = remainder.split("_");
                    for (String part : parts) {
                        broadcastToUser(part, payload);
                    }
                }
            } else if (target != null && (target.startsWith("team_group_") || target.startsWith("group-"))) {
                broadcastAll(payload);
            } else {
                broadcastToUser(target, payload);
                if (target != null && (target.startsWith("C-") || target.length() == 24)) {
                    broadcastToRole(payload, "admin", "staff");
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void broadcastAll(String payload) {
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    cleanupSession(session);
                }
            } else {
                cleanupSession(session);
            }
        }
    }

    private void broadcastToUser(String userId, String payload) {
        CopyOnWriteArrayList<String> active = userSessions.get(userId);
        if (active != null) {
            TextMessage textMessage = new TextMessage(payload);
            for (String sessionId : active) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            cleanupSession(session);
                        }
                    } else {
                        cleanupSession(session);
                    }
                }
            }
        }
    }

    private void broadcastToRole(String payload, String... roles) {
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                String sessionRole = (String) session.getAttributes().get("role");
                if (sessionRole != null) {
                    for (String role : roles) {
                        if (role.equalsIgnoreCase(sessionRole)) {
                            try {
                                session.sendMessage(textMessage);
                            } catch (IOException e) {
                                cleanupSession(session);
                            }
                            break;
                        }
                    }
                }
            } else {
                cleanupSession(session);
            }
        }
    }

    private String getQueryParam(String query, String name) {
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1 && entry[0].equals(name)) {
                return entry[1];
            }
        }
        return null;
    }
}
