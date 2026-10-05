package com.globalisor.backend.service;

import com.globalisor.backend.model.Task;
import com.globalisor.backend.repository.TaskRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.repository.OnboardingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private OnboardingRepository onboardingRepository;

    @Autowired
    private NotificationService notificationService;

    @PostConstruct
    public void seedDefaultTasks() {
        if (taskRepository.count() == 0) {
            long now = System.currentTimeMillis();
            
            Task t1 = Task.builder()
                    .ticketNumber("TSK-1001")
                    .clientId("C-1001")
                    .clientName("Ethan Tan")
                    .clientEmail("ethan@lionpath.sg")
                    .companyName("ABBEY HOLDINGS PTE LTD")
                    .title("Change Registered Office Address to Marina Bay")
                    .description("We are relocating our corporate office from Tanjong Pagar to Marina Bay Financial Centre Tower 2. Please prepare directors resolution in writing and lodge with ACRA.")
                    .type("CHANGE")
                    .category("Registered Address Change")
                    .priority("HIGH")
                    .status("IN_PROGRESS")
                    .dueDate("2026-09-20")
                    .assignedTo(Task.UserRef.builder().id("usr-staff").name("Sarah Lim").role("STAFF").avatar("SL").email("staff@globalisor.com").build())
                    .createdBy(Task.UserRef.builder().id("C-1001").name("Ethan Tan").role("CLIENT").build())
                    .createdAt(now - 86400000L * 2)
                    .updatedAt(now - 3600000L * 5)
                    .comments(new ArrayList<>(List.of(
                            Task.Comment.builder()
                                    .id("c-1")
                                    .authorName("Ethan Tan")
                                    .authorRole("CLIENT")
                                    .authorAvatar("ET")
                                    .text("Please find our signed lease agreement attached.")
                                    .timestamp(now - 86400000L)
                                    .isInternal(false)
                                    .build(),
                            Task.Comment.builder()
                                    .id("c-2")
                                    .authorName("Sarah Lim")
                                    .authorRole("STAFF")
                                    .authorAvatar("SL")
                                    .text("Received. Drafted the board resolution and sending for e-signatures.")
                                    .timestamp(now - 3600000L * 5)
                                    .isInternal(false)
                                    .build()
                    )))
                    .activityLog(new ArrayList<>(List.of(
                            Task.ActivityLog.builder().id("act-1").action("CREATED").details("Task created by client").performedBy("Ethan Tan").performedByRole("CLIENT").timestamp(now - 86400000L * 2).build()
                    )))
                    .build();

            Task t2 = Task.builder()
                    .ticketNumber("TSK-1002")
                    .clientId("C-1001")
                    .clientName("Priya Sharma")
                    .clientEmail("priya@merlion.sg")
                    .companyName("ABBEY HOLDINGS PTE LTD")
                    .title("Corporate Income Tax (Form C-S) Estimation Query")
                    .description("We would like to clarify if our recent cross-border SaaS subscription revenue qualifies for the startup tax exemption scheme under YA2026.")
                    .type("QUERY")
                    .category("Tax & Accounting")
                    .priority("MEDIUM")
                    .status("WAITING_CLIENT_INPUT")
                    .dueDate("2026-09-25")
                    .assignedTo(Task.UserRef.builder().id("usr-admin").name("Admin User").role("ADMIN").avatar("AU").email("admin@globalisor.com").build())
                    .createdBy(Task.UserRef.builder().id("C-1001").name("Priya Sharma").role("CLIENT").build())
                    .createdAt(now - 86400000L * 3)
                    .updatedAt(now - 3600000L * 12)
                    .comments(new ArrayList<>(List.of(
                            Task.Comment.builder()
                                    .id("c-3")
                                    .authorName("Admin User")
                                    .authorRole("ADMIN")
                                    .authorAvatar("AU")
                                    .text("To evaluate your startup tax exemption eligibility, could you upload your draft balance sheet and shareholder registry?")
                                    .timestamp(now - 3600000L * 12)
                                    .isInternal(false)
                                    .build()
                    )))
                    .activityLog(new ArrayList<>(List.of(
                            Task.ActivityLog.builder().id("act-2").action("CREATED").details("Query submitted").performedBy("Priya Sharma").performedByRole("CLIENT").timestamp(now - 86400000L * 3).build()
                    )))
                    .build();

            Task t3 = Task.builder()
                    .ticketNumber("TSK-1003")
                    .clientId("C-1001")
                    .clientName("Ravi Kumar")
                    .clientEmail("ravi@harbouredge.sg")
                    .companyName("ABBEY HOLDINGS PTE LTD")
                    .title("Appointment of Additional Resident Director")
                    .description("Urgent request: We are onboarding a new local director (Singapore Citizen). Need Form 45 prepared, identity verified via Singpass KYC, and ACRA notification lodged.")
                    .type("CHANGE")
                    .category("Director / Shareholder Change")
                    .priority("URGENT")
                    .status("PENDING")
                    .dueDate("2026-09-15")
                    .createdBy(Task.UserRef.builder().id("C-1001").name("Ravi Kumar").role("CLIENT").build())
                    .createdAt(now - 3600000L * 4)
                    .updatedAt(now - 3600000L * 4)
                    .comments(new ArrayList<>())
                    .activityLog(new ArrayList<>(List.of(
                            Task.ActivityLog.builder().id("act-3").action("CREATED").details("Urgent appointment request logged").performedBy("Ravi Kumar").performedByRole("CLIENT").timestamp(now - 3600000L * 4).build()
                    )))
                    .build();

            taskRepository.saveAll(List.of(t1, t2, t3));
        } else {
            // Sanitize any existing tasks with placeholder LionPath company name to Abbey Holdings
            List<Task> lionTasks = taskRepository.findByCompanyNameContainingIgnoreCase("LionPath");
            if (!lionTasks.isEmpty()) {
                for (Task t : lionTasks) {
                    t.setCompanyName("ABBEY HOLDINGS PTE LTD");
                }
                taskRepository.saveAll(lionTasks);
            }
        }
    }

    public Task updateTask(String id, Task updated) {
        Task existing = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));

        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getCategory() != null) existing.setCategory(updated.getCategory());
        if (updated.getType() != null) existing.setType(updated.getType());
        if (updated.getTaskScope() != null) existing.setTaskScope(updated.getTaskScope());
        if (updated.getIsInternal() != null) existing.setIsInternal(updated.getIsInternal());
        if (updated.getPriority() != null) existing.setPriority(updated.getPriority());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getCompanyName() != null) existing.setCompanyName(updated.getCompanyName());
        if (updated.getClientName() != null) existing.setClientName(updated.getClientName());
        if (updated.getClientEmail() != null) existing.setClientEmail(updated.getClientEmail());
        if (updated.getDueDate() != null) existing.setDueDate(updated.getDueDate());
        if (updated.getAssignedTo() != null) existing.setAssignedTo(updated.getAssignedTo());
        existing.setUpdatedAt(System.currentTimeMillis());

        return taskRepository.save(existing);
    }

    public List<Task> getAllTasks(String status, String priority, String category, String clientId, String companyName, String assignedToId) {
        return getAllTasks(status, priority, category, clientId, companyName, assignedToId, null, null);
    }

    public List<Task> getAllTasks(String status, String priority, String category, String clientId, String companyName, String assignedToId, String taskScope, Boolean isInternal) {
        return getAllTasks(status, priority, category, clientId, companyName, assignedToId, taskScope, isInternal, null);
    }

    public List<Task> getAllTasks(String status, String priority, String category, String clientId, String companyName, String assignedToId, String taskScope, Boolean isInternal, Integer completedDays) {
        List<Task> all = taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        final Integer days = completedDays;
        final long cutoff = (days != null && days > 0) ? (System.currentTimeMillis() - (days * 86400000L)) : 0L;

        return all.stream().filter(task -> {
            // Filter completed tasks by cutoff time if completedDays > 0
            if (days != null && days > 0) {
                String st = task.getStatus() != null ? task.getStatus().toUpperCase() : "";
                if ("COMPLETED".equals(st) || "RESOLVED".equals(st) || "LODGED".equals(st) || "CANCELLED".equals(st) || "CLOSED".equals(st)) {
                    Long taskTime = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
                    if (taskTime != null && taskTime < cutoff) {
                        return false;
                    }
                }
            }

            boolean isTaskInternal = Boolean.TRUE.equals(task.getIsInternal()) || "INTERNAL".equalsIgnoreCase(task.getTaskScope());

            // Scope / Internal filter
            if (taskScope != null && !taskScope.isEmpty() && !taskScope.equalsIgnoreCase("ALL")) {
                if ("INTERNAL".equalsIgnoreCase(taskScope)) {
                    if (!isTaskInternal) return false;
                } else if ("CLIENT".equalsIgnoreCase(taskScope)) {
                    if (isTaskInternal) return false;
                }
            }
            if (isInternal != null) {
                if (isInternal && !isTaskInternal) return false;
                if (!isInternal && isTaskInternal) return false;
            }

            // If queried specifically by clientId or companyName without requesting internal tasks, strictly hide internal tasks
            boolean clientQuery = (clientId != null && !clientId.isEmpty() && !"ALL".equalsIgnoreCase(clientId)) ||
                                  (companyName != null && !companyName.isEmpty() && !"ALL".equalsIgnoreCase(companyName));
            if (clientQuery && taskScope == null && isInternal == null) {
                if (isTaskInternal) return false;
            }

            if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("ALL") && !status.equalsIgnoreCase(task.getStatus())) {
                return false;
            }
            if (priority != null && !priority.isEmpty() && !priority.equalsIgnoreCase("ALL") && !priority.equalsIgnoreCase(task.getPriority())) {
                return false;
            }
            if (category != null && !category.isEmpty() && !category.equalsIgnoreCase("ALL") && !category.equalsIgnoreCase(task.getCategory())) {
                return false;
            }
            if (clientId != null && !clientId.isEmpty() && !clientId.equalsIgnoreCase("ALL") && !clientId.equalsIgnoreCase(task.getClientId())) {
                return false;
            }
            if (companyName != null && !companyName.isEmpty() && !companyName.equalsIgnoreCase("ALL")) {
                if (task.getCompanyName() == null || !task.getCompanyName().trim().equalsIgnoreCase(companyName.trim())) {
                    return false;
                }
            }
            if (assignedToId != null && !assignedToId.isEmpty()) {
                if (assignedToId.equalsIgnoreCase("UNASSIGNED")) {
                    if (task.getAssignedTo() != null && task.getAssignedTo().getId() != null) return false;
                } else if (task.getAssignedTo() == null || !assignedToId.equals(task.getAssignedTo().getId())) {
                    return false;
                }
            }
            return true;
        }).toList();
    }

    public Optional<Task> getTaskById(String id) {
        return taskRepository.findById(id);
    }

    public Optional<Task> getTaskByTicketNumber(String ticketNumber) {
        return taskRepository.findByTicketNumber(ticketNumber);
    }

    public Task createTask(Task task) {
        long count = taskRepository.count();
        if (task.getTicketNumber() == null || task.getTicketNumber().isEmpty()) {
            task.setTicketNumber("TSK-" + (1000 + count + 1));
        }
        
        long now = System.currentTimeMillis();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        boolean isInternalTask = Boolean.TRUE.equals(task.getIsInternal()) ||
                "INTERNAL".equalsIgnoreCase(task.getTaskScope()) ||
                "INTERNAL".equalsIgnoreCase(task.getType());

        if (isInternalTask) {
            task.setIsInternal(true);
            task.setTaskScope("INTERNAL");
            if (task.getType() == null || task.getType().isEmpty()) {
                task.setType("INTERNAL");
            }
            if (task.getCategory() == null || task.getCategory().isEmpty()) {
                task.setCategory("Internal Operations");
            }
        } else {
            task.setIsInternal(false);
            task.setTaskScope("CLIENT");
            if (task.getType() == null || task.getType().isEmpty()) {
                task.setType("REQUEST");
            }
        }
        
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("PENDING");
        }
        if (task.getPriority() == null || task.getPriority().isEmpty()) {
            task.setPriority("MEDIUM");
        }
        if (task.getAttachments() == null) {
            task.setAttachments(new ArrayList<>());
        }
        if (task.getComments() == null) {
            task.setComments(new ArrayList<>());
        }
        if (task.getActivityLog() == null) {
            task.setActivityLog(new ArrayList<>());
        }

        String creatorName = task.getCreatedBy() != null ? task.getCreatedBy().getName() : "Staff";
        String creatorRole = task.getCreatedBy() != null ? task.getCreatedBy().getRole() : "STAFF";

        String logDetails = isInternalTask
                ? "Internal task created: " + task.getTitle()
                : (creatorRole.equalsIgnoreCase("CLIENT")
                    ? "Task submitted by client: " + task.getTitle()
                    : "Task created against client by " + creatorName + ": " + task.getTitle());

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("CREATED")
                .details(logDetails)
                .performedBy(creatorName)
                .performedByRole(creatorRole)
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        if (isInternalTask) {
            // Notify assigned staff/admin for internal task
            if (task.getAssignedTo() != null && task.getAssignedTo().getId() != null) {
                try {
                    notificationService.sendNotification(
                            task.getAssignedTo().getId(),
                            "New Internal Task: " + task.getTicketNumber(),
                            "You have been assigned an internal to-do: " + task.getTitle(),
                            "TASK_ASSIGNED",
                            saved.getId(),
                            "Info"
                    );
                } catch (Exception ignored) {}
            }
        } else {
            // Notify client for client task
            try {
                if (task.getClientId() != null) {
                    notificationService.sendNotification(
                            task.getClientId(),
                            "Task Raised: " + task.getTicketNumber(),
                            "A task '" + task.getTitle() + "' has been logged for your company.",
                            "TASK_CREATED",
                            saved.getId(),
                            "Info",
                            "/client/portal.html?tab=tasks"
                    );
                }
            } catch (Exception ignored) {}

            // Notify admin & staff for real-time toast notification
            try {
                String clientLabel = saved.getClientName() != null ? saved.getClientName() : "Client";
                if (saved.getCompanyName() != null && !saved.getCompanyName().isEmpty()) {
                    clientLabel += " (" + saved.getCompanyName() + ")";
                }
                notificationService.sendNotification(
                        "admin",
                        "New Task Raised: " + saved.getTicketNumber(),
                        clientLabel + " - " + saved.getTitle(),
                        "TASK_CREATED",
                        saved.getId(),
                        "High",
                        "tasks.html?id=" + saved.getId()
                );
            } catch (Exception ignored) {}
        }

        return saved;
    }

    public Task assignTask(String taskId, Task.UserRef assignee, String performedByName, String performedByRole) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        long now = System.currentTimeMillis();
        task.setAssignedTo(assignee);
        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("ASSIGNED");
        }
        task.setUpdatedAt(now);

        String details = assignee != null && assignee.getName() != null 
                ? "Task assigned to " + assignee.getName() + " (" + assignee.getRole() + ")"
                : "Task unassigned";

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("ASSIGNED")
                .details(details)
                .performedBy(performedByName != null ? performedByName : "Admin")
                .performedByRole(performedByRole != null ? performedByRole : "ADMIN")
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // Notify assigned staff
        if (assignee != null && assignee.getId() != null) {
            try {
                notificationService.sendNotification(
                        assignee.getId(),
                        "New Task Assigned: " + task.getTicketNumber(),
                        "You have been assigned to handle: " + task.getTitle(),
                        "TASK_ASSIGNED",
                        task.getId(),
                        "Warning"
                );
            } catch (Exception ignored) {}
        }

        // Notify client that someone is working on their task
        if (task.getClientId() != null && assignee != null) {
            try {
                notificationService.sendNotification(
                        task.getClientId(),
                        "Task Assigned: " + task.getTicketNumber(),
                        "Your request is now assigned to specialist " + assignee.getName() + ".",
                        "TASK_STATUS",
                        task.getId(),
                        "Info"
                );
            } catch (Exception ignored) {}
        }

        return saved;
    }

    public Task handoverTask(String taskId, Task.UserRef newAssignee, String newStatus, String completedWork, String nextSteps, String performedByName, String performedByRole) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        long now = System.currentTimeMillis();
        Task.UserRef previousAssignee = task.getAssignedTo();
        String previousStatus = task.getStatus();

        // 1. Update Assignee
        task.setAssignedTo(newAssignee);

        // 2. Update Status if specified
        if (newStatus != null && !newStatus.trim().isEmpty()) {
            task.setStatus(newStatus.trim());
            if ("COMPLETED".equalsIgnoreCase(newStatus) || "RESOLVED".equalsIgnoreCase(newStatus)) {
                task.setResolvedAt(now);
            }
        } else if ("PENDING".equalsIgnoreCase(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
        }

        task.setUpdatedAt(now);

        String fromName = previousAssignee != null && previousAssignee.getName() != null ? previousAssignee.getName() : "Unassigned";
        String toName = newAssignee != null && newAssignee.getName() != null ? newAssignee.getName() : "Unassigned";

        StringBuilder logDetails = new StringBuilder();
        logDetails.append("Handover from ").append(fromName).append(" to ").append(toName).append(".");
        if (completedWork != null && !completedWork.trim().isEmpty()) {
            logDetails.append(" Completed: ").append(completedWork.trim()).append(".");
        }
        if (nextSteps != null && !nextSteps.trim().isEmpty()) {
            logDetails.append(" Next: ").append(nextSteps.trim()).append(".");
        }

        if (task.getActivityLog() == null) {
            task.setActivityLog(new ArrayList<>());
        }

        // 3. Add to ActivityLog
        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("HANDOVER")
                .details(logDetails.toString())
                .performedBy(performedByName != null && !performedByName.trim().isEmpty() ? performedByName : fromName)
                .performedByRole(performedByRole != null && !performedByRole.trim().isEmpty() ? performedByRole : "STAFF")
                .timestamp(now)
                .fromAssignee(previousAssignee)
                .toAssignee(newAssignee)
                .completedWork(completedWork)
                .nextSteps(nextSteps)
                .previousStatus(previousStatus)
                .newStatus(task.getStatus())
                .build());

        // 4. Add internal comment trail for seamless team continuity
        if (task.getComments() == null) {
            task.setComments(new ArrayList<>());
        }
        StringBuilder commentText = new StringBuilder();
        commentText.append("🔄 **Task Handover Notice**\n");
        commentText.append("Handed over from **").append(fromName).append("** to **").append(toName).append("**.\n");
        if (completedWork != null && !completedWork.trim().isEmpty()) {
            commentText.append("\n**✓ Work Completed So Far:**\n").append(completedWork.trim()).append("\n");
        }
        if (nextSteps != null && !nextSteps.trim().isEmpty()) {
            commentText.append("\n**→ Next Steps / Pending Action:**\n").append(nextSteps.trim());
        }

        String authorName = (performedByName != null && !performedByName.trim().isEmpty()) ? performedByName : fromName;
        String authorRole = (performedByRole != null && !performedByRole.trim().isEmpty()) ? performedByRole : "STAFF";

        task.getComments().add(Task.Comment.builder()
                .id("cmt-" + UUID.randomUUID())
                .authorId(previousAssignee != null ? previousAssignee.getId() : "usr-staff")
                .authorName(authorName)
                .authorRole(authorRole)
                .authorAvatar(authorName.length() >= 2 ? authorName.substring(0, 2).toUpperCase() : "ST")
                .text(commentText.toString())
                .isInternal(true)
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // 5. Notify the newly assigned colleague
        if (newAssignee != null && newAssignee.getId() != null) {
            try {
                notificationService.sendNotification(
                        newAssignee.getId(),
                        "Task Handover: " + task.getTicketNumber(),
                        fromName + " handed over task '" + task.getTitle() + "' to you. Next action required.",
                        "TASK_ASSIGNED",
                        task.getId(),
                        "Warning"
                );
            } catch (Exception ignored) {}
        }

        // 6. Notify admin
        try {
            notificationService.sendNotification(
                    "admin",
                    "Task Handover: " + task.getTicketNumber(),
                    task.getTitle() + " handed over from " + fromName + " to " + toName,
                    "TASK_HANDOVER",
                    task.getId(),
                    "Info",
                    "tasks.html?id=" + task.getId()
            );
        } catch (Exception ignored) {}

        return saved;
    }

    public Task updateStatus(String taskId, String newStatus, String resolutionNotes, String performedByName, String performedByRole) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        long now = System.currentTimeMillis();
        String oldStatus = task.getStatus();
        task.setStatus(newStatus);
        task.setUpdatedAt(now);

        if ("COMPLETED".equalsIgnoreCase(newStatus) || "RESOLVED".equalsIgnoreCase(newStatus)) {
            task.setResolvedAt(now);
            if (resolutionNotes != null && !resolutionNotes.isEmpty()) {
                task.setResolutionNotes(resolutionNotes);
            }
        }

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("STATUS_CHANGED")
                .details("Status changed from " + oldStatus + " to " + newStatus + (resolutionNotes != null ? ". Notes: " + resolutionNotes : ""))
                .performedBy(performedByName != null ? performedByName : "System")
                .performedByRole(performedByRole != null ? performedByRole : "STAFF")
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // Notify client of status change
        if (task.getClientId() != null) {
            try {
                notificationService.sendNotification(
                        task.getClientId(),
                        "Task Status Updated: " + task.getTicketNumber(),
                        "Status of '" + task.getTitle() + "' is now " + newStatus + ".",
                        "TASK_STATUS",
                        task.getId(),
                        "COMPLETED".equalsIgnoreCase(newStatus) ? "Info" : "Warning"
                );
            } catch (Exception ignored) {}
        }

        return saved;
    }

    public Task addComment(String taskId, Task.Comment comment) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        long now = System.currentTimeMillis();
        if (comment.getId() == null || comment.getId().isEmpty()) {
            comment.setId("cmt-" + UUID.randomUUID());
        }
        comment.setTimestamp(now);
        if (comment.getIsInternal() == null) {
            comment.setIsInternal(false);
        }

        if (task.getComments() == null) {
            task.setComments(new ArrayList<>());
        }
        task.getComments().add(comment);
        task.setUpdatedAt(now);

        // If client commented, set status back to IN_PROGRESS if it was WAITING_CLIENT_INPUT
        if ("CLIENT".equalsIgnoreCase(comment.getAuthorRole()) && "WAITING_CLIENT_INPUT".equalsIgnoreCase(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
        }

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("COMMENTED")
                .details((comment.getIsInternal() ? "[Internal Note] " : "") + comment.getAuthorName() + " replied.")
                .performedBy(comment.getAuthorName())
                .performedByRole(comment.getAuthorRole())
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // Notify relevant party
        try {
            if (!comment.getIsInternal()) {
                if ("CLIENT".equalsIgnoreCase(comment.getAuthorRole())) {
                    if (task.getAssignedTo() != null && task.getAssignedTo().getId() != null) {
                        notificationService.sendNotification(
                                task.getAssignedTo().getId(),
                                "New Client Message: " + task.getTicketNumber(),
                                comment.getAuthorName() + ": " + comment.getText(),
                                "TASK_COMMENT",
                                task.getId(),
                                "Info",
                                "tasks.html?id=" + task.getId()
                        );
                    }
                    notificationService.sendNotification(
                            "admin",
                            "New Client Message: " + task.getTicketNumber(),
                            comment.getAuthorName() + " (" + (task.getCompanyName() != null ? task.getCompanyName() : "Client") + "): " + comment.getText(),
                            "TASK_COMMENT",
                            task.getId(),
                            "Info",
                            "tasks.html?id=" + task.getId()
                    );
                } else {
                    if (task.getClientId() != null) {
                        notificationService.sendNotification(
                                task.getClientId(),
                                "New Update: " + task.getTicketNumber(),
                                comment.getAuthorName() + " responded to your task.",
                                "TASK_COMMENT",
                                task.getId(),
                                "Info"
                        );
                    }
                }
            }
        } catch (Exception ignored) {}

        return saved;
    }

    public Map<String, Object> getTaskStats() {
        long total = taskRepository.count();
        long pending = taskRepository.countByStatusIgnoreCase("PENDING");
        long assigned = taskRepository.countByStatusIgnoreCase("ASSIGNED");
        long inProgress = taskRepository.countByStatusIgnoreCase("IN_PROGRESS");
        long waitingClient = taskRepository.countByStatusIgnoreCase("WAITING_CLIENT_INPUT");
        long completed = taskRepository.countByStatusInIgnoreCase(List.of("COMPLETED", "RESOLVED"));
        long urgent = taskRepository.countByStatusIgnoreCase("URGENT");
        long unassigned = taskRepository.countByAssignedToIsNull();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("assigned", assigned);
        stats.put("inProgress", inProgress);
        stats.put("waitingClient", waitingClient);
        stats.put("completed", completed);
        stats.put("urgent", urgent);
        stats.put("unassigned", unassigned);
        return stats;
    }

    public Map<String, Object> getTaskFormOptions() {
        Map<String, Map<String, Object>> entityMap = new LinkedHashMap<>();

        // 1. Collect entities from Onboarding
        if (onboardingRepository != null) {
            try {
                onboardingRepository.findAll().forEach(o -> {
                    String compName = o.getClientName();
                    // Check if company details are available or client name
                    if (compName != null && !compName.trim().isEmpty() && !"N/A".equalsIgnoreCase(compName.trim())) {
                        String key = compName.trim().toUpperCase();
                        if (!entityMap.containsKey(key)) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("companyId", o.getId() != null ? o.getId() : "COMP-" + o.getClientId());
                            item.put("companyName", compName.trim());
                            item.put("clientId", o.getClientId() != null ? o.getClientId() : "C-1001");
                            item.put("clientName", o.getClientName() != null ? o.getClientName() : "Client User");
                            item.put("clientEmail", o.getClientEmail() != null ? o.getClientEmail() : "client@globalisor.com");
                            entityMap.put(key, item);
                        }
                    }
                });
            } catch (Exception ignored) {}
        }

        // 2. Collect entities from Users
        if (userRepository != null) {
            try {
                userRepository.findAll().forEach(u -> {
                    String compName = u.getCompanyName();
                    if (compName != null && !compName.trim().isEmpty() && !"N/A".equalsIgnoreCase(compName.trim())) {
                        String key = compName.trim().toUpperCase();
                        if (!entityMap.containsKey(key)) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("companyId", "COMP-" + (u.getId() != null ? u.getId() : "101"));
                            item.put("companyName", compName.trim());
                            item.put("clientId", u.getId() != null ? u.getId() : "C-1001");
                            String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
                            item.put("clientName", !name.isEmpty() ? name : "Client User");
                            item.put("clientEmail", u.getEmail() != null ? u.getEmail() : "client@globalisor.com");
                            entityMap.put(key, item);
                        }
                    }
                });
            } catch (Exception ignored) {}
        }

        // 3. Collect entities from existing tasks
        try {
            taskRepository.findAll().forEach(t -> {
                String compName = t.getCompanyName();
                if (compName != null && !compName.trim().isEmpty() && !"N/A".equalsIgnoreCase(compName.trim())) {
                    String key = compName.trim().toUpperCase();
                    if (!entityMap.containsKey(key)) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("companyId", t.getCompanyId() != null ? t.getCompanyId() : "COMP-101");
                        item.put("companyName", compName.trim());
                        item.put("clientId", t.getClientId() != null ? t.getClientId() : "C-1001");
                        item.put("clientName", t.getClientName() != null ? t.getClientName() : "Client User");
                        item.put("clientEmail", t.getClientEmail() != null ? t.getClientEmail() : "client@globalisor.com");
                        entityMap.put(key, item);
                    }
                }
            });
        } catch (Exception ignored) {}

        if (entityMap.isEmpty()) {
            Map<String, Object> defaultComp = new HashMap<>();
            defaultComp.put("companyId", "COMP-101");
            defaultComp.put("companyName", "ABBEY HOLDINGS PTE LTD");
            defaultComp.put("clientId", "C-1001");
            defaultComp.put("clientName", "Ethan Tan");
            defaultComp.put("clientEmail", "ethan@abbey.com");
            entityMap.put("ABBEY HOLDINGS PTE LTD", defaultComp);
        }

        List<Map<String, Object>> companies = new ArrayList<>(entityMap.values());
        companies.sort((a, b) -> ((String) a.get("companyName")).compareToIgnoreCase((String) b.get("companyName")));

        // 4. Collect Specialists / Assignees
        List<Map<String, Object>> assignees = new ArrayList<>();
        if (userRepository != null) {
            try {
                userRepository.findByRoleIn(List.of("STAFF", "ADMIN", "staff", "admin"))
                        .forEach(u -> {
                            String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
                            if (name.isEmpty()) {
                                name = "ADMIN".equalsIgnoreCase(u.getRole()) ? "Admin User" : "Staff Specialist";
                            }
                            String avatar = "ST";
                            if (u.getFirstName() != null && !u.getFirstName().isEmpty()) {
                                avatar = String.valueOf(u.getFirstName().charAt(0)).toUpperCase();
                                if (u.getLastName() != null && !u.getLastName().isEmpty()) {
                                    avatar += String.valueOf(u.getLastName().charAt(0)).toUpperCase();
                                }
                            }
                            Map<String, Object> staff = new HashMap<>();
                            staff.put("id", u.getId());
                            staff.put("name", name);
                            staff.put("role", u.getRole() != null ? u.getRole().toUpperCase() : "STAFF");
                            staff.put("email", u.getEmail() != null ? u.getEmail() : "");
                            staff.put("avatar", avatar);
                            assignees.add(staff);
                        });
            } catch (Exception ignored) {}
        }

        if (assignees.stream().noneMatch(s -> "ADMIN".equalsIgnoreCase((String) s.get("role")) || "usr-admin".equals(s.get("id")))) {
            Map<String, Object> admin = new HashMap<>();
            admin.put("id", "usr-admin");
            admin.put("name", "Admin User");
            admin.put("role", "ADMIN");
            admin.put("email", "admin@globalisor.com");
            admin.put("avatar", "AU");
            assignees.add(0, admin);
        }
        if (assignees.stream().noneMatch(s -> "usr-staff".equals(s.get("id")))) {
            Map<String, Object> staff = new HashMap<>();
            staff.put("id", "usr-staff");
            staff.put("name", "Sarah Lim");
            staff.put("role", "STAFF");
            staff.put("email", "staff@globalisor.com");
            staff.put("avatar", "SL");
            assignees.add(staff);
        }

        // 5. Standard Categories (Client-facing)
        List<String> defaultCategories = List.of(
                "Registered Address Change",
                "Director / Shareholder Change",
                "Share Capital & Allotment",
                "Company Incorporation",
                "Corporate Secretarial",
                "Tax & Accounting Filing",
                "GST & Bookkeeping",
                "Compliance & Annual Return",
                "Bank Account Opening Assistance",
                "Employment Pass & Work Visas",
                "Strike Off & Liquidation",
                "General Operations & Inquiries"
        );
        Set<String> catSet = new LinkedHashSet<>(defaultCategories);
        try {
            taskRepository.findAll().forEach(t -> {
                if (!Boolean.TRUE.equals(t.getIsInternal()) && t.getCategory() != null && !t.getCategory().trim().isEmpty()) {
                    catSet.add(t.getCategory().trim());
                }
            });
        } catch (Exception ignored) {}

        // 6. Internal Categories (Staff / Management To-Dos)
        List<String> internalCategories = List.of(
                "Management Directive & Request",
                "Internal Operations & SOP",
                "Compliance & Due Diligence Audit",
                "Document Filing & Archival",
                "Staff Training & Onboarding",
                "IT & System Administration",
                "Billing & Invoicing Follow-up",
                "General Internal To-Do"
        );

        List<Map<String, Object>> priorities = List.of(
                Map.of("value", "LOW", "label", "Low"),
                Map.of("value", "MEDIUM", "label", "Medium"),
                Map.of("value", "HIGH", "label", "High"),
                Map.of("value", "URGENT", "label", "Urgent")
        );

        Map<String, Object> result = new HashMap<>();
        result.put("companies", companies);
        result.put("assignees", assignees);
        result.put("categories", new ArrayList<>(catSet));
        result.put("internalCategories", internalCategories);
        result.put("priorities", priorities);
        return result;
    }

    public void deleteTask(String id) {
        taskRepository.deleteById(id);
    }
}
