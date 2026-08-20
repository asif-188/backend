package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "compliance_events")
public class ComplianceEvent {
    @Id
    private String id;
    private String clientId;           // Specific clientId or "all"
    private String companyName;        // Client company name
    private String title;              // e.g. "Financial Year End (FYE)", "Annual Return Filing (ACRA)"
    private String category;           // e.g. "Statutory Filing", "Taxation", "Payroll & CPF", "Corporate Governance", "Immigration & HR", "Corporate Services"
    private String dueDate;            // YYYY-MM-DD
    private Long dueTimestamp;         // Epoch millis of due date
    private String status = "upcoming"; // "upcoming", "pending", "completed", "overdue"
    private String completionDate;     // YYYY-MM-DD
    private String completedBy;        // User/Admin who marked as complete
    private Boolean recurring = false;
    private String recurringFrequency; // "annual", "quarterly", "monthly", "custom"
    private String description;
    private String requiredAction;
    private String assignedOfficer;    // e.g. "Sarah Tan (Corporate Secretary)"
    private Boolean published = true;
    private Long createdAt = System.currentTimeMillis();
    private Long updatedAt = System.currentTimeMillis();
    private List<String> auditLogs = new ArrayList<>();
}
