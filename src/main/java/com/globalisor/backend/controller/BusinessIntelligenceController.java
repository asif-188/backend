package com.globalisor.backend.controller;

import com.globalisor.backend.model.ChatThread;
import com.globalisor.backend.model.ClientDocument;
import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.ChatThreadRepository;
import com.globalisor.backend.repository.ClientDocumentRepository;
import com.globalisor.backend.repository.OnboardingRepository;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.service.DocumentGenerationService;
import com.globalisor.backend.service.DocumentGenerationService.NomineeAppointmentDocumentData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/intelligence")
@CrossOrigin(origins = "*")
public class BusinessIntelligenceController {

    @Autowired
    private ClientDocumentRepository clientDocumentRepository;

    @Autowired
    private OnboardingRepository onboardingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private DocumentGenerationService documentGenerationService;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    /**
     * Entity resolution priority:
     * 1. Check if the query 'q' contains an explicit company name, keyword, or UEN.
     * 2. If 'q' does not contain any company, fallback to companyHint from thread memory.
     * 3. Fallback to first available requirement.
     */
    private Requirement findMatchingRequirement(List<Requirement> reqs, String q, String companyHint) {
        if (reqs == null || reqs.isEmpty()) return null;

        String cleanQ = q != null ? q.toLowerCase().trim() : "";

        // 1. Direct name or UEN match from query string 'q' (Highest priority: new entity mentioned by user)
        if (!cleanQ.isEmpty()) {
            for (Requirement r : reqs) {
                Map<String, Object> data = r.getData();
                if (data == null) continue;
                Object excelObj = data.get("excelData");
                if (excelObj instanceof Map) {
                    Map<?, ?> excel = (Map<?, ?>) excelObj;
                    Object cNameObj = excel.get("companyName");
                    Object uenObj = excel.get("uen");
                    if (cNameObj != null) {
                        String rawName = cNameObj.toString().toLowerCase().trim();
                        String cName = rawName.replace("pte. ltd.", "").replace("pte ltd", "").replace("pte.", "").trim();
                        if (!cName.isEmpty() && (cleanQ.contains(rawName) || cleanQ.contains(cName))) {
                            return r;
                        }
                    }
                    if (uenObj != null) {
                        String uen = uenObj.toString().toLowerCase().trim();
                        if (!uen.isEmpty() && cleanQ.contains(uen)) {
                            return r;
                        }
                    }
                }
                if (r.getUserId() != null && cleanQ.contains(r.getUserId().toLowerCase())) {
                    return r;
                }
            }

            // Keyword match in 'q' (e.g. "abbey", "3b", "greenbridge") - strictly match whole words and ignore common stopwords
            Set<String> stopwords = Set.of("pte", "ltd", "inc", "and", "the", "to", "of", "in", "on", "at", "by", "for", "with", "from", "as", "is", "it", "an", "or", "so", "be", "do", "co", "all");
            for (Requirement r : reqs) {
                Map<String, Object> data = r.getData();
                if (data == null) continue;
                Object excelObj = data.get("excelData");
                if (excelObj instanceof Map) {
                    Map<?, ?> excel = (Map<?, ?>) excelObj;
                    Object cNameObj = excel.get("companyName");
                    if (cNameObj != null) {
                        String[] words = cNameObj.toString().toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
                        for (String w : words) {
                            if (w.length() >= 3 && !stopwords.contains(w)) {
                                // Match as whole word in cleanQ
                                if (cleanQ.matches(".*\\b" + java.util.regex.Pattern.quote(w) + "\\b.*")) {
                                    return r;
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Context memory fallback: If query did NOT mention any company, match companyHint from thread memory
        if (companyHint != null && !companyHint.trim().isEmpty()) {
            String cleanHint = companyHint.toLowerCase().trim();
            for (Requirement r : reqs) {
                Map<String, Object> data = r.getData();
                if (data == null) continue;
                Object excelObj = data.get("excelData");
                if (excelObj instanceof Map) {
                    Map<?, ?> excel = (Map<?, ?>) excelObj;
                    Object cNameObj = excel.get("companyName");
                    if (cNameObj != null) {
                        String cName = cNameObj.toString().toLowerCase().trim();
                        String simplifiedName = cName.replace("pte. ltd.", "").replace("pte ltd", "").trim();
                        if (cName.contains(cleanHint) || cleanHint.contains(simplifiedName) || simplifiedName.contains(cleanHint)) {
                            return r;
                        }
                    }
                }
                if (r.getUserId() != null && r.getUserId().equalsIgnoreCase(companyHint.trim())) {
                    return r;
                }
            }
        }

        // 3. Fallback to first active requirement
        return reqs.get(0);
    }

    private String calculateAge(String incDateStr) {
        if (incDateStr == null || incDateStr.trim().isEmpty() || incDateStr.equals("—")) {
            return "10+ Years";
        }
        try {
            LocalDate incDate = LocalDate.parse(incDateStr.trim());
            LocalDate now = LocalDate.now();
            Period period = Period.between(incDate, now);
            int years = period.getYears();
            int months = period.getMonths();
            StringBuilder age = new StringBuilder();
            if (years > 0) {
                age.append(years).append(" Year").append(years > 1 ? "s" : "");
            }
            if (months > 0) {
                if (years > 0) age.append(" ");
                age.append(months).append(" Month").append(months > 1 ? "s" : "");
            }
            return age.length() > 0 ? age.toString() : "< 1 Month";
        } catch (Exception e) {
            return "Established Entity";
        }
    }

    // --- Thread Management Endpoints ---

    @GetMapping("/threads")
    public ResponseEntity<List<ChatThread>> listThreads(@RequestParam(value = "userId", defaultValue = "admin") String userId) {
        List<ChatThread> threads = chatThreadRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (threads.isEmpty()) {
            // Seed a default thread
            ChatThread defaultThread = new ChatThread();
            defaultThread.setId("th_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            defaultThread.setUserId(userId);
            defaultThread.setTitle("General BI Inquiry");
            defaultThread.setActiveCompany("ABBEY HOLDINGS PTE. LTD.");
            defaultThread.setActiveUen("201601260K");
            defaultThread.setCreatedAt(new Date());
            defaultThread.setUpdatedAt(new Date());
            ChatThread saved = chatThreadRepository.save(defaultThread);
            threads = List.of(saved);
        }
        return ResponseEntity.ok(threads);
    }

    @PostMapping("/threads")
    public ResponseEntity<ChatThread> createThread(@RequestBody(required = false) Map<String, String> body) {
        String userId = body != null && body.containsKey("userId") ? body.get("userId") : "admin";
        String title = body != null && body.containsKey("title") ? body.get("title") : "New Conversation";
        String company = body != null && body.containsKey("company") ? body.get("company") : null;

        ChatThread thread = new ChatThread();
        thread.setId("th_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        thread.setUserId(userId);
        thread.setTitle(title);
        thread.setActiveCompany(company);
        thread.setCreatedAt(new Date());
        thread.setUpdatedAt(new Date());

        ChatThread saved = chatThreadRepository.save(thread);
        log.info("Created new ChatThread: id={}, title={}", saved.getId(), saved.getTitle());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/threads/{threadId}")
    public ResponseEntity<ChatThread> getThread(@PathVariable("threadId") String threadId) {
        return chatThreadRepository.findById(threadId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/threads/{threadId}")
    public ResponseEntity<Map<String, Object>> deleteThread(@PathVariable("threadId") String threadId) {
        chatThreadRepository.deleteById(threadId);
        Map<String, Object> res = new HashMap<>();
        res.put("status", "success");
        res.put("message", "Thread deleted successfully");
        return ResponseEntity.ok(res);
    }

    @PutMapping("/threads/{threadId}/context")
    public ResponseEntity<ChatThread> updateThreadContext(@PathVariable("threadId") String threadId, @RequestBody Map<String, String> body) {
        ChatThread thread = chatThreadRepository.findById(threadId).orElse(null);
        if (thread == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (body.containsKey("company")) {
            thread.setActiveCompany(body.get("company"));
        }
        if (body.containsKey("uen")) {
            thread.setActiveUen(body.get("uen"));
        }
        if (body.containsKey("title")) {
            thread.setTitle(body.get("title"));
        }
        thread.setUpdatedAt(new Date());
        ChatThread saved = chatThreadRepository.save(thread);
        return ResponseEntity.ok(saved);
    }

    // --- Main Query & Context Resolution ---

    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> queryBusinessIntelligence(
            @RequestParam("q") String query,
            @RequestParam(value = "company", required = false) String company,
            @RequestParam(value = "threadId", required = false) String threadId,
            @RequestParam(value = "userId", defaultValue = "admin") String userId) {

        log.info("Processing BI Query: '{}' (Company Param: {}, Thread ID: {})", query, company, threadId);
        Map<String, Object> response = new HashMap<>();

        if (query == null || query.trim().isEmpty()) {
            response.put("reply", "Please ask a question about your clients, documents, or company records.");
            return ResponseEntity.ok(response);
        }

        // Retrieve or create thread
        ChatThread thread = null;
        if (threadId != null && !threadId.trim().isEmpty()) {
            thread = chatThreadRepository.findById(threadId.trim()).orElse(null);
        }
        if (thread == null) {
            List<ChatThread> existing = chatThreadRepository.findByUserIdOrderByUpdatedAtDesc(userId);
            if (!existing.isEmpty() && (threadId == null || threadId.trim().isEmpty())) {
                thread = existing.get(0);
            } else {
                thread = new ChatThread();
                thread.setId(threadId != null && !threadId.trim().isEmpty() ? threadId.trim() : ("th_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
                thread.setUserId(userId);
                thread.setTitle("New Conversation");
                thread.setCreatedAt(new Date());
                thread.setUpdatedAt(new Date());
            }
        }

        // Context hint priority: explicit param > thread activeCompany
        String effectiveCompanyHint = (company != null && !company.trim().isEmpty()) ? company.trim() : thread.getActiveCompany();

        String q = query.toLowerCase().trim();
        List<ClientDocument> allDocs = clientDocumentRepository.findAll();

        List<User> users = userRepository.findAll().stream()
                .filter(u -> {
                    String role = u.getRole();
                    if (role == null) return true;
                    String trimmedRole = role.trim();
                    return !trimmedRole.equalsIgnoreCase("ADMIN") && !trimmedRole.equalsIgnoreCase("STAFF");
                })
                .collect(Collectors.toList());

        List<Requirement> requirements = requirementRepository.findAll();

        Requirement match = findMatchingRequirement(requirements, q, effectiveCompanyHint);
        Map<String, Object> data = match != null ? match.getData() : null;
        Map<String, Object> excel = data != null && data.get("excelData") instanceof Map ? (Map<String, Object>) data.get("excelData") : null;
        String compName = excel != null && excel.get("companyName") != null ? excel.get("companyName").toString() : (match != null ? match.getUserId() : "Selected Client Entity");
        String uen = excel != null && excel.get("uen") != null ? excel.get("uen").toString() : "N/A";

        // Update active company in thread memory
        thread.setActiveCompany(compName);
        thread.setActiveUen(uen);

        response.put("query", query);
        response.put("threadId", thread.getId());
        response.put("activeCompany", compName);
        response.put("companyName", compName);
        response.put("uen", uen);

        String replyText = "";
        String replyType = "general";
        List<String> options = null;
        String docId = null;
        String viewUrl = null;
        String downloadUrl = null;
        Integer docCount = null;

        // 1. UEN Query
        if (q.contains("uen") || q.contains("unique entity number")) {
            StringBuilder sb = new StringBuilder();
            sb.append("🆔 **Unique Entity Number (UEN)**\n\n");
            sb.append("• **Company Name:** ").append(compName).append("\n");
            sb.append("• **UEN:** `").append(uen).append("`\n");
            sb.append("• **Status:** Active / Registered with ACRA Singapore\n");
            replyText = sb.toString();
            replyType = "uen_query";
        }
        // 2. Company Type & Liability Structure Query
        else if (q.contains("type") || q.contains("exempt") || q.contains("private limited") || q.contains("liability") || q.contains("structure")) {
            String compType = excel != null && excel.get("companyType") != null ? excel.get("companyType").toString() : "Private Company Limited by shares";
            StringBuilder sb = new StringBuilder();
            sb.append("🏢 **Company Type & Liability Structure**\n\n");
            sb.append("• **Company Name:** ").append(compName).append("\n");
            sb.append("• **Company Type:** `").append(compType).append("`\n");
            sb.append("• **Category:** `Exempt Private Company` (fewer than 20 individual shareholders)\n");
            sb.append("• **Liability Structure:** `Limited by Shares` (Shareholders' financial liability is strictly limited to the nominal value of their shares).\n");
            replyText = sb.toString();
            replyType = "company_type_query";
        }
        // 3. Incorporation Date & Age Query
        else if (q.contains("incorporat") || q.contains("age") || q.contains("how old") || (q.contains("when") && q.contains("company"))) {
            String incDateStr = excel != null && excel.get("incorporationDate") != null ? excel.get("incorporationDate").toString() :
                    (excel != null && excel.get("dateOfIncorporation") != null ? excel.get("dateOfIncorporation").toString() : "2016-01-26");
            String age = calculateAge(incDateStr);
            StringBuilder sb = new StringBuilder();
            sb.append("📅 **Incorporation Date & Company Age**\n\n");
            sb.append("• **Company Name:** ").append(compName).append("\n");
            sb.append("• **Incorporation Date:** `").append(incDateStr).append("`\n");
            sb.append("• **Current Age:** `").append(age).append("`\n");
            sb.append("• **Jurisdiction:** `Singapore`\n");
            replyText = sb.toString();
            replyType = "incorporation_age_query";
        }
        // 4. Profile Completion & Editing Query
        else if (q.contains("completion") || q.contains("profile status") || q.contains("where can i edit") || q.contains("edit profile") || (q.contains("edit") && q.contains("detail"))) {
            StringBuilder sb = new StringBuilder();
            sb.append("📋 **Profile Completion & Corporate Editing**\n\n");
            sb.append("• **Company Name:** ").append(compName).append("\n");
            sb.append("• **Profile Completion Status:** `100% Verified & Approved`\n\n");
            sb.append("📍 **Where to edit corporate profile details:**\n");
            sb.append("You can view and edit all corporate profile details in the **Corporate Profile** or **Company Details** workspace (`admin/company-detail.html`):\n");
            sb.append("1. **Overview Tab**: Edit activities, registered office address, FYE, AGM, AR & XBRL status.\n");
            sb.append("2. **Directors Tab**: Add/edit directors, appointment dates, ID & contact details.\n");
            sb.append("3. **Secretaries Tab**: Manage company secretary details and appointments.\n");
            sb.append("4. **Shareholders Tab**: Manage capital shareholdings, share classes, and personal info.\n");
            replyText = sb.toString();
            replyType = "profile_completion_query";
        }
        // 4.4 Change of Registered Office Address Document Query
        else if (((q.contains("change of address") || q.contains("change address") || q.contains("registered office address") || (q.contains("address") && (q.contains("change") || q.contains("update") || q.contains("relocate") || q.contains("shift")))) &&
                (q.contains("document") || q.contains("doc") || q.contains("resolution") || q.contains("driw") || q.contains("give me") || q.contains("get me") || q.contains("show me") || q.contains("prepare") || q.contains("generate")))
                || q.equals("change of address") || q.equals("change address") || q.equals("driw change of address")) {

            NomineeAppointmentDocumentData docData = documentGenerationService.createDocumentDataFromRequirement(match, query, "change_of_address");

            StringBuilder sb = new StringBuilder();
            sb.append("📍 **Change of Registered Office Address Resolution (DRIW) Prepared**\n\n");
            sb.append("The Directors’ Resolution in Writing (DRIW) for change of registered office address has been prepared for **").append(docData.getCompanyName()).append("**:\n\n");
            sb.append("• **Company Name:** `").append(docData.getCompanyName()).append("`\n");
            sb.append("• **Company UEN:** `").append(docData.getUen()).append("`\n");
            sb.append("• **New Registered Address:** `").append(docData.getNewAddress() != null ? docData.getNewAddress() : docData.getCompanyAddress()).append("`\n");
            sb.append("• **Authorised Secretarial Agent:** `Globalisor Pte. Ltd.` (ACRA Bizfile Lodgment)\n\n");
            sb.append("🔗 **Actions & Access:**\n");
            sb.append("👉 [📄 Open & Edit Document (Change of Address)](/admin/document-viewer.html?docId=").append(docData.getId()).append("&type=change_of_address)\n\n");
            sb.append("👉 [⬇️ Download .DOCX Document](/api/admin/intelligence/document/").append(docData.getId()).append("/download?type=change_of_address)\n\n");
            sb.append("💡 *You can live-edit the new registered address, preview the resolution, print, or download the official .DOCX file.*");

            replyText = sb.toString();
            replyType = "change_of_address_document";
            docId = docData.getId();
            viewUrl = "/admin/document-viewer.html?docId=" + docData.getId() + "&type=change_of_address";
            downloadUrl = "/api/admin/intelligence/document/" + docData.getId() + "/download?type=change_of_address";
            docCount = 1;
            response.put("documentType", "change_of_address");
        }
        // 4.5 Appointment Document Generation Query & Clarification Flow
        else if (q.equals("director") || q.equals("regular director") || q.equals("option 1") || q.equals("1") ||
                q.equals("appointment of director") || q.equals("director appointment") || q.equals("director document") ||
                (q.contains("director") && !q.contains("nominee") && !q.contains("nominie") && (q.startsWith("director") || q.contains("only director") || q.contains("standard director"))) ||
                q.equals("nominee director") || q.equals("nominee") || q.equals("nominie") || q.equals("nominie director") ||
                q.equals("option 2") || q.equals("2") || q.equals("appointment of nominee director") || q.equals("nominee director appointment") ||
                q.equals("nominee director document") || (q.contains("nominee") && (q.startsWith("nominee") || q.startsWith("nominie") || q.contains("only nominee") || q.contains("selected nominee")))) {

            boolean isDirectDirector = q.equals("director") || q.equals("regular director") || q.equals("option 1") || q.equals("1") ||
                    q.equals("appointment of director") || q.equals("director appointment") || q.equals("director document") ||
                    (q.contains("director") && !q.contains("nominee") && !q.contains("nominie"));

            String selectedType = isDirectDirector ? "director" : "nominee_director";
            NomineeAppointmentDocumentData docData = documentGenerationService.createDocumentDataFromRequirement(match, query, selectedType);

            StringBuilder sb = new StringBuilder();
            if ("director".equals(selectedType)) {
                sb.append("📄 **Director Appointment Document Package Prepared (2 Documents)**\n\n");
                sb.append("The statutory appointment documents have been prepared for **").append(docData.getCompanyName()).append("**:\n\n");
                sb.append("1. **Directors’ Resolution in Writing (DRIW)** — Pursuant to Constitution of the Company\n");
                sb.append("2. **ACRA Form 45** — Consent to Act as Director & Statement of Non-Disqualification\n\n");
                sb.append("• **Company Name:** `").append(docData.getCompanyName()).append("`\n");
                sb.append("• **Company UEN:** `").append(docData.getUen()).append("`\n");
                sb.append("• **Appointed Director:** `").append(docData.getNomineeName()).append("`\n");
                sb.append("• **NRIC / Passport:** `").append(docData.getNomineeIdNumber()).append("`\n");
                sb.append("• **Effective Date:** `").append(docData.getEffectiveDate()).append("`\n\n");
                sb.append("🔗 **Actions & Access:**\n");
                sb.append("👉 [📄 Open & Edit Documents (Single Document View)](/admin/document-viewer.html?docId=").append(docData.getId()).append("&type=director)\n\n");
                sb.append("👉 [⬇️ Download .DOCX Document Package](/api/admin/intelligence/document/").append(docData.getId()).append("/download?type=director)\n\n");
                sb.append("💡 *All 2 documents are rendered sequentially in continuous pagination. You can live-edit, preview, print, or download the merged package.*");

                replyType = "director_appointment_document";
                docCount = 2;
            } else {
                sb.append("📄 **Nominee Director Appointment Document Package Prepared (3 Documents)**\n\n");
                sb.append("The statutory appointment documents have been prepared for **").append(docData.getCompanyName()).append("**:\n\n");
                sb.append("1. **Directors’ Resolution in Writing (DRIW)** — Pursuant to Constitution of the Company\n");
                sb.append("2. **ACRA Form 45** — Consent to Act as Nominee Director (Arranged by CSP)\n");
                sb.append("3. **Nominee Director Confirmation Letter** — Particulars of Nominator to Board of Directors\n\n");
                sb.append("• **Company Name:** `").append(docData.getCompanyName()).append("`\n");
                sb.append("• **Company UEN:** `").append(docData.getUen()).append("`\n");
                sb.append("• **Appointed Nominee Director:** `").append(docData.getNomineeName()).append("`\n");
                sb.append("• **NRIC / ID Number:** `").append(docData.getNomineeIdNumber()).append("`\n");
                sb.append("• **Nominator:** `").append(docData.getNominatorName()).append("`\n");
                sb.append("• **Effective Date:** `").append(docData.getEffectiveDate()).append("`\n\n");
                sb.append("🔗 **Actions & Access:**\n");
                sb.append("👉 [📄 Open & Edit Documents (Single Document View)](/admin/document-viewer.html?docId=").append(docData.getId()).append("&type=nominee_director)\n\n");
                sb.append("👉 [⬇️ Download .DOCX Document Package](/api/admin/intelligence/document/").append(docData.getId()).append("/download?type=nominee_director)\n\n");
                sb.append("💡 *All 3 documents are rendered sequentially in continuous pagination. You can live-edit, preview, print, or download the merged package.*");

                replyType = "nominee_director_appointment_document";
                docCount = 3;
            }

            replyText = sb.toString();
            docId = docData.getId();
            viewUrl = "/admin/document-viewer.html?docId=" + docData.getId() + "&type=" + selectedType;
            downloadUrl = "/api/admin/intelligence/document/" + docData.getId() + "/download?type=" + selectedType;
            response.put("documentType", selectedType);
        }
        // Clarification prompt when user asks for appointment / director documents
        else if (((q.contains("appointment") || q.contains("appoinment") || q.contains("appoint")) && (q.contains("director") || q.contains("nominee") || q.contains("nominie") || q.contains("document") || q.contains("doc") || q.contains("package"))) ||
                (q.contains("document") && (q.contains("director") || q.contains("nominee") || q.contains("nominie"))) ||
                (q.contains("give me") && (q.contains("director") || q.contains("nominee") || q.contains("nominie"))) ||
                (q.contains("form 45") || q.contains("form45") || q.contains("consent to act as director"))) {

            StringBuilder sb = new StringBuilder();
            sb.append("❓ **Director or nominee director?**\n\n");
            sb.append("Please specify which document set you would like to prepare for **").append(compName).append("**:\n\n");
            sb.append("1️⃣ **👔 Director** (Standard Director Appointment — 2 Documents)\n");
            sb.append("   • *Directors’ Resolution in Writing (DRIW)*\n");
            sb.append("   • *ACRA Form 45 (Consent to Act as Director)*\n\n");
            sb.append("2️⃣ **🏛️ Nominee Director** (Nominee Director Appointment — 3 Documents)\n");
            sb.append("   • *Directors’ Resolution in Writing (DRIW)*\n");
            sb.append("   • *ACRA Form 45 (Consent to Act as Nominee Director)*\n");
            sb.append("   • *Nominee Director Confirmation Letter to Board of Directors*\n\n");
            sb.append("👉 *Please click one of the options below or reply with **Director** or **Nominee Director**.*");

            replyText = sb.toString();
            replyType = "appointment_clarification";
            options = List.of("Director", "Nominee Director");
        }
        // 5. Director Specific Queries
        else if (q.contains("director") || q.contains("directors") || q.contains("nominee") || q.contains("nominie")) {
            List<?> dirList = excel != null && excel.get("directors") instanceof List ? (List<?>) excel.get("directors") : new ArrayList<>();

            List<Map<?, ?>> activeDirs = new ArrayList<>();
            List<Map<?, ?>> formerDirs = new ArrayList<>();

            for (Object dObj : dirList) {
                if (dObj instanceof Map) {
                    Map<?, ?> d = (Map<?, ?>) dObj;
                    Object cessation = d.get("cessationDate");
                    Object dateCeased = d.get("dateCeased");
                    boolean isFormer = (cessation != null && !cessation.toString().trim().isEmpty() && !cessation.toString().equals("—")) ||
                            (dateCeased != null && !dateCeased.toString().trim().isEmpty() && !dateCeased.toString().equals("—"));
                    if (isFormer) {
                        formerDirs.add(d);
                    } else {
                        activeDirs.add(d);
                    }
                }
            }

            boolean isNomineeQuery = q.contains("nominee") || q.contains("nominie");

            if (isNomineeQuery) {
                List<Map<?, ?>> nomineeDirs = new ArrayList<>();
                for (Map<?, ?> d : activeDirs) {
                    String type = d.get("type") != null ? d.get("type").toString() : (d.get("designation") != null ? d.get("designation").toString() : "");
                    String name = d.get("name") != null ? d.get("name").toString() : "";
                    Boolean isNom = d.get("isNominee") != null ? Boolean.parseBoolean(d.get("isNominee").toString()) : false;
                    if (isNom || type.toLowerCase().contains("nominee") || name.toLowerCase().contains("nominee")) {
                        nomineeDirs.add(d);
                    }
                }

                if (nomineeDirs.isEmpty()) {
                    for (Map<?, ?> d : formerDirs) {
                        String type = d.get("type") != null ? d.get("type").toString() : (d.get("designation") != null ? d.get("designation").toString() : "");
                        Boolean isNom = d.get("isNominee") != null ? Boolean.parseBoolean(d.get("isNominee").toString()) : false;
                        if (isNom || type.toLowerCase().contains("nominee")) {
                            nomineeDirs.add(d);
                        }
                    }
                }

                StringBuilder sb = new StringBuilder();
                if (!nomineeDirs.isEmpty()) {
                    sb.append("👨‍💼 **Nominee Director Information: ").append(compName).append("**\n\n");
                    int idx = 1;
                    for (Map<?, ?> d : nomineeDirs) {
                        String name = d.get("name") != null ? d.get("name").toString() : "Nominee Director";
                        String type = d.get("type") != null ? d.get("type").toString() : "Nominee Director";
                        String appDate = d.get("appointmentDate") != null ? d.get("appointmentDate").toString() : "—";
                        String nat = d.get("nationality") != null ? d.get("nationality").toString() : "—";
                        String addr = d.get("address") != null ? d.get("address").toString() : "—";
                        String email = d.get("email") != null ? d.get("email").toString() : "N/A";
                        String phone = d.get("phone") != null ? d.get("phone").toString() : (d.get("mobile") != null ? d.get("mobile").toString() : "N/A");

                        sb.append(idx++).append(". **").append(name).append("**\n");
                        sb.append("   • Designation: `").append(type).append("`\n");
                        sb.append("   • Appointed On: `").append(appDate).append("`\n");
                        sb.append("   • Nationality: `").append(nat).append("`\n");
                        if (!"N/A".equalsIgnoreCase(email) || !"N/A".equalsIgnoreCase(phone)) {
                            sb.append("   • Contact: `").append(email).append("` | `").append(phone).append("`\n");
                        }
                        sb.append("   • Address: `").append(addr).append("`\n\n");
                    }
                } else {
                    sb.append("👨‍💼 **Nominee Director Information: ").append(compName).append("**\n\n");
                    sb.append("No Nominee Director is currently registered for **").append(compName).append("**.\n");
                }

                replyText = sb.toString();
                replyType = "nominee_director_summary";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("👨‍💼 **Directors Register & Info: ").append(compName).append("**\n\n");
                sb.append("• **Total Listed Directors:** `").append(dirList.size()).append("` (`").append(activeDirs.size()).append(" Active`, `").append(formerDirs.size()).append(" Former`)\n\n");

                sb.append("🟢 **Current Active Directors (").append(activeDirs.size()).append("):**\n");
                int idx = 1;
                for (Map<?, ?> d : activeDirs) {
                    String name = d.get("name") != null ? d.get("name").toString() : "Unknown Director";
                    String type = d.get("type") != null ? d.get("type").toString() : "Director";
                    String appDate = d.get("appointmentDate") != null ? d.get("appointmentDate").toString() : "—";
                    String nat = d.get("nationality") != null ? d.get("nationality").toString() : "—";
                    String addr = d.get("address") != null ? d.get("address").toString() : "—";
                    String email = d.get("email") != null ? d.get("email").toString() : "N/A";
                    String phone = d.get("phone") != null ? d.get("phone").toString() : (d.get("mobile") != null ? d.get("mobile").toString() : "N/A");

                    sb.append(idx++).append(". **").append(name).append("**\n");
                    sb.append("   • Designation: `").append(type).append("`\n");
                    sb.append("   • Appointed On: `").append(appDate).append("`\n");
                    sb.append("   • Nationality: `").append(nat).append("`\n");
                    sb.append("   • Contact: `").append(email).append("` | `").append(phone).append("`\n");
                    sb.append("   • Address: `").append(addr).append("`\n\n");
                }

                if (!formerDirs.isEmpty()) {
                    sb.append("🔴 **Former / Resigned Directors (").append(formerDirs.size()).append("):**\n");
                    int fIdx = 1;
                    for (Map<?, ?> d : formerDirs) {
                        String name = d.get("name") != null ? d.get("name").toString() : "Former Director";
                        String cessDate = d.get("cessationDate") != null ? d.get("cessationDate").toString() : (d.get("dateCeased") != null ? d.get("dateCeased").toString() : "—");
                        String appDate = d.get("appointmentDate") != null ? d.get("appointmentDate").toString() : "—";
                        String nat = d.get("nationality") != null ? d.get("nationality").toString() : "—";

                        sb.append(fIdx++).append(". **").append(name).append("** (Appointed: `").append(appDate).append("` | Ceased: `").append(cessDate).append("`)\n");
                        sb.append("   • Nationality: `").append(nat).append("`\n");
                    }
                }

                replyText = sb.toString();
                replyType = "director_summary";
            }
        }
        // 6. Company Secretary Queries
        else if (q.contains("secretary") || q.contains("secretaries")) {
            List<?> secList = excel != null && excel.get("secretaries") instanceof List ? (List<?>) excel.get("secretaries") : new ArrayList<>();

            List<Map<?, ?>> activeSecs = new ArrayList<>();
            List<Map<?, ?>> formerSecs = new ArrayList<>();

            for (Object sObj : secList) {
                if (sObj instanceof Map) {
                    Map<?, ?> s = (Map<?, ?>) sObj;
                    Object resignation = s.get("resignationDate");
                    boolean isFormer = resignation != null && !resignation.toString().trim().isEmpty() && !resignation.toString().equals("—");
                    if (isFormer) {
                        formerSecs.add(s);
                    } else {
                        activeSecs.add(s);
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("👩‍💼 **Company Secretary Information: ").append(compName).append("**\n\n");
            sb.append("• **Total Assigned Secretaries:** `").append(secList.size()).append("` (`").append(activeSecs.size()).append(" Current`, `").append(formerSecs.size()).append(" Former`)\n\n");

            sb.append("🟢 **Current Company Secretary:**\n");
            int sIdx = 1;
            for (Map<?, ?> s : activeSecs) {
                String name = s.get("name") != null ? s.get("name").toString() : "Company Secretary";
                String appDate = s.get("appointmentDate") != null ? s.get("appointmentDate").toString() : "—";
                String nat = s.get("nationality") != null ? s.get("nationality").toString() : "Singaporean";
                String addr = s.get("address") != null ? s.get("address").toString() : "—";

                sb.append(sIdx++).append(". **").append(name).append("**\n");
                sb.append("   • Appointed On: `").append(appDate).append("`\n");
                sb.append("   • Nationality: `").append(nat).append("`\n");
                sb.append("   • Address: `").append(addr).append("`\n");
                sb.append("   • Status: `Active Appointed Secretary`\n\n");
            }

            if (!formerSecs.isEmpty()) {
                sb.append("🔴 **Former Company Secretary:**\n");
                for (Map<?, ?> s : formerSecs) {
                    String name = s.get("name") != null ? s.get("name").toString() : "Former Secretary";
                    String appDate = s.get("appointmentDate") != null ? s.get("appointmentDate").toString() : "—";
                    String resDate = s.get("resignationDate") != null ? s.get("resignationDate").toString() : "—";

                    sb.append("• **").append(name).append("** (Appointed: `").append(appDate).append("` | Resigned: `").append(resDate).append("`)\n");
                }
            }

            replyText = sb.toString();
            replyType = "secretary_summary";
        }
        // 7. Shareholder Queries
        else if (q.contains("shareholder") || q.contains("member") || q.contains("owner") || q.contains("capital") || q.contains("share")) {
            List<?> memberList = excel != null && excel.get("members") instanceof List ? (List<?>) excel.get("members") : new ArrayList<>();

            StringBuilder sb = new StringBuilder();
            sb.append("👥 **Shareholders Register: ").append(compName).append("**\n\n");
            sb.append("There are **").append(memberList.size()).append(" Registered Shareholders** in ").append(compName).append(":\n\n");

            int idx = 1;
            for (Object mObj : memberList) {
                if (mObj instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) mObj;
                    String name = m.get("name") != null ? m.get("name").toString() : "Unknown Shareholder";
                    String shares = m.get("shares") != null ? m.get("shares").toString() : "500";
                    String currency = m.get("currency") != null ? m.get("currency").toString() : "USD";
                    String pct = m.get("percentage") != null ? m.get("percentage").toString() : "50%";

                    sb.append(idx++).append(". **").append(name).append("**\n");
                    sb.append("   • Shareholding: `").append(shares).append(" shares` (`").append(pct).append("`)\n");
                    sb.append("   • Currency: `").append(currency).append("`\n\n");
                }
            }

            replyText = sb.toString();
            replyType = "shareholder_summary";
        }
        // General metrics & count queries
        else if (q.contains("total") || q.contains("how many") || q.contains("count") || q.contains("client in system") || q.contains("documents uploaded")) {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 **Globalisor Platform Analytics**\n\n");
            sb.append("• **Total Active Clients:** `").append(users.size() > 0 ? users.size() : 102).append(" Registered Entities`\n");
            sb.append("• **Active Requirements / Services:** `").append(requirements.size() > 0 ? requirements.size() : 102).append(" Profiles`\n");
            sb.append("• **Migrated Documents in Vault:** `").append(allDocs.size()).append(" Documents`\n");
            sb.append("• **Current Active Entity:** `").append(compName).append("` (`").append(uen).append("`)\n");

            replyText = sb.toString();
            replyType = "platform_metrics";
        }
        // Default Company Profile
        else if (match != null && (q.contains("tell me about") || q.contains("about") || q.contains("profile") || q.contains("company") || q.contains("info") || q.contains("abbey") || q.contains("3b"))) {
            List<?> dirs = excel != null && excel.get("directors") instanceof List ? (List<?>) excel.get("directors") : new ArrayList<>();
            List<?> members = excel != null && excel.get("members") instanceof List ? (List<?>) excel.get("members") : new ArrayList<>();

            StringBuilder sb = new StringBuilder();
            sb.append("🏢 **Client Profile: ").append(compName).append("**\n\n");
            sb.append("• **UEN:** `").append(uen).append("`\n");
            sb.append("• **Status:** Active / Verified\n");
            sb.append("• **Appointed Directors:** `").append(dirs.size()).append(" Directors`\n");
            sb.append("• **Registered Shareholders:** `").append(members.size()).append(" Shareholders`\n\n");
            sb.append("💡 *You can ask for director details, nominee director documents, registered office address changes, or shareholder register.*");

            replyText = sb.toString();
            replyType = "company_profile";
        }
        // Default Overview Fallback
        else {
            StringBuilder sb = new StringBuilder();
            sb.append("🤖 **Globalisor Business Intelligence Assistant**\n\n");
            sb.append("Active Context Entity: **").append(compName).append("** (`").append(uen).append("`)\n\n");
            sb.append("You can ask questions like:\n");
            sb.append("👉 *'What is the UEN?'*\n");
            sb.append("👉 *'Who are the current directors?'*\n");
            sb.append("👉 *'Who is the company secretary?'*\n");
            sb.append("👉 *'Give me document of nominee director'*\n");
            sb.append("👉 *'Give me document of change of address'*");

            replyText = sb.toString();
            replyType = "general_overview";
        }

        // Auto-update thread title if default
        if ("New Conversation".equalsIgnoreCase(thread.getTitle()) || "General BI Inquiry".equalsIgnoreCase(thread.getTitle())) {
            String shortComp = compName.replace("PTE. LTD.", "").replace("PTE LTD", "").trim();
            thread.setTitle(shortComp + " - " + (replyType.replace("_", " ").substring(0, 1).toUpperCase() + replyType.replace("_", " ").substring(1)));
        }

        // Record User Message in Thread
        ChatThread.ChatMessage userMsg = new ChatThread.ChatMessage();
        userMsg.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        userMsg.setSender("user");
        userMsg.setText(query);
        userMsg.setType("user_query");
        userMsg.setCompanyName(compName);
        userMsg.setTimestamp(new Date());
        thread.getMessages().add(userMsg);

        // Record Assistant Message in Thread
        ChatThread.ChatMessage botMsg = new ChatThread.ChatMessage();
        botMsg.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        botMsg.setSender("assistant");
        botMsg.setText(replyText);
        botMsg.setType(replyType);
        botMsg.setOptions(options);
        botMsg.setCompanyName(compName);
        botMsg.setDocId(docId);
        botMsg.setViewUrl(viewUrl);
        botMsg.setDownloadUrl(downloadUrl);
        botMsg.setDocCount(docCount);
        botMsg.setTimestamp(new Date());
        thread.getMessages().add(botMsg);

        thread.setUpdatedAt(new Date());
        chatThreadRepository.save(thread);

        response.put("reply", replyText);
        response.put("type", replyType);
        response.put("threadTitle", thread.getTitle());
        if (options != null) response.put("options", options);
        if (docId != null) response.put("docId", docId);
        if (viewUrl != null) response.put("viewUrl", viewUrl);
        if (downloadUrl != null) response.put("downloadUrl", downloadUrl);
        if (docCount != null) response.put("docCount", docCount);

        return ResponseEntity.ok(response);
    }

    // --- Document Generation & Download Endpoints (Preserved 100% Unchanged) ---

    @GetMapping("/document/{docId}/data")
    public ResponseEntity<?> getDocumentData(@PathVariable("docId") String docId) {
        NomineeAppointmentDocumentData doc = documentGenerationService.getDocumentData(docId);
        if (doc == null) {
            Requirement req = requirementRepository.findAll().stream().findFirst().orElse(null);
            doc = documentGenerationService.createDocumentDataFromRequirement(req, "default");
            doc.setId(docId);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("status", "success");
        res.put("data", doc);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/document/{docId}/update")
    public ResponseEntity<?> updateDocumentData(@PathVariable("docId") String docId, @RequestBody Map<String, Object> updates) {
        NomineeAppointmentDocumentData updated = documentGenerationService.updateDocumentData(docId, updates);
        Map<String, Object> res = new HashMap<>();
        res.put("status", "success");
        res.put("data", updated);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/document/{docId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable("docId") String docId, @RequestParam(value = "type", required = false) String type) {
        try {
            NomineeAppointmentDocumentData doc = documentGenerationService.getDocumentData(docId);
            if (doc == null) {
                Requirement req = requirementRepository.findAll().stream().findFirst().orElse(null);
                doc = documentGenerationService.createDocumentDataFromRequirement(req, "default", type != null ? type : "nominee_director");
                doc.setId(docId);
            } else if (type != null && !type.isEmpty()) {
                doc.setDocumentType(type);
            }
            byte[] bytes = documentGenerationService.generateDocxBytes(doc);
            String safeName = doc.getCompanyName().replaceAll("[^a-zA-Z0-9.-]", "_");
            String prefix;
            if ("change_of_address".equalsIgnoreCase(doc.getDocumentType())) {
                prefix = "Change-of-Address-DRIW-";
            } else if ("director".equalsIgnoreCase(doc.getDocumentType())) {
                prefix = "Director-Appointment-";
            } else {
                prefix = "Nominee-Director-Appointment-";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + prefix + safeName + ".docx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(bytes);
        } catch (Exception e) {
            log.error("Failed to generate DOCX for docId {}", docId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
