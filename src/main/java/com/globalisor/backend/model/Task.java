package com.globalisor.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tasks")
@CompoundIndexes({
    @CompoundIndex(name = "task_client_status_idx", def = "{'clientId': 1, 'status': 1}"),
    @CompoundIndex(name = "task_assigned_status_idx", def = "{'assignedTo.id': 1, 'status': 1}")
})
public class Task {

    @Id
    private String id;

    @Indexed
    private String ticketNumber; // e.g. TSK-1001

    @Indexed
    private String clientId;
    private String clientName;
    private String clientEmail;
    
    @Indexed
    private String companyId;
    private String companyName;

    private String title;
    private String description;
    
    // Type: REQUEST, CHANGE, QUERY, COMPLIANCE, GENERAL, INTERNAL
    @Indexed
    private String type; 
    
    // Category: "Registered Address Change", "Director Appointment", "Tax Query", "Share Capital", "Internal Operations", etc.
    @Indexed
    private String category;

    // Scope: "CLIENT" (Task Against Client) or "INTERNAL" (Internal Staff/Management To-Do)
    @Indexed
    private String taskScope;

    @Indexed
    private Boolean isInternal;

    // Priority: LOW, MEDIUM, HIGH, URGENT
    @Indexed
    private String priority;

    // Status: PENDING, ASSIGNED, IN_PROGRESS, WAITING_CLIENT_INPUT, UNDER_REVIEW, COMPLETED, CANCELLED
    @Indexed
    private String status;

    private UserRef assignedTo;
    private UserRef createdBy;

    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @Builder.Default
    private List<ActivityLog> activityLog = new ArrayList<>();

    private String dueDate;
    private Integer slaHours;
    private String resolutionNotes;
    
    // Follow-up Reminder fields
    @Indexed
    private String reminderDateTime; // e.g. "2026-10-21T10:00"
    private String reminderNote; // e.g. "Call client and follow up the document"
    @Indexed
    private String reminderStatus; // PENDING, COMPLETED, RESCHEDULED
    private Long reminderCompletedAt;
    private String reminderCompletedBy;
    private String reminderActionNotes;
    private Long reminderRescheduledAt;
    private String reminderRescheduledBy;

    @Indexed
    private Long createdAt;
    private Long updatedAt;
    private Long resolvedAt;

    @Data
    @Builder
    public static class UserRef {
        private String id;
        private String name;
        private String email;
        private String role; // CLIENT, STAFF, ADMIN
        private String avatar;

        public UserRef() {
        }

        public UserRef(String id, String name, String email, String role, String avatar) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.role = role;
            this.avatar = avatar;
        }
    }

    @Data
    @Builder
    public static class Attachment {
        private String id;
        private String name;
        private String url;
        private String type;
        private Long size;
        private Long uploadedAt;

        public Attachment() {
        }

        public Attachment(String id, String name, String url, String type, Long size, Long uploadedAt) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.type = type;
            this.size = size;
            this.uploadedAt = uploadedAt;
        }
    }

    @Data
    @Builder
    public static class Comment {
        private String id;
        private String authorId;
        private String authorName;
        private String authorRole; // CLIENT, STAFF, ADMIN
        private String authorAvatar;
        private String text;
        private Boolean isInternal; // True if visible only to Staff/Admin
        @Builder.Default
        private List<Attachment> attachments = new ArrayList<>();
        private Long timestamp;

        public Comment() {
        }

        public Comment(String id, String authorId, String authorName, String authorRole, String authorAvatar, String text, Boolean isInternal, List<Attachment> attachments, Long timestamp) {
            this.id = id;
            this.authorId = authorId;
            this.authorName = authorName;
            this.authorRole = authorRole;
            this.authorAvatar = authorAvatar;
            this.text = text;
            this.isInternal = isInternal;
            this.attachments = attachments != null ? attachments : new ArrayList<>();
            this.timestamp = timestamp;
        }
    }

    @Data
    @Builder
    public static class ActivityLog {
        private String id;
        private String action; // CREATED, ASSIGNED, HANDOVER, STATUS_CHANGED, COMMENTED, RESOLVED
        private String details;
        private String performedBy;
        private String performedByRole;
        private Long timestamp;
        private UserRef fromAssignee;
        private UserRef toAssignee;
        private String completedWork;
        private String nextSteps;
        private String previousStatus;
        private String newStatus;

        public ActivityLog() {
        }

        public ActivityLog(String id, String action, String details, String performedBy, String performedByRole, Long timestamp, UserRef fromAssignee, UserRef toAssignee, String completedWork, String nextSteps, String previousStatus, String newStatus) {
            this.id = id;
            this.action = action;
            this.details = details;
            this.performedBy = performedBy;
            this.performedByRole = performedByRole;
            this.timestamp = timestamp;
            this.fromAssignee = fromAssignee;
            this.toAssignee = toAssignee;
            this.completedWork = completedWork;
            this.nextSteps = nextSteps;
            this.previousStatus = previousStatus;
            this.newStatus = newStatus;
        }
    }
}
