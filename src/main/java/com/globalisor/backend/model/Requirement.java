package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;
import java.util.Date;
import java.util.HashMap;

@Data
@NoArgsConstructor
@Document(collection = "requirements")
public class Requirement {
    @Id
    private String id;
    @Indexed
    private String userId;
    @Indexed
    private String status = "pending";
    @Indexed
    private String applicationReferenceNo;
    private String staff = "Unassigned";
    @Indexed
    private String assignedStaffId;
    private String assignedStaffName;
    private String reviewedBy;
    private Date reviewedAt;
    private String rejectionReason;
    private Date createdAt = new Date();
    private Date updatedAt = new Date();
    private Map<String, Map<String, Object>> sectionStatuses = new HashMap<>();
    private Map<String, Object> data;

    public Requirement(String userId, Map<String, Object> data) {
        this.userId = userId;
        this.data = data;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }
}
