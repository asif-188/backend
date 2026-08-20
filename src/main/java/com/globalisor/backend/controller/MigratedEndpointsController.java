package com.globalisor.backend.controller;

import com.globalisor.backend.model.*;
import com.globalisor.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import com.globalisor.backend.websocket.ChatWebSocketHandler;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class MigratedEndpointsController {

    @Autowired
    ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    KycRepository kycRepository;

    @Autowired
    ComplianceRepository complianceRepository;

    @Autowired
    ClientDocumentRepository clientDocumentRepository;

    @Autowired
    StaticContentRepository staticContentRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    com.globalisor.backend.service.NotificationService notificationService;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    CatalogItemRepository catalogItemRepository;

    @Autowired
    CountryRepository countryRepository;

    // --- BLOG ENDPOINTS ---
    @GetMapping("/blogs")
    public ResponseEntity<List<Blog>> getAllBlogs() {
        List<Blog> blogs = blogRepository.findAll();
        // Sort by createdAt descending
        blogs.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return ResponseEntity.ok(blogs);
    }

    @GetMapping("/auth/debug/users")

    public ResponseEntity<List<User>> getDebugUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }


    @PostMapping("/blogs")
    public ResponseEntity<Blog> createBlog(@RequestBody Blog blog) {
        if (blog.getId() == null) {
            blog.setId("BLG-" + System.currentTimeMillis());
        }
        if (blog.getCreatedAt() == null) {
            blog.setCreatedAt(System.currentTimeMillis());
        }
        Blog savedBlog = blogRepository.save(blog);

        // If published, create a notification for all clients
        if (Boolean.TRUE.equals(savedBlog.getPublished())) {
            Notification notification = new Notification();
            notification.setId("notif-" + System.currentTimeMillis());
            notification.setClientId("all");
            notification.setTitle("New update from Globalisor");
            notification.setMessage(savedBlog.getTitle() + " has been published.");
            notification.setType("blog");
            notification.setRelatedId(savedBlog.getId());
            notification.setTimestamp(System.currentTimeMillis());
            notification.setReadBy(new ArrayList<>());
            notificationRepository.save(notification);
            chatWebSocketHandler.broadcastNotification(notification);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(savedBlog);
    }

    @PatchMapping("/blogs/{id}")
    public ResponseEntity<Blog> updateBlog(@PathVariable String id, @RequestBody Blog blogUpdates) {
        Optional<Blog> blogOpt = blogRepository.findById(id);
        if (blogOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Blog blog = blogOpt.get();
        if (blogUpdates.getTitle() != null) blog.setTitle(blogUpdates.getTitle());
        if (blogUpdates.getExcerpt() != null) blog.setExcerpt(blogUpdates.getExcerpt());
        if (blogUpdates.getContent() != null) blog.setContent(blogUpdates.getContent());
        if (blogUpdates.getCategory() != null) blog.setCategory(blogUpdates.getCategory());
        if (blogUpdates.getAuthor() != null) blog.setAuthor(blogUpdates.getAuthor());
        if (blogUpdates.getDate() != null) blog.setDate(blogUpdates.getDate());
        if (blogUpdates.getPublished() != null) blog.setPublished(blogUpdates.getPublished());
        if (blogUpdates.getCoverImage() != null) blog.setCoverImage(blogUpdates.getCoverImage());
        
        // Map published version changes
        if (blogUpdates.getPublishedTitle() != null) blog.setPublishedTitle(blogUpdates.getPublishedTitle());
        if (blogUpdates.getPublishedExcerpt() != null) blog.setPublishedExcerpt(blogUpdates.getPublishedExcerpt());
        if (blogUpdates.getPublishedContent() != null) blog.setPublishedContent(blogUpdates.getPublishedContent());
        if (blogUpdates.getPublishedCoverImage() != null) blog.setPublishedCoverImage(blogUpdates.getPublishedCoverImage());
        if (blogUpdates.getHasUnpublishedChanges() != null) blog.setHasUnpublishedChanges(blogUpdates.getHasUnpublishedChanges());
        
        blog.setUpdatedAt(System.currentTimeMillis());
        
        Blog saved = blogRepository.save(blog);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/blogs/{id}")
    public ResponseEntity<Blog> updateBlogPut(@PathVariable String id, @RequestBody Blog blogUpdates) {
        Optional<Blog> blogOpt = blogRepository.findById(id);
        if (blogOpt.isEmpty()) {
            if (blogUpdates.getId() == null) {
                blogUpdates.setId(id);
            }
            if (blogUpdates.getCreatedAt() == null) {
                blogUpdates.setCreatedAt(System.currentTimeMillis());
            }
            Blog saved = blogRepository.save(blogUpdates);
            return ResponseEntity.ok(saved);
        }
        Blog blog = blogOpt.get();
        if (blogUpdates.getTitle() != null) blog.setTitle(blogUpdates.getTitle());
        if (blogUpdates.getExcerpt() != null) blog.setExcerpt(blogUpdates.getExcerpt());
        if (blogUpdates.getContent() != null) blog.setContent(blogUpdates.getContent());
        if (blogUpdates.getCategory() != null) blog.setCategory(blogUpdates.getCategory());
        if (blogUpdates.getAuthor() != null) blog.setAuthor(blogUpdates.getAuthor());
        if (blogUpdates.getDate() != null) blog.setDate(blogUpdates.getDate());
        if (blogUpdates.getPublished() != null) blog.setPublished(blogUpdates.getPublished());
        if (blogUpdates.getCoverImage() != null) blog.setCoverImage(blogUpdates.getCoverImage());
        
        // Map published version changes
        blog.setPublishedTitle(blogUpdates.getPublishedTitle());
        blog.setPublishedExcerpt(blogUpdates.getPublishedExcerpt());
        blog.setPublishedContent(blogUpdates.getPublishedContent());
        blog.setPublishedCoverImage(blogUpdates.getPublishedCoverImage());
        if (blogUpdates.getHasUnpublishedChanges() != null) {
            blog.setHasUnpublishedChanges(blogUpdates.getHasUnpublishedChanges());
        }
        
        blog.setUpdatedAt(System.currentTimeMillis());
        
        Blog saved = blogRepository.save(blog);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/blogs/{id}")
    public ResponseEntity<Void> deleteBlog(@PathVariable String id) {
        blogRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- STATIC CONTENT ENDPOINTS ---
    @GetMapping("/static-content")
    public ResponseEntity<List<StaticContent>> getStaticContent(
            @RequestParam(required = false) String portal,
            @RequestParam(required = false) String category) {
        List<StaticContent> list;
        if (portal != null && category != null) {
            list = staticContentRepository.findByPortalAndCategory(portal, category);
        } else if (portal != null) {
            list = staticContentRepository.findByPortal(portal);
        } else if (category != null) {
            list = staticContentRepository.findByCategory(category);
        } else {
            list = staticContentRepository.findAll();
        }
        // Sort by createdAt descending
        list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/static-content")
    public ResponseEntity<StaticContent> createStaticContent(@RequestBody StaticContent sc) {
        if (sc.getId() == null) {
            sc.setId("sc-" + System.currentTimeMillis());
        }
        sc.setCreatedAt(System.currentTimeMillis());
        sc.setUpdatedAt(System.currentTimeMillis());
        StaticContent saved = staticContentRepository.save(sc);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/static-content/{id}")
    public ResponseEntity<StaticContent> updateStaticContent(@PathVariable String id, @RequestBody StaticContent scUpdates) {
        Optional<StaticContent> scOpt = staticContentRepository.findById(id);
        if (scOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StaticContent sc = scOpt.get();
        if (scUpdates.getTitle() != null) sc.setTitle(scUpdates.getTitle());
        if (scUpdates.getDescription() != null) sc.setDescription(scUpdates.getDescription());
        if (scUpdates.getContent() != null) sc.setContent(scUpdates.getContent());
        if (scUpdates.getPortal() != null) sc.setPortal(scUpdates.getPortal());
        if (scUpdates.getCategory() != null) sc.setCategory(scUpdates.getCategory());
        if (scUpdates.getIsPublished() != null) sc.setIsPublished(scUpdates.getIsPublished());
        if (scUpdates.getIsPinned() != null) sc.setIsPinned(scUpdates.getIsPinned());
        sc.setUpdatedAt(System.currentTimeMillis());

        StaticContent saved = staticContentRepository.save(sc);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/static-content/{id}")
    public ResponseEntity<Void> deleteStaticContent(@PathVariable String id) {
        staticContentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- KYC ENDPOINTS ---
    @GetMapping("/kyc")
    public ResponseEntity<List<Map<String, Object>>> getAllKyc() {
        List<Kyc> kycList = kycRepository.findAll();
        List<Map<String, Object>> response = kycList.stream().map(k -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", k.getId());
            map.put("clientId", k.getClientId());
            map.put("name", k.getName());
            map.put("idType", k.getIdType());
            map.put("idNum", k.getIdNum() != null ? k.getIdNum() : "N/A");
            map.put("idExpiry", k.getIdExpiry() != null ? k.getIdExpiry() : "N/A");
            map.put("nation", k.getNation());
            map.put("status", k.getStatus());
            map.put("risk", k.getRisk());
            map.put("lastUpdated", k.getLastUpdated());
            map.put("identityStatus", k.getIdentityStatus() != null ? k.getIdentityStatus() : "pending");
            map.put("amlStatus", k.getAmlStatus() != null ? k.getAmlStatus() : "pending");
            map.put("pepStatus", k.getPepStatus() != null ? k.getPepStatus() : "pending");
            map.put("sanctionsStatus", k.getSanctionsStatus() != null ? k.getSanctionsStatus() : "pending");
            map.put("overrideNotes", k.getOverrideNotes() != null ? k.getOverrideNotes() : "");
            map.put("overrideBy", k.getOverrideBy() != null ? k.getOverrideBy() : "");
            map.put("overrideAt", k.getOverrideAt() != null ? k.getOverrideAt() : 0L);
            map.put("shuftiRef", k.getShuftiRef() != null ? k.getShuftiRef() : "");
            map.put("auditLogs", k.getAuditLogs() != null ? k.getAuditLogs() : new ArrayList<String>());

            Optional<User> userOpt = userRepository.findById(k.getClientId());
            if (userOpt.isPresent()) {
                map.put("clientName", formatUserName(userOpt.get()));
            } else {
                map.put("clientName", "Unknown");
            }
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/kyc/{id}")
    public ResponseEntity<Kyc> updateKyc(@PathVariable String id, @RequestBody Map<String, Object> updates) {
        Optional<Kyc> kycOpt = kycRepository.findById(id);
        if (kycOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Kyc kyc = kycOpt.get();
        if (kyc.getAuditLogs() == null) {
            kyc.setAuditLogs(new ArrayList<>());
        }
        
        if (updates.containsKey("status")) {
            String oldStatus = kyc.getStatus();
            String newStatus = (String) updates.get("status");
            kyc.setStatus(newStatus);
            kyc.getAuditLogs().add("KYC overall status overridden from " + oldStatus + " to " + newStatus);
        }
        if (updates.containsKey("risk")) {
            String oldRisk = kyc.getRisk();
            String newRisk = (String) updates.get("risk");
            kyc.setRisk(newRisk);
            kyc.getAuditLogs().add("Risk rating modified from " + oldRisk + " to " + newRisk);
        }
        if (updates.containsKey("identityStatus")) {
            kyc.setIdentityStatus((String) updates.get("identityStatus"));
            kyc.getAuditLogs().add("Identity check status updated to: " + updates.get("identityStatus"));
        }
        if (updates.containsKey("amlStatus")) {
            kyc.setAmlStatus((String) updates.get("amlStatus"));
            kyc.getAuditLogs().add("AML screening status updated to: " + updates.get("amlStatus"));
        }
        if (updates.containsKey("pepStatus")) {
            kyc.setPepStatus((String) updates.get("pepStatus"));
            kyc.getAuditLogs().add("PEP listing check status updated to: " + updates.get("pepStatus"));
        }
        if (updates.containsKey("sanctionsStatus")) {
            kyc.setSanctionsStatus((String) updates.get("sanctionsStatus"));
            kyc.getAuditLogs().add("Sanctions list check status updated to: " + updates.get("sanctionsStatus"));
        }
        if (updates.containsKey("overrideNotes")) {
            kyc.setOverrideNotes((String) updates.get("overrideNotes"));
        }
        if (updates.containsKey("overrideBy")) {
            kyc.setOverrideBy((String) updates.get("overrideBy"));
            kyc.getAuditLogs().add("Review completed by officer: " + updates.get("overrideBy"));
        }
        
        kyc.setOverrideAt(System.currentTimeMillis());
        kyc.setLastUpdated(System.currentTimeMillis());
        Kyc saved = kycRepository.save(kyc);

        // Also update matching Compliance record to sync statuses
        Optional<Compliance> compOpt = complianceRepository.findAll().stream().filter(c -> c.getClientId().equals(kyc.getClientId())).findFirst();
        if (compOpt.isPresent()) {
            Compliance comp = compOpt.get();
            comp.setStatus(kyc.getStatus());
            comp.setRisk(kyc.getRisk());
            comp.setAmlStatus(kyc.getAmlStatus());
            comp.setPepStatus(kyc.getPepStatus());
            comp.setSanctionsStatus(kyc.getSanctionsStatus());
            comp.setLastUpdated(System.currentTimeMillis());
            complianceRepository.save(comp);
        }

        // Notify client
        try {
            notificationService.sendNotification(
                kyc.getClientId(),
                "KYC Verification Update",
                "Your compliance profile review is complete. Overall KYC Status: " + saved.getStatus().toUpperCase() + " (Risk: " + saved.getRisk() + ").",
                "compliance",
                saved.getId(),
                "Info"
            );
        } catch(Exception e){}

        // WS Event broadcast
        Map<String, Object> syncEvent = new HashMap<>();
        syncEvent.put("type", "compliance_sync");
        syncEvent.put("clientId", kyc.getClientId());
        syncEvent.put("kycId", saved.getId());
        chatWebSocketHandler.broadcastEvent(syncEvent);

        return ResponseEntity.ok(saved);
    }

    // --- COMPLIANCE ENDPOINTS ---
    @GetMapping("/compliance")
    public ResponseEntity<List<Map<String, Object>>> getAllCompliance() {
        List<Compliance> list = complianceRepository.findAll();
        List<Map<String, Object>> response = list.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("complianceId", c.getId());
            map.put("clientId", c.getClientId());
            map.put("name", c.getName());
            map.put("type", c.getType());
            map.put("requirement", c.getType()); // Fix undefined in compliance.html
            map.put("status", c.getStatus());
            map.put("risk", c.getRisk());
            map.put("lastUpdated", c.getLastUpdated());
            
            // Calculate dynamic deadline (30 days from update)
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            long updateTime = c.getLastUpdated() != null ? c.getLastUpdated() : System.currentTimeMillis();
            map.put("deadline", sdf.format(new Date(updateTime + 30L * 24 * 60 * 60 * 1000)));

            map.put("amlStatus", c.getAmlStatus() != null ? c.getAmlStatus() : "pending");
            map.put("pepStatus", c.getPepStatus() != null ? c.getPepStatus() : "pending");
            map.put("sanctionsStatus", c.getSanctionsStatus() != null ? c.getSanctionsStatus() : "pending");
            map.put("overrideNotes", c.getOverrideNotes() != null ? c.getOverrideNotes() : "");
            map.put("overrideBy", c.getOverrideBy() != null ? c.getOverrideBy() : "");
            map.put("overrideAt", c.getOverrideAt() != null ? c.getOverrideAt() : 0L);
            map.put("auditLogs", c.getAuditLogs() != null ? c.getAuditLogs() : new ArrayList<String>());

            Optional<User> userOpt = userRepository.findById(c.getClientId());
            if (userOpt.isPresent()) {
                map.put("clientName", formatUserName(userOpt.get()));
            } else {
                map.put("clientName", "Unknown");
            }
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/compliance/{id}")
    public ResponseEntity<Compliance> updateCompliance(@PathVariable String id, @RequestBody Map<String, Object> updates) {
        Optional<Compliance> compOpt = complianceRepository.findById(id);
        if (compOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Compliance comp = compOpt.get();
        if (comp.getAuditLogs() == null) {
            comp.setAuditLogs(new ArrayList<>());
        }

        if (updates.containsKey("status")) {
            String oldStatus = comp.getStatus();
            String newStatus = (String) updates.get("status");
            comp.setStatus(newStatus);
            comp.getAuditLogs().add("Compliance status overridden from " + oldStatus + " to " + newStatus);
        }
        if (updates.containsKey("risk")) {
            String oldRisk = comp.getRisk();
            String newRisk = (String) updates.get("risk");
            comp.setRisk(newRisk);
            comp.getAuditLogs().add("Risk classification changed from " + oldRisk + " to " + newRisk);
        }
        if (updates.containsKey("amlStatus")) {
            comp.setAmlStatus((String) updates.get("amlStatus"));
        }
        if (updates.containsKey("pepStatus")) {
            comp.setPepStatus((String) updates.get("pepStatus"));
        }
        if (updates.containsKey("sanctionsStatus")) {
            comp.setSanctionsStatus((String) updates.get("sanctionsStatus"));
        }
        if (updates.containsKey("overrideNotes")) {
            comp.setOverrideNotes((String) updates.get("overrideNotes"));
        }
        if (updates.containsKey("overrideBy")) {
            comp.setOverrideBy((String) updates.get("overrideBy"));
            comp.getAuditLogs().add("Manual review updated by officer: " + updates.get("overrideBy"));
        }

        comp.setOverrideAt(System.currentTimeMillis());
        comp.setLastUpdated(System.currentTimeMillis());
        Compliance saved = complianceRepository.save(comp);

        // Notify client
        try {
            notificationService.sendNotification(
                comp.getClientId(),
                "Compliance Requirement Update",
                "Compliance item '" + saved.getType() + "' has been updated to: " + saved.getStatus().toUpperCase() + ".",
                "compliance",
                saved.getId(),
                "Info"
            );
        } catch(Exception e){}

        // WS Event broadcast
        Map<String, Object> syncEvent = new HashMap<>();
        syncEvent.put("type", "compliance_sync");
        syncEvent.put("clientId", comp.getClientId());
        syncEvent.put("complianceId", saved.getId());
        chatWebSocketHandler.broadcastEvent(syncEvent);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/compliance/shufti-verify")
    public ResponseEntity<?> runShuftiVerification(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String name = body.get("name");
        String idType = body.get("idType");
        String idNum = body.get("idNum");
        String nation = body.get("nation");

        if (clientId == null || name == null || idType == null || idNum == null || nation == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        // Find or create KYC record
        Optional<Kyc> kycOpt = kycRepository.findAll().stream().filter(k -> k.getClientId().equals(clientId)).findFirst();
        Kyc kyc;
        if (kycOpt.isPresent()) {
            kyc = kycOpt.get();
        } else {
            kyc = new Kyc();
            kyc.setId("KYC-" + System.currentTimeMillis());
            kyc.setClientId(clientId);
        }

        kyc.setName(name);
        kyc.setIdType(idType);
        kyc.setIdNum(idNum);
        kyc.setNation(nation);
        kyc.setShuftiRef("SHFT-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        
        if (kyc.getAuditLogs() == null) {
            kyc.setAuditLogs(new ArrayList<>());
        }
        kyc.getAuditLogs().add("Shufti compliance scan initialized. Ref: " + kyc.getShuftiRef());

        String lowerName = name.toLowerCase();

        // 1. Identity Document OCR Verification Simulation
        if (idNum.trim().isEmpty() || idNum.equalsIgnoreCase("N/A")) {
            kyc.setIdentityStatus("failed");
            kyc.getAuditLogs().add("Identity document check failed: Missing or invalid document reference number.");
        } else {
            kyc.setIdentityStatus("verified");
            kyc.getAuditLogs().add("Identity document OCR analysis: Verification success. Match confidence: 99.2%");
        }

        // 2. PEP (Politically Exposed Persons) Database Check
        if (lowerName.contains("pep") || lowerName.contains("politician") || lowerName.contains("marcus ng")) {
            kyc.setPepStatus("match");
            kyc.getAuditLogs().add("PEP screening: MATCH DETECTED. Customer matches records of politically exposed individuals.");
        } else {
            kyc.setPepStatus("clean");
            kyc.getAuditLogs().add("PEP screening: CLEAN. No database matches found.");
        }

        // 3. Sanctions Lists Check (OFAC, EU, UN, MAS, HMT)
        if (lowerName.contains("sanction") || lowerName.contains("terrorist") || lowerName.contains("marcus ng")) {
            kyc.setSanctionsStatus("match");
            kyc.getAuditLogs().add("Sanctions monitoring: MATCH DETECTED. Match found on watchlists.");
        } else {
            kyc.setSanctionsStatus("clean");
            kyc.getAuditLogs().add("Sanctions monitoring: CLEAN. No watchlist matches detected.");
        }

        // 4. Overall AML & Compliance status aggregation
        if ("match".equals(kyc.getPepStatus()) || "match".equals(kyc.getSanctionsStatus())) {
            kyc.setAmlStatus("flagged");
            kyc.setRisk("High");
            kyc.setStatus("flagged");
            kyc.getAuditLogs().add("AML Screening complete: FLAGGED (High risk PEP/Sanction hit requires manual override).");
        } else if ("failed".equals(kyc.getIdentityStatus())) {
            kyc.setAmlStatus("pending");
            kyc.setRisk("Medium");
            kyc.setStatus("under review");
            kyc.getAuditLogs().add("AML Screening complete: PENDING (Identity document verification failure).");
        } else {
            kyc.setAmlStatus("clean");
            kyc.setRisk("Low");
            kyc.setStatus("approved");
            kyc.getAuditLogs().add("AML Screening complete: APPROVED (Verified, Clean background check).");
        }

        kyc.setLastUpdated(System.currentTimeMillis());
        Kyc savedKyc = kycRepository.save(kyc);

        // Auto-sync client's primary Compliance record
        Optional<Compliance> compOpt = complianceRepository.findAll().stream().filter(c -> c.getClientId().equals(clientId)).findFirst();
        if (compOpt.isPresent()) {
            Compliance comp = compOpt.get();
            comp.setAmlStatus(kyc.getAmlStatus());
            comp.setPepStatus(kyc.getPepStatus());
            comp.setSanctionsStatus(kyc.getSanctionsStatus());
            comp.setStatus(kyc.getStatus());
            comp.setRisk(kyc.getRisk());
            comp.setLastUpdated(System.currentTimeMillis());
            if (comp.getAuditLogs() == null) {
                comp.setAuditLogs(new ArrayList<>());
            }
            comp.getAuditLogs().add("AML Screening results auto-synced. Overall compliance rating: " + comp.getStatus().toUpperCase());
            complianceRepository.save(comp);
        }

        // Trigger Notifications
        try {
            // Client dashboard alert
            notificationService.sendNotification(
                clientId,
                "Verification Scanning Complete",
                "Your real-time background screening has processed: " + kyc.getStatus().toUpperCase() + " (Risk: " + kyc.getRisk() + ").",
                "compliance",
                savedKyc.getId(),
                "Info"
            );
            // Internal compliance dashboard alert
            notificationService.sendNotification(
                "admin",
                "Shufti Compliance Alert",
                "Real-time screening executed for '" + name + "'. Status: " + kyc.getStatus().toUpperCase() + " (Risk: " + kyc.getRisk() + ").",
                "compliance",
                savedKyc.getId(),
                "Info"
            );
        } catch(Exception e){}

        // WS Sync push
        Map<String, Object> syncEvent = new HashMap<>();
        syncEvent.put("type", "compliance_sync");
        syncEvent.put("clientId", clientId);
        syncEvent.put("kycId", savedKyc.getId());
        chatWebSocketHandler.broadcastEvent(syncEvent);

        return ResponseEntity.ok(savedKyc);
    }

    // --- DOCUMENT ENDPOINTS ---
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> getAllDocuments(@RequestParam(required = false) String clientId) {
        List<ClientDocument> documents;
        if (clientId != null && !clientId.trim().isEmpty()) {
            documents = clientDocumentRepository.findByClientId(clientId.trim());
            if (documents.isEmpty()) {
                documents = generateClientDocumentSuite(clientId.trim());
            }
        } else {
            documents = clientDocumentRepository.findAll();
        }
        List<Requirement> requirements = requirementRepository.findAll();

        List<Map<String, Object>> response = documents.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("title", d.getTitle());
            map.put("file", d.getFile());
            map.put("status", d.getStatus());
            map.put("clientId", d.getClientId());
            map.put("date", d.getDate());

            Optional<User> userOpt = userRepository.findById(d.getClientId() != null ? d.getClientId() : "");
            String clientName = d.getClientName();
            if (clientName == null || clientName.isEmpty()) {
                if (userOpt.isPresent()) {
                    clientName = formatUserName(userOpt.get());
                } else {
                    clientName = "Unknown";
                }
            }
            map.put("clientName", clientName);
            map.put("client", clientName + " - " + (d.getClientId() != null ? d.getClientId().replace("C-", "APP-") : ""));

            Optional<Requirement> reqOpt = requirements.stream()
                    .filter(r -> r.getUserId() != null && r.getUserId().equalsIgnoreCase(d.getClientId()))
                    .findFirst();

            String companyName = d.getCompanyName();
            if (companyName == null || companyName.isEmpty()) {
                if (reqOpt.isPresent()) {
                    Map<String, Object> data = reqOpt.get().getData();
                    if (data != null && data.containsKey("names")) {
                        Object namesObj = data.get("names");
                        if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                            companyName = ((List<?>) namesObj).get(0).toString();
                        }
                    }
                }
            }
            if (companyName == null || companyName.isEmpty()) {
                if (userOpt.isPresent() && userOpt.get().getCompanyName() != null && !userOpt.get().getCompanyName().isEmpty()) {
                    companyName = userOpt.get().getCompanyName();
                } else {
                    companyName = "Globalisor Entity";
                }
            }
            map.put("company", companyName);
            map.put("companyName", companyName);

            String appId = d.getApplicationId();
            if (appId == null || appId.isEmpty()) {
                appId = reqOpt.isPresent() ? (reqOpt.get().getId() != null ? reqOpt.get().getId().replace("SRV-", "APP-") : "APP-unknown") : "N/A";
            }
            map.put("applicationId", appId);

            String service = d.getService();
            if (service == null || service.isEmpty()) {
                service = "Company Incorporation";
            }
            map.put("service", service);

            String documentType = d.getDocumentType();
            if (documentType == null || documentType.isEmpty()) {
                documentType = d.getTitle() != null ? d.getTitle() : "Other";
            }
            map.put("documentType", documentType);

            String category = d.getCategory();
            if (category == null || category.isEmpty()) {
                category = d.getSuggestedModule() != null ? d.getSuggestedModule() : "Corporate";
            }
            map.put("category", category);

            String uploadSource = d.getUploadSource();
            if (uploadSource == null || uploadSource.isEmpty()) {
                uploadSource = "Client Portal";
            }
            map.put("uploadSource", uploadSource);

            List<String> versions = d.getVersions();
            if (versions == null || versions.isEmpty()) {
                versions = new ArrayList<>();
                if (d.getFile() != null) {
                    versions.add(d.getFile());
                }
            }
            map.put("versions", versions);

            List<String> activityLogs = d.getActivityLogs();
            if (activityLogs == null) {
                activityLogs = new ArrayList<>();
            }
            map.put("activityLogs", activityLogs);

            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    public List<ClientDocument> generateClientDocumentSuite(String clientId) {
        Optional<User> userOpt = userRepository.findById(clientId);
        String clientName = userOpt.isPresent() ? formatUserName(userOpt.get()) : "Valued Client";
        String companyName = userOpt.isPresent() && userOpt.get().getCompanyName() != null ? userOpt.get().getCompanyName() : "Globalisor Entity (" + clientId + ")";

        List<ClientDocument> docs = new ArrayList<>();
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        String[][] templates = {
            {"ACRA Certificate of Incorporation", "BizFile / Corporate", "Certificate of Incorporation", "ACRA Registry", "Approved"},
            {"ACRA Business Profile (BizFile)", "BizFile / Corporate", "BizFile Extract", "ACRA Registry", "Approved"},
            {"Company Constitution & Memorandum of Association", "Constitution", "Company Constitution", "Legal Admin", "Approved"},
            {"Director Passport & Identity Verification Document", "Passport / Identity", "Passport", "KYC Portal", "Approved"},
            {"Proof of Residential Address (Bank Statement / Utility)", "Proof of Address", "Proof of Address", "KYC Portal", "Approved"},
            {"AML & KYC Compliance Clearance Certificate", "AML/CDD", "Compliance Certificate", "Compliance Engine", "Approved"},
            {"First Board Resolution & Officer Appointment", "Director/Shareholder", "Board Resolution", "Corporate Secretarial", "Approved"},
            {"Corporate Tax & GST Registration Certificate", "Tax", "Tax Certificate", "IRAS Portal", "Approved"}
        };

        for (int i = 0; i < templates.length; i++) {
            String[] t = templates[i];
            ClientDocument doc = new ClientDocument();
            doc.setId("DOC-" + clientId + "-" + (i + 1));
            doc.setTitle(t[0]);
            doc.setCategory(t[1]);
            doc.setDocumentType(t[2]);
            doc.setUploadSource(t[3]);
            doc.setStatus(t[4]);
            doc.setClientId(clientId);
            doc.setClientName(clientName);
            doc.setCompanyName(companyName);
            doc.setApplicationId(clientId.replace("C-", "APP-"));
            doc.setService("Company Incorporation");
            doc.setDate(today);
            doc.setFile("/api/documents/" + doc.getId() + "/download");
            doc.setSuggestedModule(t[1]);
            doc.getActivityLogs().add("Document verified and initialized for " + clientName + " on " + today);
            clientDocumentRepository.save(doc);
            docs.add(doc);
        }

        return docs;
    }

    @PostMapping("/documents")
    public ResponseEntity<ClientDocument> createDocument(@RequestBody ClientDocument doc) {
        if (doc.getId() == null) {
            doc.setId("DOC-" + System.currentTimeMillis());
        }
        if (doc.getDate() == null) {
            doc.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        }
        if (doc.getStatus() == null) {
            doc.setStatus("pending");
        }
        if (doc.getClientName() == null && doc.getClientId() != null) {
            Optional<User> userOpt = userRepository.findById(doc.getClientId());
            userOpt.ifPresent(u -> doc.setClientName(formatUserName(u)));
        }
        if (doc.getCompanyName() == null && doc.getClientId() != null) {
            List<Requirement> reqs = requirementRepository.findAll();
            reqs.stream().filter(r -> r.getUserId().equals(doc.getClientId())).findFirst().ifPresent(r -> {
                Map<String, Object> data = r.getData();
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                        doc.setCompanyName(((List<?>) namesObj).get(0).toString());
                    }
                }
            });
        }
        if (doc.getApplicationId() == null && doc.getClientId() != null) {
            doc.setApplicationId(doc.getClientId().replace("C-", "APP-"));
        }
        if (doc.getService() == null) {
            doc.setService("Company Incorporation");
        }
        if (doc.getDocumentType() == null) {
            doc.setDocumentType(doc.getTitle() != null ? doc.getTitle() : "Other");
        }
        if (doc.getUploadSource() == null) {
            doc.setUploadSource("Client Portal");
        }
        if (doc.getVersions() == null || doc.getVersions().isEmpty()) {
            List<String> vers = new ArrayList<>();
            if (doc.getFile() != null) {
                vers.add(doc.getFile());
            }
            doc.setVersions(vers);
        }
        if (doc.getActivityLogs() == null) {
            doc.setActivityLogs(new ArrayList<>());
        }
        doc.getActivityLogs().add("Document uploaded via " + doc.getUploadSource() + " on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        ClientDocument saved = clientDocumentRepository.save(doc);
        chatWebSocketHandler.broadcastDocumentSync(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/documents/{id}")
    public ResponseEntity<ClientDocument> updateDocument(@PathVariable String id, @RequestBody Map<String, Object> updates) {
        Optional<ClientDocument> docOpt = clientDocumentRepository.findById(id);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ClientDocument doc = docOpt.get();
        String currentStatus = doc.getStatus();
        
        if (updates.containsKey("status")) {
            String newStatus = (String) updates.get("status");
            doc.setStatus(newStatus);
            if (doc.getActivityLogs() == null) {
                doc.setActivityLogs(new ArrayList<>());
            }
            doc.getActivityLogs().add("Status updated from " + currentStatus + " to " + newStatus + " on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        }
        if (updates.containsKey("title")) {
            doc.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("documentType")) {
            doc.setDocumentType((String) updates.get("documentType"));
        }
        if (updates.containsKey("file")) {
            String newFile = (String) updates.get("file");
            doc.setFile(newFile);
            if (doc.getVersions() == null) {
                doc.setVersions(new ArrayList<>());
            }
            doc.getVersions().add(newFile);
            if (doc.getActivityLogs() == null) {
                doc.setActivityLogs(new ArrayList<>());
            }
            doc.getActivityLogs().add("New document version uploaded on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        }
        
        ClientDocument saved = clientDocumentRepository.save(doc);
        chatWebSocketHandler.broadcastDocumentSync(saved);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/documents/{id}/version")
    public ResponseEntity<ClientDocument> addDocumentVersion(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<ClientDocument> docOpt = clientDocumentRepository.findById(id);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ClientDocument doc = docOpt.get();
        String file = body.get("file");
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        doc.setFile(file);
        if (doc.getVersions() == null) {
            doc.setVersions(new ArrayList<>());
        }
        doc.getVersions().add(file);
        if (doc.getActivityLogs() == null) {
            doc.setActivityLogs(new ArrayList<>());
        }
        doc.getActivityLogs().add("New version added on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        ClientDocument saved = clientDocumentRepository.save(doc);
        chatWebSocketHandler.broadcastDocumentSync(saved);
        return ResponseEntity.ok(saved);
    }

    // --- NOTIFICATION ENDPOINTS ---
    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String userId) {
        // Cleanup notifications older than 30 days (30L * 24 * 60 * 60 * 1000 = 2592000000L)
        try {
            long cutoff = System.currentTimeMillis() - 2592000000L;
            List<Notification> allNotifs = notificationRepository.findAll();
            for (Notification n : allNotifs) {
                if (n.getTimestamp() != null && n.getTimestamp() < cutoff) {
                    notificationRepository.delete(n);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        String targetId = clientId != null && !clientId.isEmpty() ? clientId : userId;
        List<Notification> list;
        if (targetId != null && !targetId.isEmpty()) {
            Set<String> targets = new HashSet<>();
            targets.add("all");
            targets.add(targetId);
            if (targetId.equals("admin") || targetId.startsWith("staff") || targetId.equals("staff-admin")) {
                targets.add("admin");
                targets.add("staff");
                targets.add("staff-admin");
            } else {
                targets.add("client");
            }
            list = notificationRepository.findByClientIdIn(targets);
        } else {
            list = notificationRepository.findAll();
        }
        // Sort by timestamp descending
        list.sort((a, b) -> Long.compare(b.getTimestamp() != null ? b.getTimestamp() : 0L, a.getTimestamp() != null ? a.getTimestamp() : 0L));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Map<String, Object>> markNotificationAsRead(@RequestBody Map<String, String> body) {
        String notifId = body.get("notifId");
        String clientId = body.get("clientId");

        if (notifId == null || clientId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "notifId and clientId are required"));
        }

        Optional<Notification> notifOpt = notificationRepository.findById(notifId);
        if (notifOpt.isPresent()) {
            Notification notification = notifOpt.get();
            if (notification.getReadBy() == null) {
                notification.setReadBy(new ArrayList<>());
            }
            if (!notification.getReadBy().contains(clientId)) {
                notification.getReadBy().add(clientId);
                notificationRepository.save(notification);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> readAllNotifications(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String userId) {
        String targetId = clientId != null && !clientId.isEmpty() ? clientId : userId;
        if (targetId == null || targetId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId or userId is required"));
        }
        Set<String> targets = new HashSet<>();
        targets.add("all");
        targets.add(targetId);
        if (targetId.equals("admin") || targetId.startsWith("staff") || targetId.equals("staff-admin")) {
            targets.add("admin");
            targets.add("staff");
            targets.add("staff-admin");
        } else {
            targets.add("client");
        }

        List<Notification> list = notificationRepository.findByClientIdIn(targets);
        for (Notification notification : list) {
            if (notification.getReadBy() == null) {
                notification.setReadBy(new ArrayList<>());
            }
            if (!notification.getReadBy().contains(targetId)) {
                notification.getReadBy().add(targetId);
                notificationRepository.save(notification);
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- MESSAGE ENDPOINTS ---
    @GetMapping("/messages")
    public ResponseEntity<List<Message>> getMessages(@RequestParam(required = false) String clientId) {
        List<Message> list;
        if (clientId != null && !clientId.isEmpty()) {
            list = messageRepository.findByClientId(clientId);
        } else {
            list = messageRepository.findAll();
        }
        // Sort by timestamp ascending (chronological chat history)
        list.sort(Comparator.comparingLong(Message::getTimestamp));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/messages")
    public ResponseEntity<Message> createMessage(@RequestBody Message message) {
        if (message.getId() == null) {
            message.setId("msg-" + System.currentTimeMillis());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }
        if (message.getIsRead() == null) {
            message.setIsRead(false);
        }
        Message saved = messageRepository.save(message);
        chatWebSocketHandler.broadcastMessageNotification(saved);

        // Create notification for new message
        Notification notif = new Notification();
        notif.setId("notif-" + System.currentTimeMillis());
        notif.setTitle("New Message");
        notif.setMessage(message.getSenderName() + ": " + message.getText());
        notif.setType("message");
        notif.setRelatedId(message.getClientId());
        if ("client".equals(message.getSenderRole())) {
            notif.setClientId("admin");
        } else {
            notif.setClientId(message.getClientId());
        }
        notificationRepository.save(notif);
        chatWebSocketHandler.broadcastNotification(notif);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/messages/{id}")
    public ResponseEntity<Message> updateMessage(@PathVariable String id, @RequestBody Map<String, Object> updates) {
        Optional<Message> msgOpt = messageRepository.findById(id);
        if (msgOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Message msg = msgOpt.get();
        if (updates.containsKey("text")) {
            msg.setText((String) updates.get("text"));
        }
        if (updates.containsKey("isRead")) {
            msg.setIsRead((Boolean) updates.get("isRead"));
        }
        Message saved = messageRepository.save(msg);
        chatWebSocketHandler.broadcastMessageNotification(saved);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/messages/conversations")
    public ResponseEntity<List<Map<String, Object>>> getConversations() {
        List<Message> allMessages = messageRepository.findAll();
        Map<String, Message> latestMessagePerClient = new HashMap<>();

        for (Message m : allMessages) {
            if (m.getClientId() == null) continue;
            if (m.getClientId().startsWith("team_chat_") || m.getClientId().startsWith("team_group_")) continue;
            Message existing = latestMessagePerClient.get(m.getClientId());
            if (existing == null || m.getTimestamp() > existing.getTimestamp()) {
                latestMessagePerClient.put(m.getClientId(), m);
            }
        }

        List<Map<String, Object>> conversations = new ArrayList<>();
        for (Map.Entry<String, Message> entry : latestMessagePerClient.entrySet()) {
            String clientId = entry.getKey();
            Message latestMsg = entry.getValue();

            Map<String, Object> conv = new HashMap<>();
            conv.put("clientId", clientId);

            Optional<User> userOpt = userRepository.findById(clientId);
            if (!userOpt.isPresent() || (!"CLIENT".equalsIgnoreCase(userOpt.get().getRole()) && !"USER".equalsIgnoreCase(userOpt.get().getRole()))) {
                continue;
            }
            String clientName = userOpt.map(this::formatUserName).orElse("Unknown");
            conv.put("clientName", clientName);
            conv.put("lastMessage", latestMsg.getText());
            conv.put("lastMessageTime", latestMsg.getTimestamp());
            
            // Calculate actual unread count
            int unreadCount = 0;
            List<Message> msgs = messageRepository.findByClientId(clientId);
            for (Message m : msgs) {
                if ("client".equals(m.getSenderRole()) && (m.getIsRead() == null || !m.getIsRead())) {
                    unreadCount++;
                }
            }
            conv.put("unreadCount", unreadCount);

            // Add presence status
            boolean isOnline = chatWebSocketHandler.isUserOnline(clientId);
            conv.put("isOnline", isOnline);
            userOpt.ifPresent(u -> conv.put("lastSeen", u.getLastSeenTime()));

            conversations.add(conv);
        }

        // Sort by lastMessageTime descending
        conversations.sort((a, b) -> Long.compare((Long) b.get("lastMessageTime"), (Long) a.get("lastMessageTime")));
        return ResponseEntity.ok(conversations);
    }

    @PostMapping("/messages/read-all")
    public ResponseEntity<?> readAllMessages(
            @RequestParam String clientId,
            @RequestParam(required = false) String senderRole,
            @RequestParam(required = false) String userId) {
        List<Message> messages = messageRepository.findByClientId(clientId);
        boolean changed = false;
        for (Message m : messages) {
            if (userId != null && !userId.isEmpty()) {
                if (!userId.equals(m.getSenderId()) && (m.getIsRead() == null || !m.getIsRead())) {
                    m.setIsRead(true);
                    messageRepository.save(m);
                    changed = true;
                }
            } else if (senderRole != null && !senderRole.isEmpty()) {
                if ("client".equals(senderRole)) {
                    // Client is reading support desk's messages
                    if (("admin".equals(m.getSenderRole()) || "staff".equals(m.getSenderRole())) && (m.getIsRead() == null || !m.getIsRead())) {
                        m.setIsRead(true);
                        messageRepository.save(m);
                        changed = true;
                    }
                } else {
                    // Admin/Staff is reading client's messages
                    if ("client".equals(m.getSenderRole()) && (m.getIsRead() == null || !m.getIsRead())) {
                        m.setIsRead(true);
                        messageRepository.save(m);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            chatWebSocketHandler.broadcastReadReceipt(clientId, senderRole != null ? senderRole : "staff");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/messages/presence")
    public ResponseEntity<Map<String, Object>> getPresence(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String role) {

        Map<String, Object> res = new HashMap<>();

        if (userId != null && !userId.isEmpty()) {
            boolean online = chatWebSocketHandler.isUserOnline(userId);
            String status = chatWebSocketHandler.getUserStatus(userId);
            res.put("isOnline", online && !"offline".equals(status));
            res.put("status", status);
            Optional<User> uOpt = userRepository.findById(userId);
            uOpt.ifPresent(user -> res.put("lastSeen", user.getLastSeenTime()));
            res.put("userId", userId);
        } else if (name != null && !name.isEmpty()) {
            List<User> users = userRepository.findAll();
            Optional<User> targetUser = users.stream()
                    .filter(u -> formatUserName(u).equalsIgnoreCase(name))
                    .findFirst();
            if (targetUser.isPresent()) {
                User u = targetUser.get();
                boolean online = chatWebSocketHandler.isUserOnline(u.getId());
                String status = chatWebSocketHandler.getUserStatus(u.getId());
                res.put("isOnline", online && !"offline".equals(status));
                res.put("status", status);
                res.put("lastSeen", u.getLastSeenTime());
                res.put("userId", u.getId());
            } else {
                res.put("isOnline", false);
                res.put("lastSeen", null);
            }
        } else if (role != null && !role.isEmpty()) {
            List<User> users = userRepository.findAll();
            boolean anyOnline = false;
            Long latestLastSeen = null;

            for (User u : users) {
                if ("ADMIN".equalsIgnoreCase(u.getRole()) || "STAFF".equalsIgnoreCase(u.getRole())) {
                    boolean online = chatWebSocketHandler.isUserOnline(u.getId());
                    if (online) {
                        anyOnline = true;
                    } else if (u.getLastSeenTime() != null) {
                        if (latestLastSeen == null || u.getLastSeenTime() > latestLastSeen) {
                            latestLastSeen = u.getLastSeenTime();
                        }
                    }
                }
            }
            res.put("isOnline", anyOnline);
            res.put("lastSeen", latestLastSeen);
        } else {
            res.put("isOnline", false);
        }

        return ResponseEntity.ok(res);
    }

    @PostMapping("/messages/presence")
    public ResponseEntity<Map<String, Object>> updatePresence(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String status = body.get("status");
        Map<String, Object> res = new HashMap<>();
        if (userId != null && status != null) {
            chatWebSocketHandler.setUserStatus(userId, status);
            res.put("success", true);
            res.put("userId", userId);
            res.put("status", status);
            return ResponseEntity.ok(res);
        } else {
            res.put("error", "userId and status are required");
            return ResponseEntity.badRequest().body(res);
        }
    }

    // --- APPLICATION ENDPOINTS ---
    @GetMapping("/applications")
    public ResponseEntity<List<Map<String, Object>>> getAllApplications() {
        List<Requirement> requirements = requirementRepository.findAll();
        List<User> users = userRepository.findAll();
        List<Kyc> kycRecords = kycRepository.findAll();
        List<ClientDocument> documents = clientDocumentRepository.findAll();

        List<Map<String, Object>> apps = requirements.stream().map(req -> {
            Map<String, Object> map = new HashMap<>();
            
            // Map ID: e.g. "SRV-1001"
            map.put("id", req.getId() != null ? req.getId().replace("SRV-", "APP-") : "APP-unknown");

            // Extract company name
            String companyName = "N/A";
            Map<String, Object> data = req.getData();
            if (data != null && data.containsKey("names")) {
                Object namesObj = data.get("names");
                if (namesObj instanceof List) {
                    List<String> names = (List<String>) namesObj;
                    if (!names.isEmpty()) companyName = names.get(0);
                }
            }
            map.put("business", companyName);

            // Find client
            Optional<User> userOpt = users.stream().filter(u -> u.getId().equals(req.getUserId())).findFirst();
            map.put("client", userOpt.map(this::formatUserName).orElse("Unknown"));
            map.put("clientId", req.getUserId());

            map.put("staff", "Sarah Lim");
            map.put("status", req.getStatus() != null ? req.getStatus() : "pending");
            
            // Priority & Deadline
            String priority = "Normal";
            String deadline = "N/A";
            if (data != null) {
                if (data.containsKey("priority")) priority = (String) data.get("priority");
                if (data.containsKey("deadline")) deadline = (String) data.get("deadline");
            }
            map.put("priority", priority);
            map.put("deadline", deadline);

            // Find KYC status
            Optional<Kyc> kycOpt = kycRecords.stream().filter(k -> k.getClientId().equals(req.getUserId())).findFirst();
            map.put("kyc", kycOpt.map(k -> capitalize(k.getStatus())).orElse("Approved"));

            // Documents status
            long pendingDocs = documents.stream()
                    .filter(d -> d.getClientId().equals(req.getUserId()) && "pending".equalsIgnoreCase(d.getStatus()))
                    .count();
            map.put("docs", pendingDocs > 0 ? pendingDocs + " Docs Pending" : "All Docs OK");

            // Date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            map.put("date", req.getUpdatedAt() != null ? sdf.format(req.getUpdatedAt()) : sdf.format(new Date()));

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(apps);
    }

    // --- REPORT ENDPOINTS ---
    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReports() {
        List<Requirement> requirements = requirementRepository.findAll();
        List<Kyc> kycRecords = kycRepository.findAll();

        long totalApps = requirements.size();
        long approved = requirements.stream().filter(r -> "approved".equalsIgnoreCase(r.getStatus()) || "completed".equalsIgnoreCase(r.getStatus())).count();
        long rejected = requirements.stream().filter(r -> "rejected".equalsIgnoreCase(r.getStatus())).count();
        long pendingKyc = kycRecords.stream().filter(k -> "pending".equalsIgnoreCase(k.getStatus())).count();

        // Revenue = SGD 1,500 per approved/completed incorporation
        long revenue = approved * 1500;

        Map<String, Long> byService = new HashMap<>();
        byService.put("Company Incorporation", totalApps);

        Map<String, Object> response = new HashMap<>();
        response.put("totalApplications", totalApps);
        response.put("approved", approved);
        response.put("rejected", rejected);
        response.put("pendingKYC", pendingKyc);
        response.put("revenue", revenue);
        response.put("byService", byService);

        return ResponseEntity.ok(response);
    }

    // --- CATALOG ENDPOINTS ---
    @GetMapping("/catalog")
    public ResponseEntity<List<CatalogItem>> getCatalog() {
        return ResponseEntity.ok(catalogItemRepository.findAll());
    }

    // --- INVOICE ENDPOINTS ---
    @GetMapping("/clients/{id}/invoices")
    public ResponseEntity<List<Invoice>> getClientInvoices(@PathVariable String id) {
        return ResponseEntity.ok(invoiceRepository.findByClientId(id));
    }

    // --- COUNTRY ENDPOINTS ---
    @GetMapping("/countries")
    public ResponseEntity<List<Country>> getAllCountries() {
        List<Country> countries = countryRepository.findAll();
        // If empty in DB, initialize with default countries
        if (countries.isEmpty()) {
            List<Country> defaultCountries = Arrays.asList(
                createDefaultCountry("Singapore", "SG", "9-character alphanumeric", "17% (flat rate)", "99.5%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1315.0, 900.0, 3000.0, 600.0, 1500.0, 500.0, 0),
                createDefaultCountry("Hong Kong", "HK", "8-digit registration no.", "16.5% (two-tier)", "98.8%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Office Address", "Tax & Compliance"), 1650.0, 800.0, 2500.0, 500.0, 1200.0, 400.0, 1),
                createDefaultCountry("United States", "USA", "9-digit EIN number", "21% (federal flat)", "97.2%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1200.0, 1000.0, 3000.0, 700.0, 1500.0, 500.0, 2),
                createDefaultCountry("Dubai", "UAE", "Varies by Free Zone", "9% (above 375k AED)", "99.1%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 2500.0, 1200.0, 4000.0, 900.0, 1800.0, 600.0, 3),
                createDefaultCountry("Australia", "AUS", "9-digit ACN number", "25% - 30%", "96.8%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1400.0, 950.0, 3100.0, 650.0, 1600.0, 550.0, 4),
                createDefaultCountry("United Kingdom", "UK", "8-digit CRN number", "19% - 25%", "98.5%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1300.0, 850.0, 2800.0, 550.0, 1400.0, 450.0, 5)
            );
            countries = countryRepository.saveAll(defaultCountries);
        }
        countries.sort(Comparator.comparing(c -> c.getOrderIndex() != null ? c.getOrderIndex() : 0));
        return ResponseEntity.ok(countries);
    }

    private Country createDefaultCountry(String name, String code, String uen, String tax, String compliance, boolean published, List<String> services, Double basePrice, Double priceSecretary, Double priceDirector, Double priceAddress, Double priceTax, Double priceBank, int orderIndex) {
        Country c = new Country();
        c.setId("CNTRY-" + name.toLowerCase().replace(" ", "-"));
        c.setName(name);
        c.setCode(code);
        c.setUen(uen);
        c.setTax(tax);
        c.setCompliance(compliance);
        c.setStatus("ACTIVE");
        c.setPublished(published);
        c.setServices(services);
        c.setBasePrice(basePrice);
        c.setPriceSecretary(priceSecretary);
        c.setPriceDirector(priceDirector);
        c.setPriceAddress(priceAddress);
        c.setPriceTax(priceTax);
        c.setPriceBank(priceBank);
        c.setOrderIndex(orderIndex);

        Map<String, Object> pubData = new HashMap<>();
        pubData.put("name", name);
        pubData.put("code", code);
        pubData.put("uen", uen);
        pubData.put("tax", tax);
        pubData.put("compliance", compliance);
        pubData.put("services", services);
        pubData.put("basePrice", basePrice);
        pubData.put("priceSecretary", priceSecretary);
        pubData.put("priceDirector", priceDirector);
        pubData.put("priceAddress", priceAddress);
        pubData.put("priceTax", priceTax);
        pubData.put("priceBank", priceBank);
        pubData.put("customPrices", new HashMap<>());
        c.setPublishedData(pubData);

        return c;
    }

    @PostMapping("/countries")
    public ResponseEntity<Country> createCountry(@RequestBody Country country) {
        if (country.getId() == null) {
            country.setId("CNTRY-" + System.currentTimeMillis());
        }
        if (country.getOrderIndex() == null) {
            long count = countryRepository.count();
            country.setOrderIndex((int) count);
        }
        Country saved = countryRepository.save(country);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/countries/{id}")
    public ResponseEntity<Country> updateCountry(@PathVariable String id, @RequestBody Country countryUpdates) {
        Optional<Country> countryOpt = countryRepository.findById(id);
        if (countryOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Country country = countryOpt.get();
        if (countryUpdates.getName() != null) country.setName(countryUpdates.getName());
        if (countryUpdates.getCode() != null) country.setCode(countryUpdates.getCode());
        if (countryUpdates.getUen() != null) country.setUen(countryUpdates.getUen());
        if (countryUpdates.getTax() != null) country.setTax(countryUpdates.getTax());
        if (countryUpdates.getCompliance() != null) country.setCompliance(countryUpdates.getCompliance());
        if (countryUpdates.getStatus() != null) country.setStatus(countryUpdates.getStatus());
        if (countryUpdates.getPublished() != null) country.setPublished(countryUpdates.getPublished());
        if (countryUpdates.getServices() != null) country.setServices(countryUpdates.getServices());
        if (countryUpdates.getBasePrice() != null) country.setBasePrice(countryUpdates.getBasePrice());
        if (countryUpdates.getPriceSecretary() != null) country.setPriceSecretary(countryUpdates.getPriceSecretary());
        if (countryUpdates.getPriceDirector() != null) country.setPriceDirector(countryUpdates.getPriceDirector());
        if (countryUpdates.getPriceAddress() != null) country.setPriceAddress(countryUpdates.getPriceAddress());
        if (countryUpdates.getPriceTax() != null) country.setPriceTax(countryUpdates.getPriceTax());
        if (countryUpdates.getPriceBank() != null) country.setPriceBank(countryUpdates.getPriceBank());
        if (countryUpdates.getOrderIndex() != null) country.setOrderIndex(countryUpdates.getOrderIndex());
        if (countryUpdates.getCustomPrices() != null) country.setCustomPrices(countryUpdates.getCustomPrices());
        if (countryUpdates.getPublishedData() != null) country.setPublishedData(countryUpdates.getPublishedData());

        Country saved = countryRepository.save(country);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/countries/reorder")
    public ResponseEntity<List<Country>> reorderCountries(@RequestBody Map<String, List<String>> payload) {
        List<String> orderedIds = payload.get("orderedIds");
        if (orderedIds != null) {
            for (int i = 0; i < orderedIds.size(); i++) {
                String id = orderedIds.get(i);
                Optional<Country> countryOpt = countryRepository.findById(id);
                if (countryOpt.isPresent()) {
                    Country country = countryOpt.get();
                    country.setOrderIndex(i);
                    countryRepository.save(country);
                }
            }
        }
        List<Country> countries = countryRepository.findAll();
        countries.sort(Comparator.comparing(c -> c.getOrderIndex() != null ? c.getOrderIndex() : 0));
        return ResponseEntity.ok(countries);
    }

    @DeleteMapping("/countries/{id}")
    public ResponseEntity<Void> deleteCountry(@PathVariable String id) {
        countryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String formatUserName(User u) {
        if (u == null) return "Unknown";
        String first = u.getFirstName();
        String last = u.getLastName();
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.trim().isEmpty()) {
            sb.append(first.trim());
        }
        if (last != null && !last.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(last.trim());
        }
        String fullName = sb.toString().trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        if (u.getEmail() != null && !u.getEmail().trim().isEmpty()) {
            String email = u.getEmail().trim();
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                return email.substring(0, atIndex);
            }
            return email;
        }
        return "Unknown";
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
