package com.globalisor.backend.controller;

import com.globalisor.backend.model.Onboarding;
import com.globalisor.backend.model.OnboardingConfig;
import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.OnboardingRepository;
import com.globalisor.backend.repository.OnboardingConfigRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.service.NotificationService;
import com.globalisor.backend.service.EmailService;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    @org.springframework.beans.factory.annotation.Value("${gemini.api.key:}")
    private String configuredGeminiApiKey;

    @Autowired OnboardingRepository onboardingRepository;
    @Autowired OnboardingConfigRepository onboardingConfigRepository;
    @Autowired UserRepository userRepository;
    @Autowired NotificationService notificationService;
    @Autowired ChatWebSocketHandler chatWebSocketHandler;
    @Autowired RequirementRepository requirementRepository;

    // GET all onboardings (admin)
    @GetMapping
    public ResponseEntity<List<Onboarding>> getAllOnboardings() {
        return ResponseEntity.ok(onboardingRepository.findAllByOrderByCreatedAtDesc());
    }

    // GET onboarding by client id
    @GetMapping("/client/{clientId}")
    public ResponseEntity<?> getByClientId(@PathVariable String clientId) {
        Optional<Onboarding> opt = onboardingRepository.findByClientId(clientId);
        Onboarding ob;
        boolean isNew = false;
        if (opt.isPresent()) {
            ob = opt.get();
        } else {
            ob = new Onboarding();
            ob.setClientId(clientId);
            ob.setDisplayClientId(generateNextDisplayClientId());
            ob.setStatus("in_progress");
            Optional<com.globalisor.backend.model.User> uOpt = userRepository.findById(clientId);
            if (uOpt.isPresent()) {
                com.globalisor.backend.model.User u = uOpt.get();
                ob.setClientEmail(u.getEmail());
                ob.setClientName((u.getFirstName() + " " + u.getLastName()).trim());
            }
            ob.getAuditLogs().add("Onboarding initiated automatically at " + new Date());
            isNew = true;
        }

        // Fetch requirement data for this client
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(clientId);
        if (reqOpt.isPresent()) {
            Requirement requirement = reqOpt.get();
            Map<String, Object> reqData = requirement.getData();
            if (reqData != null) {
                boolean changed = false;
                if (reqData.containsKey("journeyType")) {
                    String jType = (String) reqData.get("journeyType");
                    if (jType != null && !jType.equals(ob.getJourneyType())) {
                        ob.setJourneyType(jType);
                        changed = true;
                    }
                }

                // --- Sync Share Capital ---
                Onboarding.OnboardingStep capStep = ob.getStepShareCapital();
                if (capStep.getData() == null) capStep.setData(new HashMap<>());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> capCurrencies = (List<Map<String, Object>>) capStep.getData().get("currencies");
                if (capCurrencies == null) capCurrencies = new ArrayList<>();

                @SuppressWarnings("unchecked")
                Map<String, Object> reqCapital = (Map<String, Object>) reqData.get("capital");
                if (reqCapital != null && capCurrencies.isEmpty()) {
                    Map<String, Object> c = new HashMap<>();
                    String rCurr = (String) reqCapital.getOrDefault("currency", "SGD");
                    c.put("currency", rCurr);
                    c.put("customCurrency", "");
                    c.put("shareClass", reqCapital.getOrDefault("type", "Ordinary"));
                    c.put("numberOfShares", String.valueOf(reqCapital.getOrDefault("numShares", "")));
                    c.put("shareCapitalAmount", String.valueOf(reqCapital.getOrDefault("issued", "")));
                    c.put("paidUpShareCapital", String.valueOf(reqCapital.getOrDefault("paidUp", "")));
                    capCurrencies.add(c);
                    capStep.getData().put("currencies", capCurrencies);
                    changed = true;
                }

                // --- Sync Directors ---
                List<?> reqDirs = (List<?>) reqData.get("directors");
                if (reqDirs == null) reqDirs = new ArrayList<>();
                Onboarding.OnboardingStep dirStep = ob.getStep2DirectorDetails();
                if (dirStep.getData() == null) dirStep.setData(new HashMap<>());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> currentDirs = (List<Map<String, Object>>) dirStep.getData().get("list");
                if (currentDirs == null) currentDirs = new ArrayList<>();

                List<Map<String, Object>> newList = new ArrayList<>();
                for (int i = 0; i < reqDirs.size(); i++) {
                    Map<String, Object> existing = i < currentDirs.size() ? currentDirs.get(i) : new HashMap<>();
                    Object rDirObj = reqDirs.get(i);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rDir = (rDirObj instanceof Map) ? (Map<String, Object>) rDirObj : new HashMap<>();

                    Map<String, Object> item = new HashMap<>();
                    item.put("fullName", getMergedValue(existing, "fullName", rDir, "name"));
                    item.put("idNumber", getMergedValue(existing, "idNumber", rDir, "idNum"));
                    item.put("nationality", getMergedValue(existing, "nationality", rDir, "nation"));
                    item.put("gender", getMergedValue(existing, "gender", rDir, "gender"));
                    item.put("dateOfBirth", getMergedValue(existing, "dateOfBirth", rDir, "dob"));
                    item.put("residentialAddress", getMergedValue(existing, "residentialAddress", rDir, "addr"));
                    item.put("useDifferentAddress", getMergedObject(existing, "useDifferentAddress", rDir, "useDifferentAddress", false));
                    item.put("alternativeAddress", getMergedValue(existing, "alternativeAddress", rDir, "alternativeAddress"));
                    item.put("email", getMergedValue(existing, "email", rDir, "email"));
                    item.put("mobile", getMergedValue(existing, "mobile", rDir, "phone"));
                    item.put("idType", getMergedValue(existing, "idType", rDir, "idType"));
                    item.put("source", getMergedValue(existing, "source", rDir, "source"));
                    item.put("passportExpiry", getMergedValue(existing, "passportExpiry", rDir, "passportExpiry"));
                    item.put("disqualificationAcknowledge", getMergedObject(existing, "disqualificationAcknowledge", rDir, "disqualificationAcknowledge", false));
                    newList.add(item);
                }
                if (newList.isEmpty()) newList.add(new HashMap<>());
                if (!newList.equals(currentDirs)) {
                    dirStep.getData().put("list", newList);
                    changed = true;
                }

                // --- Sync Shareholders ---
                List<?> reqShs = (List<?>) reqData.get("shareholders");
                if (reqShs == null) reqShs = new ArrayList<>();
                List<Map<String, Object>> reqInds = new ArrayList<>();
                List<Map<String, Object>> reqCorps = new ArrayList<>();
                for (Object shObj : reqShs) {
                    if (shObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sh = (Map<String, Object>) shObj;
                        if ("individual".equals(sh.get("type"))) {
                            reqInds.add(sh);
                        } else if ("corporate".equals(sh.get("type"))) {
                            reqCorps.add(sh);
                        }
                    }
                }

                // Sync Individual Shareholders
                Onboarding.OnboardingStep indStep = ob.getStep3IndividualShareholder();
                if (indStep.getData() == null) indStep.setData(new HashMap<>());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> currentInds = (List<Map<String, Object>>) indStep.getData().get("list");
                if (currentInds == null) currentInds = new ArrayList<>();

                List<Map<String, Object>> newIndList = new ArrayList<>();
                for (int i = 0; i < reqInds.size(); i++) {
                    Map<String, Object> existing = i < currentInds.size() ? currentInds.get(i) : new HashMap<>();
                    Map<String, Object> rInd = reqInds.get(i);

                    Map<String, Object> item = new HashMap<>();
                    item.put("sameAsDirector", getMergedObject(existing, "sameAsDirector", rInd, "sameAsDirector", false));
                    item.put("selectedDirectorIdx", getMergedValue(existing, "selectedDirectorIdx", rInd, "selectedDirectorIdx"));
                    item.put("fullName", getMergedValue(existing, "fullName", rInd, "name"));
                    item.put("idNumber", getMergedValue(existing, "idNumber", rInd, "idNum"));
                    item.put("nationality", getMergedValue(existing, "nationality", rInd, "nation"));
                    item.put("dateOfBirth", getMergedValue(existing, "dateOfBirth", rInd, "dob"));
                    item.put("residentialAddress", getMergedValue(existing, "residentialAddress", rInd, "addr"));
                    item.put("email", getMergedValue(existing, "email", rInd, "email"));
                    item.put("mobile", getMergedValue(existing, "mobile", rInd, "phone"));
                    item.put("passportExpiry", getMergedValue(existing, "passportExpiry", rInd, "passportExpiry"));
                    item.put("totalShares", getMergedValue(existing, "totalShares", rInd, "totalShares"));
                    item.put("totalShareCapital", getMergedValue(existing, "totalShareCapital", rInd, "totalShareCapital"));
                    item.put("currency", getMergedValue(existing, "currency", rInd, "currency").isEmpty() ? "Select" : getMergedValue(existing, "currency", rInd, "currency"));
                    item.put("shareClass", getMergedValue(existing, "shareClass", rInd, "shareClass").isEmpty() ? "Select" : getMergedValue(existing, "shareClass", rInd, "shareClass"));
                    item.put("numberOfShares", getMergedValue(existing, "numberOfShares", rInd, "shares"));
                    item.put("shareCapitalAmount", getMergedValue(existing, "shareCapitalAmount", rInd, "percent"));
                    item.put("numberOfSharesPct", getMergedValue(existing, "numberOfSharesPct", rInd, "numberOfSharesPct"));
                    item.put("shareCapitalAmountPct", getMergedValue(existing, "shareCapitalAmountPct", rInd, "shareCapitalAmountPct"));
                    item.put("ownershipPercentage", getMergedValue(existing, "ownershipPercentage", rInd, "ownershipPercentage"));
                    item.put("uboDeclaration", getMergedValue(existing, "uboDeclaration", rInd, "uboDeclaration").isEmpty() ? "Select" : getMergedValue(existing, "uboDeclaration", rInd, "uboDeclaration"));
                    newIndList.add(item);
                }
                if (newIndList.isEmpty()) newIndList.add(new HashMap<>());
                if (!newIndList.equals(currentInds)) {
                    indStep.getData().put("list", newIndList);
                    changed = true;
                }

                // Sync Corporate Shareholders
                Onboarding.OnboardingStep corpStep = ob.getStep4CorporateShareholder();
                if (corpStep.getData() == null) corpStep.setData(new HashMap<>());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> currentCorps = (List<Map<String, Object>>) corpStep.getData().get("list");
                if (currentCorps == null) currentCorps = new ArrayList<>();

                List<Map<String, Object>> newCorpList = new ArrayList<>();
                for (int i = 0; i < reqCorps.size(); i++) {
                    Map<String, Object> existing = i < currentCorps.size() ? currentCorps.get(i) : new HashMap<>();
                    Map<String, Object> rCorp = reqCorps.get(i);

                    Map<String, Object> item = new HashMap<>();
                    item.put("companyName", getMergedValue(existing, "companyName", rCorp, "name"));
                    item.put("uen", getMergedValue(existing, "uen", rCorp, "regNum"));
                    item.put("registeredAddress", getMergedValue(existing, "registeredAddress", rCorp, "addr"));
                    item.put("countryOfIncorporation", getMergedValue(existing, "countryOfIncorporation", rCorp, "regPlace"));
                    item.put("dateOfIncorporation", getMergedValue(existing, "dateOfIncorporation", rCorp, "regDate"));
                    item.put("totalShares", getMergedValue(existing, "totalShares", rCorp, "totalShares"));
                    item.put("totalShareCapital", getMergedValue(existing, "totalShareCapital", rCorp, "totalShareCapital"));
                    item.put("currency", getMergedValue(existing, "currency", rCorp, "currency").isEmpty() ? "Select" : getMergedValue(existing, "currency", rCorp, "currency"));
                    item.put("shareClass", getMergedValue(existing, "shareClass", rCorp, "shareClass").isEmpty() ? "Select" : getMergedValue(existing, "shareClass", rCorp, "shareClass"));
                    item.put("numberOfShares", getMergedValue(existing, "numberOfShares", rCorp, "shares"));
                    item.put("shareCapitalAmount", getMergedValue(existing, "shareCapitalAmount", rCorp, "percent"));
                    item.put("numberOfSharesPct", getMergedValue(existing, "numberOfSharesPct", rCorp, "numberOfSharesPct"));
                    item.put("shareCapitalAmountPct", getMergedValue(existing, "shareCapitalAmountPct", rCorp, "shareCapitalAmountPct"));
                    item.put("ownershipPercentage", getMergedValue(existing, "ownershipPercentage", rCorp, "ownershipPercentage"));
                    item.put("uboDeclaration", getMergedValue(existing, "uboDeclaration", rCorp, "uboDeclaration").isEmpty() ? "No" : getMergedValue(existing, "uboDeclaration", rCorp, "uboDeclaration"));
                    newCorpList.add(item);
                }
                if (newCorpList.isEmpty()) newCorpList.add(new HashMap<>());
                if (!newCorpList.equals(currentCorps)) {
                    corpStep.getData().put("list", newCorpList);
                    changed = true;
                }

                if (changed || isNew) {
                    ob.setUpdatedAt(System.currentTimeMillis());
                    ob = onboardingRepository.save(ob);
                }
            }
        } else if (isNew) {
            ob = onboardingRepository.save(ob);
        }

        return ResponseEntity.ok(ob);
    }

    // GET portal activation status
    @GetMapping("/client/{clientId}/status")
    public ResponseEntity<?> getPortalStatus(@PathVariable String clientId) {
        Optional<Onboarding> opt = onboardingRepository.findByClientId(clientId);
        Map<String, Object> result = new HashMap<>();
        if (opt.isPresent()) {
            result.put("portalActivated", opt.get().isPortalActivated());
            result.put("status", opt.get().getStatus());
            result.put("progressPercent", opt.get().getProgressPercent());
            result.put("onboardingId", opt.get().getId());
        } else {
            result.put("portalActivated", false);
            result.put("status", "not_started");
            result.put("progressPercent", 0);
        }
        return ResponseEntity.ok(result);
    }

    // POST create or update onboarding record
    @PostMapping("/client/{clientId}")
    public ResponseEntity<Onboarding> createOrUpdate(@PathVariable String clientId,
                                                      @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findByClientId(clientId);
        Onboarding ob = opt.orElseGet(() -> {
            Onboarding n = new Onboarding();
            n.setClientId(clientId);
            n.setDisplayClientId(generateNextDisplayClientId());
            n.setStatus("in_progress");
            // Try to get name/email from user
            userRepository.findById(clientId).ifPresent(u -> {
                n.setClientEmail(u.getEmail());
                n.setClientName(u.getFirstName() + " " + u.getLastName());
            });
            n.getAuditLogs().add("Onboarding initiated at " + new Date());
            return n;
        });
        ob.setUpdatedAt(System.currentTimeMillis());
        if (body.containsKey("clientEmail")) ob.setClientEmail((String) body.get("clientEmail"));
        if (body.containsKey("clientName")) ob.setClientName((String) body.get("clientName"));
        Onboarding saved = onboardingRepository.save(ob);
        return ResponseEntity.ok(saved);
    }

    // PATCH update a specific step's data + status
    @PatchMapping("/{id}/step/{stepKey}")
    public ResponseEntity<?> updateStep(@PathVariable String id,
                                        @PathVariable String stepKey,
                                        @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Onboarding ob = opt.get();

        Onboarding.OnboardingStep step = getStep(ob, stepKey);
        if (step == null) return ResponseEntity.badRequest().body("Unknown step: " + stepKey);

        // Merge data
        if (body.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> newData = (Map<String, Object>) body.get("data");
            step.getData().putAll(newData);
        }
            if (body.containsKey("status")) {
                String oldStatus = step.getStatus();
                String newStatus = (String) body.get("status");
                step.setStatus(newStatus);
                step.getAuditLogs().add("Status changed from " + oldStatus + " to " + newStatus + " at " + new Date());
                ob.getAuditLogs().add("Step '" + step.getTitle() + "' status → " + newStatus);
            }
        if (body.containsKey("documents")) {
            step.getDocuments().clear();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> docs = (List<Map<String, Object>>) body.get("documents");
            docs.forEach(d -> {
                Onboarding.DocumentUpload doc = new Onboarding.DocumentUpload();
                String docId = (String) d.get("id");
                if (docId == null || docId.trim().isEmpty()) {
                    docId = "DOC-" + System.currentTimeMillis() + "-" + (int)(Math.random()*1000);
                }
                doc.setId(docId);
                doc.setType((String) d.getOrDefault("type", "other"));
                doc.setLabel((String) d.getOrDefault("label", "Document"));
                doc.setFileName((String) d.getOrDefault("fileName", ""));
                doc.setFileData((String) d.getOrDefault("fileData", ""));
                doc.setMimeType((String) d.getOrDefault("mimeType", "application/octet-stream"));
                doc.setStatus((String) d.getOrDefault("status", "pending"));
                
                if (d.containsKey("extractedData")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> extData = (Map<String, Object>) d.get("extractedData");
                    doc.setExtractedData(extData);
                } else if (doc.getType().startsWith("nric") || doc.getType().startsWith("fin")) {
                    Map<String, Object> extracted = new HashMap<>();
                    extracted.put("fullName", d.getOrDefault("fullName", "Extracted Name"));
                    extracted.put("idNumber", d.getOrDefault("idNumber", "S1234567A"));
                    extracted.put("nationality", d.getOrDefault("nationality", "Singaporean"));
                    extracted.put("gender", d.getOrDefault("gender", "Male"));
                    extracted.put("dateOfBirth", d.getOrDefault("dateOfBirth", "01/01/1990"));
                    doc.setExtractedData(extracted);
                }
                step.getDocuments().add(doc);
            });
        }

        // Recalculate progress
        ob.setProgressPercent(calculateProgress(ob));
        ob.setUpdatedAt(System.currentTimeMillis());
        updateOverallStatus(ob);

        Onboarding saved = onboardingRepository.save(ob);

        // Notify admin/staff on submission
        if ("submitted".equals(body.get("status"))) {
            try {
                notificationService.sendNotification("admin",
                        "Onboarding Step Submitted",
                        ob.getClientName() + " submitted: " + step.getTitle(),
                        "onboarding", saved.getId(), "Info");
            } catch (Exception ignored) {}
        }

        // Broadcast WS
        Map<String, Object> event = new HashMap<>();
        event.put("type", "onboarding_update");
        event.put("clientId", ob.getClientId());
        event.put("stepKey", stepKey);
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(saved);
    }

    // PATCH admin review a step (approve/reject)
    @PatchMapping("/{id}/step/{stepKey}/review")
    public ResponseEntity<?> reviewStep(@PathVariable String id,
                                        @PathVariable String stepKey,
                                        @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Onboarding ob = opt.get();
        Onboarding.OnboardingStep step = getStep(ob, stepKey);
        if (step == null) return ResponseEntity.badRequest().body("Unknown step");

        String newStatus = (String) body.getOrDefault("status", "under_review");
        String reviewer = (String) body.getOrDefault("reviewedBy", "Admin");
        String notes = (String) body.getOrDefault("notes", "");

        step.setStatus(newStatus);
        step.setReviewedBy(reviewer);
        step.setReviewedAt(System.currentTimeMillis());
        step.setReviewNotes(notes);
        step.getAuditLogs().add("Reviewed by " + reviewer + ": " + newStatus + " — " + notes + " at " + new Date());
        ob.getAuditLogs().add("Step '" + step.getTitle() + "' reviewed by " + reviewer + " → " + newStatus);

        ob.setProgressPercent(calculateProgress(ob));
        ob.setUpdatedAt(System.currentTimeMillis());
        updateOverallStatus(ob);

        Onboarding saved = onboardingRepository.save(ob);

        // Notify client
        try {
            String clientMsg = "approved".equals(newStatus)
                    ? "Your " + step.getTitle() + " has been approved."
                    : "Your " + step.getTitle() + " requires attention: " + notes;
            notificationService.sendNotification(ob.getClientId(),
                    "Onboarding Update: " + step.getTitle(),
                    clientMsg, "onboarding", saved.getId(), "Info");
        } catch (Exception ignored) {}

        // WS broadcast
        Map<String, Object> event = new HashMap<>();
        event.put("type", "onboarding_review");
        event.put("clientId", ob.getClientId());
        event.put("stepKey", stepKey);
        event.put("status", newStatus);
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(saved);
    }

    @Autowired EmailService emailService;

    // POST activate portal (admin/staff)
    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activatePortal(@PathVariable String id,
                                            @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Onboarding ob = opt.get();

        ob.setPortalActivated(true);
        ob.setActivatedAt(System.currentTimeMillis());
        ob.setActivatedBy((String) body.getOrDefault("activatedBy", "Admin"));
        ob.setStatus("approved");
        ob.setProgressPercent(100);
        ob.getAuditLogs().add("Portal activated by " + ob.getActivatedBy() + " at " + new Date());

        Onboarding saved = onboardingRepository.save(ob);

        // Activate user account and retrieve credentials
        String clientEmail = ob.getClientEmail();
        String clientName = ob.getClientName() != null ? ob.getClientName() : "Valued Client";
        String plainPassword = "password123";

        Optional<User> userOpt = userRepository.findById(ob.getClientId());
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            if ("PENDING_APPROVAL".equals(u.getStatus())) {
                u.setStatus("ACTIVE");
                userRepository.save(u);
            }
            if (u.getEmail() != null && !u.getEmail().isEmpty()) {
                clientEmail = u.getEmail();
            }
            if (u.getPlainPassword() != null && !u.getPlainPassword().isEmpty()) {
                plainPassword = u.getPlainPassword();
            }
        }

        // Notify client via internal notification
        try {
            notificationService.sendNotification(ob.getClientId(),
                    "🎉 Your Client Portal is Now Active!",
                    "Congratulations! Your onboarding has been validated and approved. All portal sections are now unlocked.",
                    "onboarding", saved.getId(), "Info");
        } catch (Exception ignored) {}

        // Dispatch official Email with Portal URL & Credentials
        try {
            emailService.sendPortalActivationEmail(clientEmail, clientName, plainPassword, "/login.html");
        } catch (Exception ignored) {}

        // WS broadcast
        Map<String, Object> event = new HashMap<>();
        event.put("type", "portal_activated");
        event.put("clientId", ob.getClientId());
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(Map.of("success", true, "message", "Portal activated successfully and credentials sent to client"));
    }

    private Map<String, Object> callGeminiApi(String docType, String base64Data, String mimeType) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = configuredGeminiApiKey;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("[Gemini OCR] No API key configured. Falling back to mock OCR data.");
            return null;
        }

        try {
            String prompt = "";
            if ("nric".equals(docType) || "fin".equals(docType)) {
                prompt = "Extract the following details from this Singapore NRIC/FIN document image. Return a JSON object with the following keys:\n" +
                         "- fullName: The full name of the person (in uppercase)\n" +
                         "- idNumber: The NRIC or FIN number (e.g. S1234567A)\n" +
                         "- nationality: The nationality (e.g. INDIAN, SINGAPOREAN)\n" +
                         "- gender: 'Male' or 'Female'\n" +
                         "- dateOfBirth: The date of birth in YYYY-MM-DD format\n" +
                         "- residentialAddress: The residential address listed on the back of the NRIC (if visible, otherwise null)\n" +
                         "- email: Return null\n" +
                         "- mobile: Return null";
            } else if ("passport".equals(docType)) {
                prompt = "Extract the following details from this Passport document image. Return a JSON object with the following keys:\n" +
                         "- fullName: The full name of the person (in uppercase)\n" +
                         "- idNumber: The passport number\n" +
                         "- nationality: The nationality\n" +
                         "- gender: 'Male' or 'Female'\n" +
                         "- dateOfBirth: The date of birth in YYYY-MM-DD format\n" +
                         "- residentialAddress: The address (if visible, otherwise null)\n" +
                         "- passportExpiry: The passport expiry date in YYYY-MM-DD format\n" +
                         "- email: Return null\n" +
                         "- mobile: Return null";
            } else if ("bizfile".equals(docType)) {
                prompt = "Extract the following details from this Singapore ACRA Bizfile document. Return a JSON object with the following keys:\n" +
                         "- companyName: The name of the company\n" +
                         "- uen: The Unique Entity Number\n" +
                         "- dateOfIncorporation: The incorporation date in YYYY-MM-DD format\n" +
                         "- registeredAddress: The registered office address\n" +
                         "- principalActivity: The primary business activity\n" +
                         "- countryOfIncorporation: 'Singapore'\n" +
                         "- companyType: The type of company\n" +
                         "- companyStatus: The current status of the company (e.g. LIVE COMPANY)";
            } else {
                prompt = "Extract any relevant text information from this document and return a JSON object with description fields.";
            }

            Map<String, Object> requestBody = new HashMap<>();
            
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);
            
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mimeType", mimeType != null ? mimeType : "image/png");
            inlineData.put("data", base64Data);
            
            Map<String, Object> filePart = new HashMap<>();
            filePart.put("inlineData", inlineData);
            
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(textPart);
            parts.add(filePart);
            
            Map<String, Object> contentNode = new HashMap<>();
            contentNode.put("parts", parts);
            
            List<Map<String, Object>> contents = new ArrayList<>();
            contents.add(contentNode);
            
            requestBody.put("contents", contents);
            
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(requestBody);

            HttpClient client = HttpClient.newHttpClient();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                JsonNode root = mapper.readTree(responseBody);
                JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (!textNode.isMissingNode()) {
                    String extractedJsonText = textNode.asText();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = mapper.readValue(extractedJsonText, Map.class);
                    
                    if ("nric".equals(docType) || "fin".equals(docType)) {
                        result.put("email", "");
                        result.put("mobile", "");
                    }
                    return result;
                }
            } else {
                System.err.println("[Gemini API Error] status code: " + response.statusCode() + ", body: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[Gemini API Exception] Error calling Gemini: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // POST simulate/perform OCR extraction
    @PostMapping("/ocr-extract")
    public ResponseEntity<?> ocrExtract(@RequestBody Map<String, Object> body) {
        String docType = (String) body.getOrDefault("type", "nric");
        String fileData = (String) body.get("fileData");
        String mimeType = (String) body.get("mimeType");
        
        Map<String, Object> extracted = null;
        if (fileData != null && !fileData.trim().isEmpty()) {
            extracted = callGeminiApi(docType, fileData, mimeType);
        }
        
        if (extracted == null) {
            extracted = new HashMap<>();
            // Fallback to high-fidelity simulated OCR results
            if ("nric".equals(docType) || "fin".equals(docType)) {
                int randomId = 1000000 + new Random().nextInt(9000000);
                String randomNric = "S" + randomId + "G";
                extracted.put("fullName", "MOCK NAME " + randomId);
                extracted.put("idNumber", randomNric);
                extracted.put("nationality", "SINGAPOREAN");
                extracted.put("gender", "Male");
                extracted.put("dateOfBirth", "1980-01-01");
                extracted.put("residentialAddress", "BLK 123 Ang Mo Kio Ave 4 #05-67, Singapore 560123");
                extracted.put("email", "");
                extracted.put("mobile", "");
            } else if ("bizfile".equals(docType)) {
                extracted.put("companyName", "GRAAS PTE. LTD.");
                extracted.put("uen", "201538449N");
                extracted.put("dateOfIncorporation", "2015-10-22");
                extracted.put("registeredAddress", "8 CRAIG ROAD, #02-01, SINGAPORE 089668");
                extracted.put("principalActivity", "DEVELOPMENT OF SOFTWARE AND APPLICATIONS (EXCEPT GAMES AND CYBERSECURITY) (62011)");
                extracted.put("countryOfIncorporation", "Singapore");
                extracted.put("companyType", "PRIVATE COMPANY LIMITED BY SHARES");
                extracted.put("companyStatus", "LIVE COMPANY");
                extracted.put("formerName", "SELLINALL PTE. LTD.");
                extracted.put("dateOfChangeOfName", "2023-03-07");
                extracted.put("secondaryActivity", "WHOLESALE OF COMPUTER SOFTWARE (EXCEPT GAMES AND CYBERSECURITY SOFTWARE) (46512)");
                extracted.put("auditFirm", "GRANT THORNTON AUDIT LLP");
                extracted.put("numberOfShares", 1998815);
                extracted.put("shareCapitalAmount", 8297985.82);
                extracted.put("totalShares", 1998815);
                extracted.put("totalShareCapital", 8297985.82);
                extracted.put("fye", "31 DEC");
                extracted.put("currency", "SGD");
            } else if ("passport".equals(docType)) {
                int randomId = 1000000 + new Random().nextInt(9000000);
                String randomPassport = "Z" + randomId + "P";
                extracted.put("fullName", "MOCK FOREIGN SHAREHOLDER " + randomId);
                extracted.put("idNumber", randomPassport);
                extracted.put("nationality", "AMERICAN");
                extracted.put("gender", "Female");
                extracted.put("dateOfBirth", "1992-05-15");
                extracted.put("residentialAddress", "456 Broadway, New York, NY 10013");
                extracted.put("email", "");
                extracted.put("mobile", "");
                extracted.put("passportExpiry", "2032-12-31");
            } else if ("ubo_nric".equals(docType)) {
                int randomId = 1000000 + new Random().nextInt(9000000);
                extracted.put("uboName", "MOCK UBO " + randomId);
                extracted.put("uboIdNumber", "S" + randomId + "F");
            } else if ("ubo_address_proof".equals(docType)) {
                extracted.put("uboAddress", "12 MARINA BOULEVARD, #30-02, MBFC TOWER 3, SINGAPORE 018982");
                extracted.put("email", "client.representative@graas.ai");
                extracted.put("mobile", "+65 8765 4321");
            }
        }
        
        extracted.put("confidence", 0.94);
        extracted.put("extractedAt", System.currentTimeMillis());
        return ResponseEntity.ok(extracted);
    }

    // Helper: get step by key
    private Onboarding.OnboardingStep getStep(Onboarding ob, String key) {
        Onboarding.OnboardingStep step = switch (key) {
            case "individual_verification" -> ob.getStep1IndividualVerification();
            case "director_details" -> ob.getStep2DirectorDetails();
            case "individual_shareholder" -> ob.getStep3IndividualShareholder();
            case "corporate_shareholder" -> ob.getStep4CorporateShareholder();
            case "ubo" -> ob.getStep5UBO();
            case "corporate_rep" -> ob.getStep6CorporateRep();
            case "final_declaration" -> ob.getStep7FinalDeclaration();
            default -> null;
        };
        if (step != null) {
            return step;
        }
        if (ob.getDynamicSteps() == null) {
            ob.setDynamicSteps(new HashMap<>());
        }
        if (!ob.getDynamicSteps().containsKey(key)) {
            Onboarding.OnboardingStep newStep = new Onboarding.OnboardingStep(key, key);
            ob.getDynamicSteps().put(key, newStep);
        }
        return ob.getDynamicSteps().get(key);
    }

    // Helper: calculate progress
    private int calculateProgress(Onboarding ob) {
        String journey = ob.getJourneyType() != null ? ob.getJourneyType() : "LOCAL";
        List<OnboardingConfig> publishedSteps = onboardingConfigRepository.findByJourneyTypeAndStatusOrderBySortOrderAsc(journey, "PUBLISHED");
        if (publishedSteps.isEmpty()) {
            List<String> statuses = List.of(
                    ob.getStep1IndividualVerification().getStatus(),
                    ob.getStep2DirectorDetails().getStatus(),
                    ob.getStep3IndividualShareholder().getStatus(),
                    ob.getStep4CorporateShareholder().getStatus(),
                    ob.getStep5UBO().getStatus(),
                    ob.getStep6CorporateRep().getStatus(),
                    ob.getStep7FinalDeclaration().getStatus()
            );
            long approved = statuses.stream().filter("approved"::equals).count();
            long submitted = statuses.stream().filter(s -> s.equals("submitted") || s.equals("under_review")).count();
            return (int) (((approved * 100) + (submitted * 50)) / 7);
        }

        long approved = 0;
        long submitted = 0;
        for (OnboardingConfig stepConfig : publishedSteps) {
            Onboarding.OnboardingStep step = getStep(ob, stepConfig.getKey());
            String status = step.getStatus();
            if ("approved".equals(status)) {
                approved++;
            } else if ("submitted".equals(status) || "under_review".equals(status)) {
                submitted++;
            }
        }
        return (int) (((approved * 100) + (submitted * 50)) / publishedSteps.size());
    }

    private void updateOverallStatus(Onboarding ob) {
        String journey = ob.getJourneyType() != null ? ob.getJourneyType() : "LOCAL";
        List<OnboardingConfig> publishedSteps = onboardingConfigRepository.findByJourneyTypeAndStatusOrderBySortOrderAsc(journey, "PUBLISHED");
        List<String> statuses;
        if (publishedSteps.isEmpty()) {
            statuses = List.of(
                    ob.getStep1IndividualVerification().getStatus(),
                    ob.getStep2DirectorDetails().getStatus(),
                    ob.getStep3IndividualShareholder().getStatus(),
                    ob.getStep4CorporateShareholder().getStatus(),
                    ob.getStep5UBO().getStatus(),
                    ob.getStep6CorporateRep().getStatus(),
                    ob.getStep7FinalDeclaration().getStatus()
            );
        } else {
            statuses = new ArrayList<>();
            for (OnboardingConfig stepConfig : publishedSteps) {
                statuses.add(getStep(ob, stepConfig.getKey()).getStatus());
            }
        }

        boolean allApproved = statuses.stream().allMatch("approved"::equals);
        boolean anyRejected = statuses.stream().anyMatch(s -> "rejected".equals(s) || "additional_info_required".equals(s));
        boolean allCompletedOrApproved = statuses.stream().allMatch(s -> 
            "submitted".equals(s) || "under_review".equals(s) || "approved".equals(s)
        );

        if (ob.isPortalActivated()) {
            ob.setStatus("approved");
        } else if (anyRejected) {
            ob.setStatus("rejected");
        } else if (allCompletedOrApproved) {
            ob.setStatus("submitted");
        } else {
            ob.setStatus("in_progress");
        }
    }

    private String getMergedValue(Map<String, Object> existing, String existingKey, Map<String, Object> source, String sourceKey) {
        if (existing != null && existing.containsKey(existingKey)) {
            Object v = existing.get(existingKey);
            if (v != null && !v.toString().trim().isEmpty()) {
                return v.toString();
            }
        }
        if (source != null && source.containsKey(sourceKey)) {
            Object v = source.get(sourceKey);
            if (v != null) {
                return v.toString();
            }
        }
        return "";
    }

    private Object getMergedObject(Map<String, Object> existing, String existingKey, Map<String, Object> source, String sourceKey, Object defaultVal) {
        if (existing != null && existing.containsKey(existingKey)) {
            Object v = existing.get(existingKey);
            if (v != null) {
                if (v instanceof String && ((String) v).trim().isEmpty()) {
                    // skip empty string fallback
                } else {
                    return v;
                }
            }
        }
        if (source != null && source.containsKey(sourceKey)) {
            Object v = source.get(sourceKey);
            if (v != null) return v;
        }
        return defaultVal;
    }

    private synchronized String generateNextDisplayClientId() {
        List<Onboarding> all = onboardingRepository.findAll();
        int maxId = 100;
        for (Onboarding o : all) {
            if (o.getDisplayClientId() != null) {
                try {
                    int val = Integer.parseInt(o.getDisplayClientId());
                    if (val > maxId) {
                        maxId = val;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return String.valueOf(maxId + 1);
    }

    @jakarta.annotation.PostConstruct
    public void migrateExistingDisplayClientIds() {
        List<Onboarding> all = onboardingRepository.findAll();
        all.sort(Comparator.comparing(Onboarding::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Onboarding::getId));

        int currentNext = 101;
        for (Onboarding ob : all) {
            if (ob.getDisplayClientId() == null || ob.getDisplayClientId().trim().isEmpty()) {
                while (true) {
                    final int checkVal = currentNext;
                    boolean exists = all.stream().anyMatch(o -> String.valueOf(checkVal).equals(o.getDisplayClientId()));
                    if (!exists) {
                        ob.setDisplayClientId(String.valueOf(checkVal));
                        onboardingRepository.save(ob);
                        currentNext = checkVal + 1;
                        break;
                    }
                    currentNext++;
                }
            }
        }
    }
}
