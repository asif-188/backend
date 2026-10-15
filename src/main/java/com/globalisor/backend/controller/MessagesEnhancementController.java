package com.globalisor.backend.controller;

import com.globalisor.backend.model.*;
import com.globalisor.backend.repository.*;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessagesEnhancementController {

    @Autowired
    private GroupChatRepository groupChatRepository;

    @Autowired
    private CallHistoryRepository callHistoryRepository;

    @Autowired
    private StarredMessageRepository starredMessageRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired(required = false)
    private com.globalisor.backend.security.EncryptionUtils encryptionUtils;

    // --- Dynamic Group Chats ---

    @GetMapping("/groups")
    public ResponseEntity<List<GroupChat>> getGroups(@RequestParam String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        String role = "CLIENT";
        if (userOpt.isPresent()) {
            role = userOpt.get().getRole();
        }
        final String finalRole = role;
        
        List<GroupChat> allGroups = groupChatRepository.findAll();
        List<GroupChat> filtered = allGroups.stream().filter(g -> {
            if ("ADMIN".equalsIgnoreCase(finalRole)) {
                return true;
            }
            boolean isMember = g.getMemberIds() != null && g.getMemberIds().contains(userId);
            if (isMember) {
                return true;
            }
            String priv = g.getPrivacy() != null ? g.getPrivacy() : "Public";
            if ("STAFF".equalsIgnoreCase(finalRole)) {
                return "Public".equalsIgnoreCase(priv) || "Internal".equalsIgnoreCase(priv);
            }
            // Client/User
            return "Public".equalsIgnoreCase(priv);
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(filtered);
    }

    @PostMapping("/groups")
    public ResponseEntity<GroupChat> createGroup(@RequestBody GroupChat group) {
        if (group.getId() == null) {
            group.setId("group-" + System.currentTimeMillis());
        }
        if (group.getCreatedTime() == null) {
            group.setCreatedTime(System.currentTimeMillis());
        }
        if (group.getPrivacy() == null) {
            group.setPrivacy("Public");
        }
        if (group.getCreatedBy() != null) {
            if (group.getMemberIds() == null) {
                group.setMemberIds(new ArrayList<>());
            }
            if (!group.getMemberIds().contains(group.getCreatedBy())) {
                group.getMemberIds().add(group.getCreatedBy());
            }
            if (group.getAdmins() == null) {
                group.setAdmins(new ArrayList<>());
            }
            if (!group.getAdmins().contains(group.getCreatedBy())) {
                group.getAdmins().add(group.getCreatedBy());
            }
        }
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members about new group creation
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<GroupChat> renameGroup(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<GroupChat> groupOpt = groupChatRepository.findById(id);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GroupChat group = groupOpt.get();
        if (body.containsKey("name")) {
            group.setName((String) body.get("name"));
        }
        if (body.containsKey("description")) {
            group.setDescription((String) body.get("description"));
        }
        if (body.containsKey("icon")) {
            group.setIcon((String) body.get("icon"));
        }
        if (body.containsKey("privacy")) {
            group.setPrivacy((String) body.get("privacy"));
        }
        if (body.containsKey("isArchived")) {
            group.setIsArchived((Boolean) body.get("isArchived"));
        }
        if (body.containsKey("memberIds")) {
            group.setMemberIds((List<String>) body.get("memberIds"));
        }
        if (body.containsKey("admins")) {
            group.setAdmins((List<String>) body.get("admins"));
        }
        if (body.containsKey("pinnedMessages")) {
            group.setPinnedMessages((List<String>) body.get("pinnedMessages"));
        }
        if (body.containsKey("announcements")) {
            group.setAnnouncements((List<String>) body.get("announcements"));
        }
        if (body.containsKey("mutedBy")) {
            group.setMutedBy((List<String>) body.get("mutedBy"));
        }
        if (body.containsKey("starredBy")) {
            group.setStarredBy((List<String>) body.get("starredBy"));
        }
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        Optional<GroupChat> groupOpt = groupChatRepository.findById(id);
        if (groupOpt.isPresent()) {
            groupChatRepository.deleteById(id);
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("type", "group_deleted");
                event.put("groupId", id);
                chatWebSocketHandler.broadcastEvent(event);
            } catch (Exception e) {}
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{id}/members")
    public ResponseEntity<GroupChat> addMembers(@PathVariable String id, @RequestBody Map<String, List<String>> body) {
        Optional<GroupChat> groupOpt = groupChatRepository.findById(id);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GroupChat group = groupOpt.get();
        List<String> userIds = body.get("userIds");
        if (userIds != null) {
            for (String uId : userIds) {
                if (!group.getMemberIds().contains(uId)) {
                    group.getMemberIds().add(uId);
                }
            }
        }
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/groups/{id}/members/{userId}")
    public ResponseEntity<GroupChat> removeMember(@PathVariable String id, @PathVariable String userId) {
        Optional<GroupChat> groupOpt = groupChatRepository.findById(id);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GroupChat group = groupOpt.get();
        group.getMemberIds().remove(userId);
        if (group.getAdmins() != null) {
            group.getAdmins().remove(userId);
        }
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    // --- Call History ---

    @GetMapping("/calls/history")
    public ResponseEntity<List<CallHistory>> getCallHistory(@RequestParam String userId) {
        List<CallHistory> history = callHistoryRepository.findByCallerIdOrReceiverIdOrderByTimestampDesc(userId, userId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/calls")
    public ResponseEntity<CallHistory> logCall(@RequestBody CallHistory call) {
        if (call.getId() == null) {
            call.setId("call-" + System.currentTimeMillis());
        }
        if (call.getTimestamp() == null) {
            call.setTimestamp(System.currentTimeMillis());
        }
        CallHistory saved = callHistoryRepository.save(call);
        return ResponseEntity.ok(saved);
    }

    // --- Starred Messages ---

    @GetMapping("/starred")
    public ResponseEntity<List<Message>> getStarredMessages(@RequestParam String userId) {
        List<StarredMessage> starredLinks = starredMessageRepository.findByUserId(userId);
        List<String> messageIds = starredLinks.stream()
                .map(StarredMessage::getMessageId)
                .collect(Collectors.toList());
        
        Iterable<Message> messages = messageRepository.findAllById(messageIds);
        List<Message> list = new ArrayList<>();
        messages.forEach(list::add);
        list.sort(Comparator.comparingLong(Message::getTimestamp));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/starred/{messageId}")
    public ResponseEntity<?> starMessage(@PathVariable String messageId, @RequestParam String userId) {
        Optional<StarredMessage> existing = starredMessageRepository.findByUserIdAndMessageId(userId, messageId);
        if (existing.isEmpty()) {
            StarredMessage star = new StarredMessage(userId, messageId);
            star.setId("star-" + System.currentTimeMillis());
            starredMessageRepository.save(star);
            
            // Notify other tabs
            broadcastStarredUpdate(userId, messageId, true);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/starred/{messageId}")
    public ResponseEntity<?> unstarMessage(@PathVariable String messageId, @RequestParam String userId) {
        Optional<StarredMessage> existing = starredMessageRepository.findByUserIdAndMessageId(userId, messageId);
        if (existing.isPresent()) {
            starredMessageRepository.delete(existing.get());
            
            // Notify other tabs
            broadcastStarredUpdate(userId, messageId, false);
        }
        return ResponseEntity.ok().build();
    }

    // --- Users directory for Dynamic Groups ---

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> userList = new ArrayList<>();
        
        for (User u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            String fullName = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
            if (fullName.isEmpty()) {
                fullName = u.getCompanyName() != null ? u.getCompanyName() : (u.getEmail() != null ? u.getEmail() : "User");
            }
            map.put("name", fullName);
            map.put("role", u.getRole());
            
            String email = u.getEmail();
            if (email != null && encryptionUtils != null && (email.endsWith("=") || email.length() > 40)) {
                try {
                    String dec = encryptionUtils.decryptQueryable(email);
                    if (dec != null && dec.contains("@")) {
                        email = dec;
                    }
                } catch (Exception ignored) {}
            }
            map.put("email", email);

            boolean online = chatWebSocketHandler.isUserOnline(u.getId()) || (email != null && chatWebSocketHandler.isUserOnline(email.toLowerCase().trim()));
            String status = chatWebSocketHandler.getUserStatus(u.getId());
            if (("offline".equalsIgnoreCase(status) || status == null) && email != null) {
                String stEmail = chatWebSocketHandler.getUserStatus(email.toLowerCase().trim());
                if (stEmail != null && !"offline".equalsIgnoreCase(stEmail)) {
                    status = stEmail;
                }
            }
            if (!online && "ONLINE".equalsIgnoreCase(u.getOnlineStatus())) {
                if (u.getLastSeenTime() != null && System.currentTimeMillis() - u.getLastSeenTime() < 300000) {
                    online = true;
                }
            }
            if ("offline".equalsIgnoreCase(status) && !chatWebSocketHandler.isUserOnline(u.getId()) && (email == null || !chatWebSocketHandler.isUserOnline(email.toLowerCase().trim()))) {
                online = false;
            }
            map.put("isOnline", online && !"offline".equalsIgnoreCase(status));
            map.put("status", status != null && !"offline".equals(status) ? status : (online ? "online" : "offline"));
            map.put("lastSeen", u.getLastSeenTime());
            userList.add(map);
        }
        return ResponseEntity.ok(userList);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        Optional<Message> msgOpt = messageRepository.findById(id);
        if (msgOpt.isPresent()) {
            messageRepository.deleteById(id);
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("type", "message_deleted");
                event.put("messageId", id);
                event.put("clientId", msgOpt.get().getClientId());
                chatWebSocketHandler.broadcastEvent(event);
            } catch (Exception e) {}
        }
        return ResponseEntity.noContent().build();
    }

    private void broadcastGroupUpdate(GroupChat group) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "group_update");
            event.put("group", group);
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {
            // ignore
        }
    }

    private void broadcastStarredUpdate(String userId, String messageId, boolean isStarred) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "starred_update");
            event.put("userId", userId);
            event.put("messageId", messageId);
            event.put("isStarred", isStarred);
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {
            // ignore
        }
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllMessageHistory() {
        messageRepository.deleteAll();
        starredMessageRepository.deleteAll();
        callHistoryRepository.deleteAll();
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "clear_all_messages");
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {}
        Map<String, String> res = new HashMap<>();
        res.put("message", "All message history successfully cleared.");
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<?> clearClientMessages(@RequestParam String clientId) {
        List<Message> msgs = messageRepository.findByClientId(clientId);
        messageRepository.deleteAll(msgs);
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "clear_messages");
            event.put("clientId", clientId);
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {}
        Map<String, String> res = new HashMap<>();
        res.put("message", "Message history for client " + clientId + " cleared.");
        return ResponseEntity.ok(res);
    }
}
