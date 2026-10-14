package com.globalisor.backend.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document(collection = "document_categories")
public class DocumentCategory {
    @Id
    private String id;
    private String key;         // e.g. "KYC", "Invoice", "Board Resolutions"
    private String label;       // e.g. "KYC", "Invoice", "Board Resolutions"
    private String description; // e.g. "Passport, NRIC, Proof of Address"
    private String icon;        // e.g. "shield-check", "file-text", "folder"
    private String color;       // e.g. "blue", "emerald", "purple"
    private Integer sortOrder;
    private Boolean isSystem;
    private String parentKey;   // Key of parent category/subfolder, null for root
    private String parentId;    // ID of parent category/subfolder, null for root
    private String rootKey;     // Key of top-level Root Category (Level 0)
    @Builder.Default
    private Integer level = 0;  // 0 = Root Category, 1 = Sub-Folder Level 1, 2 = Nested Sub-Folder Level 2 (Max depth)
    private String fullPath;    // e.g. "Tax/FY 2024/Q1 Returns"
    @Builder.Default
    private java.util.List<String> subFolders = new java.util.ArrayList<>();

    // Folder Scope: "COMMON" (all clients) or "CLIENT_SPECIFIC" (specific client account)
    @Builder.Default
    private String scope = "COMMON";
    private String clientId;
    private String clientName;

    public DocumentCategory() {
    }

    public DocumentCategory(String id, String key, String label, String description, String icon, String color, Integer sortOrder, Boolean isSystem, String parentKey, String parentId, String rootKey, Integer level, String fullPath, java.util.List<String> subFolders, String scope, String clientId, String clientName) {
        this.id = id;
        this.key = key;
        this.label = label;
        this.description = description;
        this.icon = icon;
        this.color = color;
        this.sortOrder = sortOrder;
        this.isSystem = isSystem;
        this.parentKey = parentKey;
        this.parentId = parentId;
        this.rootKey = rootKey;
        this.level = level;
        this.fullPath = fullPath;
        this.subFolders = subFolders;
        this.scope = scope;
        this.clientId = clientId;
        this.clientName = clientName;
    }
}
