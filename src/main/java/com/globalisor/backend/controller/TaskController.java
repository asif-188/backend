package com.globalisor.backend.controller;

import com.globalisor.backend.model.Task;
import com.globalisor.backend.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping
    public ResponseEntity<List<Task>> getAllTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String assignedToId,
            @RequestParam(required = false) String taskScope,
            @RequestParam(required = false) Boolean isInternal,
            @RequestParam(required = false) Integer completedDays
    ) {
        return ResponseEntity.ok(taskService.getAllTasks(status, priority, category, clientId, companyName, assignedToId, taskScope, isInternal, completedDays));
    }

    @GetMapping("/form-options")
    public ResponseEntity<Map<String, Object>> getTaskFormOptions() {
        return ResponseEntity.ok(taskService.getTaskFormOptions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable String id) {
        return taskService.getTaskById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ticket/{ticketNumber}")
    public ResponseEntity<Task> getTaskByTicketNumber(@PathVariable String ticketNumber) {
        return taskService.getTaskByTicketNumber(ticketNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        Task created = taskService.createTask(task);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable String id, @RequestBody Task task) {
        Task updated = taskService.updateTask(id, task);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<Task> assignTask(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        Task.UserRef assignee = null;
        if (payload.containsKey("assignee") && payload.get("assignee") != null) {
            Map<String, Object> map = (Map<String, Object>) payload.get("assignee");
            assignee = Task.UserRef.builder()
                    .id((String) map.get("id"))
                    .name((String) map.get("name"))
                    .email((String) map.get("email"))
                    .role((String) map.get("role"))
                    .avatar((String) map.get("avatar"))
                    .build();
        }
        String performedBy = (String) payload.getOrDefault("performedBy", "Admin");
        String performedByRole = (String) payload.getOrDefault("performedByRole", "ADMIN");

        Task updated = taskService.assignTask(id, assignee, performedBy, performedByRole);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/handover")
    public ResponseEntity<Task> handoverTask(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        Task.UserRef assignee = null;
        if (payload.containsKey("assignee") && payload.get("assignee") != null) {
            Map<String, Object> map = (Map<String, Object>) payload.get("assignee");
            assignee = Task.UserRef.builder()
                    .id((String) map.get("id"))
                    .name((String) map.get("name"))
                    .email((String) map.get("email"))
                    .role((String) map.get("role"))
                    .avatar((String) map.get("avatar"))
                    .build();
        }
        String status = (String) payload.get("status");
        String completedWork = (String) payload.get("completedWork");
        String nextSteps = (String) payload.get("nextSteps");
        String performedBy = (String) payload.getOrDefault("performedBy", "Staff");
        String performedByRole = (String) payload.getOrDefault("performedByRole", "STAFF");

        Task updated = taskService.handoverTask(id, assignee, status, completedWork, nextSteps, performedBy, performedByRole);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Task> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        String status = (String) payload.get("status");
        String resolutionNotes = (String) payload.get("resolutionNotes");
        String performedBy = (String) payload.getOrDefault("performedBy", "Staff");
        String performedByRole = (String) payload.getOrDefault("performedByRole", "STAFF");

        Task updated = taskService.updateStatus(id, status, resolutionNotes, performedBy, performedByRole);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/priority")
    public ResponseEntity<Task> updatePriority(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        String priority = (String) payload.get("priority");
        String performedBy = (String) payload.getOrDefault("performedBy", "Staff Specialist");
        String performedByRole = (String) payload.getOrDefault("performedByRole", "STAFF");

        Task updated = taskService.updatePriority(id, priority, performedBy, performedByRole);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Task> addComment(
            @PathVariable String id,
            @RequestBody Task.Comment comment
    ) {
        Task updated = taskService.addComment(id, comment);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTaskStats() {
        return ResponseEntity.ok(taskService.getTaskStats());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reminder")
    public ResponseEntity<Task> setReminder(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        String reminderDateTime = (String) payload.get("reminderDateTime");
        String reminderNote = (String) payload.get("reminderNote");
        String performedBy = (String) payload.getOrDefault("performedBy", "Staff");
        String performedByRole = (String) payload.getOrDefault("performedByRole", "STAFF");

        Task.UserRef reminderAssignedTo = extractUserRefFromPayload(payload, "reminderAssignedTo");

        return ResponseEntity.ok(taskService.setReminder(id, reminderDateTime, reminderNote, reminderAssignedTo, performedBy, performedByRole));
    }

    @PutMapping("/{id}/reminder/complete")
    public ResponseEntity<Task> completeReminder(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        String actionNotes = (String) payload.get("actionNotes");
        String performedBy = (String) payload.getOrDefault("performedBy", "Staff");
        String performedByRole = (String) payload.getOrDefault("performedByRole", "STAFF");
        return ResponseEntity.ok(taskService.completeReminder(id, actionNotes, performedBy, performedByRole));
    }

    @PutMapping("/{id}/reminder/reschedule")
    public ResponseEntity<Task> rescheduleReminder(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        String newReminderDateTime = (String) payload.get("newReminderDateTime");
        String rescheduleReason = (String) payload.get("rescheduleReason");
        String performedBy = (String) payload.getOrDefault("performedBy", "Staff");
        String performedByRole = (String) payload.getOrDefault("performedByRole", "STAFF");

        Task.UserRef reminderAssignedTo = extractUserRefFromPayload(payload, "reminderAssignedTo");

        return ResponseEntity.ok(taskService.rescheduleReminder(id, newReminderDateTime, rescheduleReason, reminderAssignedTo, performedBy, performedByRole));
    }

    @GetMapping("/reminders/due")
    public ResponseEntity<List<Task>> getDueReminders(@RequestParam(required = false) String staffId) {
        return ResponseEntity.ok(taskService.getDueReminders(staffId));
    }

    @SuppressWarnings("unchecked")
    private Task.UserRef extractUserRefFromPayload(Map<String, Object> payload, String key) {
        if (payload.containsKey(key) && payload.get(key) instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) payload.get(key);
            return Task.UserRef.builder()
                    .id((String) map.get("id"))
                    .name((String) map.get("name"))
                    .email((String) map.get("email"))
                    .role((String) map.get("role"))
                    .avatar((String) map.get("avatar"))
                    .build();
        } else if (payload.containsKey(key + "Id") || payload.containsKey(key + "Name")) {
            return Task.UserRef.builder()
                    .id((String) payload.get(key + "Id"))
                    .name((String) payload.get(key + "Name"))
                    .role("STAFF")
                    .build();
        }
        return null;
    }
}
