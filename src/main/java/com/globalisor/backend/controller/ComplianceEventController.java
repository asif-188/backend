package com.globalisor.backend.controller;

import com.globalisor.backend.model.ComplianceEvent;
import com.globalisor.backend.repository.ComplianceEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/compliance-events")
public class ComplianceEventController {

    @Autowired
    private ComplianceEventRepository complianceEventRepository;

    @GetMapping
    public ResponseEntity<List<ComplianceEvent>> getAllEvents(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        
        List<ComplianceEvent> list = complianceEventRepository.findAll();
        
        if (clientId != null && !clientId.isEmpty() && !"all".equalsIgnoreCase(clientId)) {
            list = list.stream().filter(e -> e.getClientId() == null || e.getClientId().equals("all") || e.getClientId().equals(clientId)).collect(Collectors.toList());
        }
        if (category != null && !category.isEmpty() && !"all".equalsIgnoreCase(category)) {
            list = list.stream().filter(e -> category.equalsIgnoreCase(e.getCategory())).collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            list = list.stream().filter(e -> status.equalsIgnoreCase(e.getStatus())).collect(Collectors.toList());
        }

        // Sort by dueDate chronologically
        list.sort((a, b) -> {
            String d1 = a.getDueDate() != null ? a.getDueDate() : "";
            String d2 = b.getDueDate() != null ? b.getDueDate() : "";
            return d1.compareTo(d2);
        });

        return ResponseEntity.ok(list);
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<ComplianceEvent>> getEventsByClient(@PathVariable String clientId) {
        List<ComplianceEvent> list = complianceEventRepository.findAll().stream()
                .filter(e -> (e.getClientId() == null || "all".equalsIgnoreCase(e.getClientId()) || clientId.equalsIgnoreCase(e.getClientId())) && Boolean.TRUE.equals(e.getPublished()))
                .collect(Collectors.toList());

        list.sort((a, b) -> {
            String d1 = a.getDueDate() != null ? a.getDueDate() : "";
            String d2 = b.getDueDate() != null ? b.getDueDate() : "";
            return d1.compareTo(d2);
        });

        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<ComplianceEvent> createEvent(@RequestBody ComplianceEvent event) {
        if (event.getId() == null || event.getId().isEmpty()) {
            event.setId("COMP-EVT-" + System.currentTimeMillis());
        }
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(System.currentTimeMillis());
        }
        event.setUpdatedAt(System.currentTimeMillis());
        if (event.getStatus() == null || event.getStatus().isEmpty()) {
            event.setStatus("upcoming");
        }
        ComplianceEvent saved = complianceEventRepository.save(event);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComplianceEvent> updateEvent(@PathVariable String id, @RequestBody ComplianceEvent updates) {
        Optional<ComplianceEvent> opt = complianceEventRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ComplianceEvent existing = opt.get();
        if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
        if (updates.getCategory() != null) existing.setCategory(updates.getCategory());
        if (updates.getDueDate() != null) existing.setDueDate(updates.getDueDate());
        if (updates.getDueTimestamp() != null) existing.setDueTimestamp(updates.getDueTimestamp());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getCompletionDate() != null) existing.setCompletionDate(updates.getCompletionDate());
        if (updates.getCompletedBy() != null) existing.setCompletedBy(updates.getCompletedBy());
        if (updates.getRecurring() != null) existing.setRecurring(updates.getRecurring());
        if (updates.getRecurringFrequency() != null) existing.setRecurringFrequency(updates.getRecurringFrequency());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getRequiredAction() != null) existing.setRequiredAction(updates.getRequiredAction());
        if (updates.getAssignedOfficer() != null) existing.setAssignedOfficer(updates.getAssignedOfficer());
        if (updates.getPublished() != null) existing.setPublished(updates.getPublished());
        
        existing.setUpdatedAt(System.currentTimeMillis());
        ComplianceEvent saved = complianceEventRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ComplianceEvent> completeEvent(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        Optional<ComplianceEvent> opt = complianceEventRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ComplianceEvent existing = opt.get();
        existing.setStatus("completed");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        existing.setCompletionDate(sdf.format(new Date()));
        
        String completedBy = (body != null && body.containsKey("completedBy")) ? body.get("completedBy") : "Client User";
        existing.setCompletedBy(completedBy);
        existing.setUpdatedAt(System.currentTimeMillis());
        
        ComplianceEvent saved = complianceEventRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String id) {
        if (!complianceEventRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        complianceEventRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
