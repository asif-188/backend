package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@Document(collection = "documents")
public class ClientDocument {
    @Id
    private String id;
    private String title;
    private String file; // base64 or URL
    private String status; // pending, approved, rejected
    private String clientId;
    private String date;

    private String clientName;
    private String applicationId;
    private String companyName;
    private String service;
    private String documentType; // passport, proof_of_address, director_id, business_plan, etc.
    private String uploadSource; // "Pre-Registration", "Client Portal", "Applications", "Messages", "Migration", etc.

    // Multi-tenant & GCP Storage Migration fields
    private String tenantId;
    private String category; // AML/CDD, Tax, Passport, BizFile, Invoice, NRIC/FIN, Lease/Tenancy, Constitution, Bank Statement, Other
    private String suggestedModule; // Compliance, Tax, KYC, Company, Finance, Director/Shareholder, Registered Office, Misc
    private String gcsBucket;
    private String gcsBlobName;
    private String originalPath;
    private String fileExtension;
    private Long fileSize;
    private String uploadDate;

    private List<String> versions = new ArrayList<>();
    private List<String> activityLogs = new ArrayList<>();
}
