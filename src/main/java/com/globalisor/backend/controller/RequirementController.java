package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.model.Kyc;
import com.globalisor.backend.model.Compliance;
import com.globalisor.backend.model.Onboarding;
import com.globalisor.backend.model.Invoice;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.repository.KycRepository;
import com.globalisor.backend.repository.ComplianceRepository;
import com.globalisor.backend.repository.OnboardingRepository;
import com.globalisor.backend.repository.InvoiceRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import com.globalisor.backend.security.EncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.Date;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/requirements")
public class RequirementController {
 
    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    InvoiceRepository invoiceRepository;
 
    @Autowired
    private com.globalisor.backend.service.NotificationService notificationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    KycRepository kycRepository;

    @Autowired
    ComplianceRepository complianceRepository;

    @Autowired
    OnboardingRepository onboardingRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    EncryptionUtils encryptionUtils;

    @GetMapping
    public ResponseEntity<?> getRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> req = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(userDetails.getId());
        if (req.isPresent()) {
            Requirement r = req.get();
            if (r.getApplicationReferenceNo() == null || r.getApplicationReferenceNo().trim().isEmpty() || !r.getApplicationReferenceNo().startsWith("APP-")) {
                r.setApplicationReferenceNo(generateNextApplicationReferenceNo(r));
                if (r.getData() != null) {
                    r.getData().put("applicationReferenceNo", r.getApplicationReferenceNo());
                }
                requirementRepository.save(r);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("status", r.getStatus());
            response.put("data", r.getData());
            response.put("sectionStatuses", r.getSectionStatuses());
            response.put("applicationId", r.getId());
            response.put("applicationReferenceNo", r.getApplicationReferenceNo());
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "none");
            response.put("data", new HashMap<>());
            response.put("sectionStatuses", new HashMap<>());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping
    public ResponseEntity<?> saveRequirement(@RequestBody Map<String, Object> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        normalizeSsicData(data);

        String appId = data != null && data.get("applicationId") != null ? String.valueOf(data.get("applicationId")) : null;
        Optional<Requirement> reqOpt = Optional.empty();
        if (appId != null && !appId.trim().isEmpty() && !appId.startsWith("APP-")) {
            reqOpt = requirementRepository.findById(appId);
        }
        if (!reqOpt.isPresent()) {
            Optional<Requirement> latest = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(userDetails.getId());
            if (latest.isPresent()) {
                String existingStatus = latest.get().getStatus() != null ? latest.get().getStatus().toLowerCase() : "";
                // If existing application is already under review or approved/completed, create a separate new application
                if (!existingStatus.contains("review") && !existingStatus.contains("approved") && !existingStatus.contains("completed")) {
                    reqOpt = latest;
                }
            }
        }

        Requirement requirement;
        boolean isNew = !reqOpt.isPresent();
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            requirement.setData(data);
            requirement.setUpdatedAt(new java.util.Date());
        } else {
            requirement = new Requirement(userDetails.getId(), data);
            requirement.setStatus("pending");
            requirement.setCreatedAt(new java.util.Date());
            requirement.setUpdatedAt(new java.util.Date());
        }
        if (requirement.getApplicationReferenceNo() == null || requirement.getApplicationReferenceNo().trim().isEmpty() || !requirement.getApplicationReferenceNo().startsWith("APP-")) {
            requirement.setApplicationReferenceNo(generateNextApplicationReferenceNo(requirement));
        }
        if (requirement.getData() != null) {
            requirement.getData().put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        }
        requirementRepository.save(requirement);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        response.put("sectionStatuses", requirement.getSectionStatuses());
        response.put("applicationId", requirement.getId());
        response.put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitRequirement(@RequestBody(required = false) Map<String, Object> bodyData) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> reqOpt = Optional.empty();
        if (bodyData != null && bodyData.get("applicationId") != null) {
            String appId = String.valueOf(bodyData.get("applicationId"));
            if (!appId.startsWith("APP-")) {
                reqOpt = requirementRepository.findById(appId);
            }
        }
        if (!reqOpt.isPresent()) {
            reqOpt = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(userDetails.getId());
        }

        Requirement requirement;
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            String existingStatus = requirement.getStatus() != null ? requirement.getStatus().toLowerCase() : "";
            if (existingStatus.contains("approved") || existingStatus.contains("completed")) {
                requirement = new Requirement(userDetails.getId(), bodyData != null ? bodyData : new HashMap<>());
                requirement.setCreatedAt(new java.util.Date());
            } else if (bodyData != null && !bodyData.isEmpty()) {
                normalizeSsicData(bodyData);
                requirement.setData(bodyData);
            }
            requirement.setStatus("pending");
            requirement.setUpdatedAt(new java.util.Date());
            requirementRepository.save(requirement);
        } else {
            requirement = new Requirement(userDetails.getId(), bodyData != null ? bodyData : new HashMap<>());
            if (bodyData != null && !bodyData.isEmpty()) {
                normalizeSsicData(bodyData);
                requirement.setData(bodyData);
            }
            requirement.setStatus("pending");
            requirement.setCreatedAt(new java.util.Date());
            requirement.setUpdatedAt(new java.util.Date());
            requirementRepository.save(requirement);
        }
            
        Optional<User> userOpt = userRepository.findById(userDetails.getId());
        if (userOpt.isPresent()) {
            syncWithOnboardingAndKyc(userOpt.get(), requirement.getData(), requirement);
        }

        String compName = resolveProposedCompanyName(requirement.getData());
        String applicantName = (userDetails.getFirstName() + " " + userDetails.getLastName()).trim();
        if (applicantName.isEmpty()) applicantName = userDetails.getEmail();

        try {
            // Staff & Admin notification
            notificationService.sendNotification(
                    "staff-admin",
                    "New Application: " + compName,
                    applicantName + " submitted a new incorporation application for " + compName + ".",
                    "application",
                    requirement.getId(),
                    "High",
                    "applications.html"
            );
            // Client notification
            notificationService.sendNotification(
                    userDetails.getId(),
                    "Application Submitted Successfully",
                    "Your incorporation application for " + compName + " has been received and is currently under review.",
                    "application",
                    requirement.getId(),
                    "Info",
                    "/client/portal.html"
            );
        } catch (Exception e) {}

        if (requirement.getApplicationReferenceNo() == null || requirement.getApplicationReferenceNo().trim().isEmpty() || !requirement.getApplicationReferenceNo().startsWith("APP-")) {
            requirement.setApplicationReferenceNo(generateNextApplicationReferenceNo(requirement));
        }
        if (requirement.getData() != null) {
            requirement.getData().put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        }
        requirementRepository.save(requirement);

        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        response.put("sectionStatuses", requirement.getSectionStatuses());
        response.put("applicationId", requirement.getId());
        response.put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pay")
    public ResponseEntity<?> payRequirement(@RequestBody Map<String, Object> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        normalizeSsicData(data);
        Optional<Requirement> reqOpt = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(userDetails.getId());
        Requirement requirement;
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            requirement.setData(data);
            requirement.setUpdatedAt(new java.util.Date());
        } else {
            requirement = new Requirement(userDetails.getId(), data);
        }
        if (requirement.getApplicationReferenceNo() == null || requirement.getApplicationReferenceNo().trim().isEmpty() || !requirement.getApplicationReferenceNo().startsWith("APP-")) {
            requirement.setApplicationReferenceNo(generateNextApplicationReferenceNo(requirement));
        }
        if (requirement.getData() != null) {
            requirement.getData().put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        }
        requirementRepository.save(requirement);

        // Generate paid Invoice
        try {
            String amountStr = "SGD 1,315";
            if (data.containsKey("totalAmount")) {
                amountStr = String.valueOf(data.get("totalAmount"));
            }
            String journeyType = "LOCAL";
            if (data.containsKey("journeyType")) {
                journeyType = String.valueOf(data.get("journeyType"));
            }

            Invoice invoice = new Invoice();
            invoice.setId("INV-" + System.currentTimeMillis());
            invoice.setClientId(userDetails.getId());
            invoice.setAmount(amountStr);
            invoice.setStatus("paid");
            invoice.setDate(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
            invoice.setDueDate(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
            invoice.setDescription("Singapore Company Incorporation (" + journeyType + " Journey)");
            invoiceRepository.save(invoice);
        } catch (Exception e) {
            System.err.println("Failed to create paid invoice: " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        response.put("sectionStatuses", requirement.getSectionStatuses());
        response.put("applicationId", requirement.getId());
        response.put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        Optional<Requirement> reqOpt = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(userDetails.getId());
        if (reqOpt.isPresent()) {
            requirementRepository.delete(reqOpt.get());
            try {
                notificationService.sendNotification(
                        "admin",
                        "Application Deleted",
                        userDetails.getFirstName() + " " + userDetails.getLastName() + " reset and deleted their application.",
                        "application",
                        reqOpt.get().getId(),
                        "Warning"
                );
            } catch (Exception e) {}
            return ResponseEntity.ok().body("Requirement deleted successfully");
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/public/submit")
    public ResponseEntity<?> publicSubmitRequirement(@RequestBody Map<String, Object> data) {
        // 1. Extract contact details with resilient fallbacks
        String email = null;
        String firstName = "";
        String lastName = "";
        String phone = "";

        if (data != null && data.containsKey("contact") && data.get("contact") instanceof Map) {
            Map<?, ?> contact = (Map<?, ?>) data.get("contact");
            if (contact.get("email") != null) email = String.valueOf(contact.get("email")).trim();
            if (contact.get("firstName") != null) firstName = String.valueOf(contact.get("firstName")).trim();
            if (contact.get("lastName") != null) lastName = String.valueOf(contact.get("lastName")).trim();
            if (contact.get("phone") != null) phone = String.valueOf(contact.get("phone")).trim();
        }

        if ((email == null || email.isEmpty()) && data != null && data.containsKey("email") && data.get("email") != null) {
            email = String.valueOf(data.get("email")).trim();
        }

        if ((email == null || email.isEmpty()) && data != null && data.containsKey("directors") && data.get("directors") instanceof List) {
            List<?> dirs = (List<?>) data.get("directors");
            if (!dirs.isEmpty() && dirs.get(0) instanceof Map) {
                Map<?, ?> d0 = (Map<?, ?>) dirs.get(0);
                if (d0.get("email") != null) email = String.valueOf(d0.get("email")).trim();
                if (firstName.isEmpty() && d0.get("name") != null) firstName = String.valueOf(d0.get("name")).trim();
            }
        }

        if ((email == null || email.isEmpty()) && data != null && data.containsKey("shareholders") && data.get("shareholders") instanceof List) {
            List<?> shs = (List<?>) data.get("shareholders");
            if (!shs.isEmpty() && shs.get(0) instanceof Map) {
                Map<?, ?> s0 = (Map<?, ?>) shs.get(0);
                if (s0.get("email") != null) email = String.valueOf(s0.get("email")).trim();
                if (firstName.isEmpty() && s0.get("name") != null) firstName = String.valueOf(s0.get("name")).trim();
            }
        }

        if (email == null || email.isEmpty()) {
            email = "client." + System.currentTimeMillis() + "@globalisor.com";
        }
        if (firstName.isEmpty()) {
            firstName = "Client";
        }

        // Guarantee contact map is populated on data
        if (data != null) {
            Map<String, Object> contactMap = new HashMap<>();
            if (data.containsKey("contact") && data.get("contact") instanceof Map) {
                contactMap.putAll((Map<String, Object>) data.get("contact"));
            }
            contactMap.put("email", email);
            contactMap.put("firstName", firstName);
            contactMap.put("lastName", lastName);
            if (!phone.isEmpty()) contactMap.put("phone", phone);
            data.put("contact", contactMap);
        }

        // 2. Check if user already exists
        String encryptedEmail = encryptionUtils.encryptQueryable(email);
        Optional<User> userOpt = userRepository.findByEmail(encryptedEmail);
        User clientUser;
        String rawPassword = "";
        
        if (userOpt.isPresent()) {
            clientUser = userOpt.get();
            rawPassword = clientUser.getPlainPassword() != null ? clientUser.getPlainPassword() : "password123";
        } else {
            // Generate a random password: Glob-[4-digit-number]
            int randomNum = (int) (Math.random() * 9000) + 1000;
            rawPassword = "Glob-" + randomNum;
            String encodedPassword = encoder.encode(rawPassword);
            
            clientUser = new User(firstName, lastName, email, encodedPassword);
            clientUser.setRole("CLIENT");
            clientUser.setStatus("ACTIVE");
            clientUser.setPlainPassword(rawPassword);
            userRepository.save(clientUser);
            
            // Auto-initialize KYC record for new client
            Kyc kyc = new Kyc();
            kyc.setId("KYC-" + System.currentTimeMillis());
            kyc.setClientId(clientUser.getId());
            kyc.setName(clientUser.getFirstName() + " " + clientUser.getLastName());
            kyc.setIdType("N/A");
            kyc.setIdNum("N/A");
            kyc.setNation("N/A");
            kyc.setStatus("pending");
            kyc.setRisk("Low");
            kyc.setLastUpdated(System.currentTimeMillis());
            kyc.getAuditLogs().add("KYC profile initialized on user registration.");
            kycRepository.save(kyc);

            // Auto-initialize Compliance record for new client
            Compliance compliance = new Compliance();
            compliance.setId("COMP-" + System.currentTimeMillis());
            compliance.setClientId(clientUser.getId());
            compliance.setName(clientUser.getFirstName() + " " + clientUser.getLastName());
            compliance.setType("AML Screening");
            compliance.setStatus("pending");
            compliance.setRisk("Low");
            compliance.setLastUpdated(System.currentTimeMillis());
            compliance.getAuditLogs().add("AML compliance monitoring initialized on registration.");
            complianceRepository.save(compliance);
        }

        normalizeSsicData(data);
        // 3. Save the Requirement record (pre-registration application)
        Optional<Requirement> reqOpt = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(clientUser.getId());
        Requirement requirement;
        if (reqOpt.isPresent()) {
            Requirement existing = reqOpt.get();
            String existingStatus = existing.getStatus() != null ? existing.getStatus().toLowerCase() : "";
            if (!existingStatus.contains("approved") && !existingStatus.contains("completed")) {
                requirement = existing;
                requirement.setData(data);
                requirement.setStatus("under review");
                requirement.setUpdatedAt(new Date());
            } else {
                requirement = new Requirement(clientUser.getId(), data);
                requirement.setStatus("under review");
                requirement.setCreatedAt(new Date());
                requirement.setUpdatedAt(new Date());
            }
        } else {
            requirement = new Requirement(clientUser.getId(), data);
            requirement.setStatus("under review");
            requirement.setCreatedAt(new Date());
            requirement.setUpdatedAt(new Date());
        }
        if (requirement.getApplicationReferenceNo() == null || requirement.getApplicationReferenceNo().trim().isEmpty() || !requirement.getApplicationReferenceNo().startsWith("APP-")) {
            requirement.setApplicationReferenceNo(generateNextApplicationReferenceNo(requirement));
        }
        if (requirement.getData() != null) {
            requirement.getData().put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        }
        requirementRepository.save(requirement);

        // 4. Synchronize with Onboarding and KYC records
        syncWithOnboardingAndKyc(clientUser, data, requirement);

        // 5. Notify admin/staff & client
        String compName = resolveProposedCompanyName(data);
        String applicantName = (firstName + " " + lastName).trim();
        if (applicantName.isEmpty()) applicantName = email;

        try {
            // Staff & Admin notification
            notificationService.sendNotification(
                    "staff-admin",
                    "New Application: " + compName,
                    applicantName + " submitted a new incorporation application for " + compName + " (" + email + ").",
                    "application",
                    requirement.getId(),
                    "High",
                    "applications.html"
            );
            // Client notification
            notificationService.sendNotification(
                    clientUser.getId(),
                    "Application Submitted Successfully",
                    "Your incorporation application for " + compName + " has been received and is currently under review.",
                    "application",
                    requirement.getId(),
                    "Info",
                    "/client/portal.html"
            );
        } catch (Exception e) {}

        // 6. Build response
        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        response.put("applicationId", requirement.getId());
        response.put("applicationReferenceNo", requirement.getApplicationReferenceNo());
        response.put("email", email);
        response.put("password", rawPassword);
        response.put("firstName", firstName);
        response.put("lastName", lastName);
        response.put("clientId", clientUser.getId());
        
        return ResponseEntity.ok(response);
    }

    private void syncWithOnboardingAndKyc(User clientUser, Map<String, Object> data, Requirement requirement) {
        if (clientUser == null || data == null) return;
        try {
            Optional<Onboarding> onboardingOpt = onboardingRepository.findFirstByClientIdOrderByCreatedAtDesc(clientUser.getId());
            Onboarding onboarding = onboardingOpt.orElseGet(() -> {
                Onboarding ob = new Onboarding();
                ob.setClientId(clientUser.getId());
                ob.setClientEmail(clientUser.getEmail());
                ob.setClientName(clientUser.getFirstName() + " " + clientUser.getLastName());
                return ob;
            });

            onboarding.setStatus("submitted");
            onboarding.setPortalActivated(false);
            if (data.containsKey("journeyType")) {
                onboarding.setJourneyType(String.valueOf(data.get("journeyType")));
            }

            // Sync Directors
            if (data.containsKey("directors")) {
                Onboarding.OnboardingStep dirStep = onboarding.getStep2DirectorDetails();
                dirStep.setStatus("submitted");
                Map<String, Object> dirData = new HashMap<>();
                dirData.put("directors", data.get("directors"));
                dirStep.setData(dirData);
            }

            // Sync Share Capital
            if (data.containsKey("capital")) {
                Onboarding.OnboardingStep capStep = onboarding.getStepShareCapital();
                capStep.setStatus("submitted");
                if (data.get("capital") instanceof Map) {
                    capStep.setData((Map<String, Object>) data.get("capital"));
                }
            }

            // Sync Shareholders
            if (data.containsKey("shareholders")) {
                Onboarding.OnboardingStep indShStep = onboarding.getStep3IndividualShareholder();
                indShStep.setStatus("submitted");
                Map<String, Object> indData = new HashMap<>();
                indData.put("shareholders", data.get("shareholders"));
                indShStep.setData(indData);

                Onboarding.OnboardingStep corpShStep = onboarding.getStep4CorporateShareholder();
                corpShStep.setStatus("submitted");
                corpShStep.setData(indData);
            }

            // Sync Declarations & Contact
            if (data.containsKey("contact")) {
                Onboarding.OnboardingStep declStep = onboarding.getStep7FinalDeclaration();
                declStep.setStatus("submitted");
                Map<String, Object> declData = new HashMap<>();
                declData.put("contact", data.get("contact"));
                declStep.setData(declData);
            }

            onboarding.getAuditLogs().add("Pre-registration submitted with full director, shareholder, and document details at " + new Date());
            onboardingRepository.save(onboarding);

            // Sync KYC
            Optional<Kyc> kycOpt = kycRepository.findFirstByClientIdOrderByLastUpdatedDesc(clientUser.getId());
            Kyc kyc = kycOpt.orElseGet(() -> {
                Kyc k = new Kyc();
                k.setId("KYC-" + System.currentTimeMillis());
                k.setClientId(clientUser.getId());
                return k;
            });
            kyc.setName(clientUser.getFirstName() + " " + clientUser.getLastName());
            kyc.setStatus("pending");
            kyc.setRisk("Low");
            kyc.setLastUpdated(System.currentTimeMillis());
            kyc.getAuditLogs().add("KYC profile submitted with pre-registration documentation at " + new Date());
            kycRepository.save(kyc);
        } catch (Exception ex) {
            System.err.println("Error syncing with onboarding and kyc: " + ex.getMessage());
        }
    }

    private String resolveProposedCompanyName(Map<String, Object> data) {
        if (data != null && data.containsKey("names")) {
            Object namesObj = data.get("names");
            if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                Object first = ((List<?>) namesObj).get(0);
                if (first != null && !String.valueOf(first).trim().isEmpty()) {
                    return String.valueOf(first).trim();
                }
            } else if (namesObj instanceof String && !((String) namesObj).trim().isEmpty()) {
                return ((String) namesObj).trim();
            }
        }
        if (data != null && data.containsKey("companyName") && data.get("companyName") != null && !String.valueOf(data.get("companyName")).trim().isEmpty()) {
            return String.valueOf(data.get("companyName")).trim();
        }
        if (data != null && data.containsKey("company_name") && data.get("company_name") != null && !String.valueOf(data.get("company_name")).trim().isEmpty()) {
            return String.valueOf(data.get("company_name")).trim();
        }
        return "Singapore Pte Ltd";
    }

    private void normalizeSsicData(Map<String, Object> data) {
        if (data == null) return;
        if (data.containsKey("activities") && data.get("activities") instanceof Map) {
            Map<?, ?> acts = (Map<?, ?>) data.get("activities");
            Object pri = acts.get("primary");
            if (pri != null) {
                if (!data.containsKey("primarySsic")) {
                    data.put("primarySsic", pri);
                }
                if (!data.containsKey("primaryActivity")) {
                    if (pri instanceof Map) {
                        Map<?, ?> pm = (Map<?, ?>) pri;
                        String code = pm.get("code") != null ? String.valueOf(pm.get("code")) : "";
                        String name = pm.get("name") != null ? String.valueOf(pm.get("name")) : (pm.get("description") != null ? String.valueOf(pm.get("description")) : "");
                        data.put("primaryActivity", (code.isEmpty() ? "" : code + " - ") + name);
                    } else {
                        data.put("primaryActivity", String.valueOf(pri));
                    }
                }
            }
            Object sec = acts.get("secondary");
            if (sec != null) {
                if (!data.containsKey("secondarySsic")) {
                    data.put("secondarySsic", sec);
                }
                if (!data.containsKey("secondaryActivity")) {
                    if (sec instanceof Map) {
                        Map<?, ?> sm = (Map<?, ?>) sec;
                        String code = sm.get("code") != null ? String.valueOf(sm.get("code")) : "";
                        String name = sm.get("name") != null ? String.valueOf(sm.get("name")) : (sm.get("description") != null ? String.valueOf(sm.get("description")) : "");
                        data.put("secondaryActivity", (code.isEmpty() ? "" : code + " - ") + name);
                    } else {
                        data.put("secondaryActivity", String.valueOf(sec));
                    }
                }
            }
        }
    }

    private synchronized String generateNextApplicationReferenceNo(Requirement requirement) {
        if (requirement != null && requirement.getApplicationReferenceNo() != null 
                && !requirement.getApplicationReferenceNo().trim().isEmpty() 
                && requirement.getApplicationReferenceNo().startsWith("APP-")) {
            return requirement.getApplicationReferenceNo();
        }

        // Count existing clients exactly as displayed in Admin > Clients > Total Clients
        long clientCount = 0;
        try {
            List<User> users = userRepository.findAll();
            clientCount = users.stream().filter(u -> {
                String role = u.getRole();
                if (role != null) {
                    String trimmedRole = role.trim();
                    if (trimmedRole.equalsIgnoreCase("ADMIN") || trimmedRole.equalsIgnoreCase("STAFF")) {
                        return false;
                    }
                }
                return !"PENDING_APPROVAL".equalsIgnoreCase(u.getStatus());
            }).count();
        } catch (Exception e) {}

        long highestNumber = clientCount;

        // Check if existing applications in database already have higher numbers
        try {
            List<Requirement> allReqs = requirementRepository.findAll();
            for (Requirement r : allReqs) {
                String ref = r.getApplicationReferenceNo();
                if (ref != null && ref.startsWith("APP-")) {
                    String digits = ref.substring(4).replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        try {
                            long val = Long.parseLong(digits);
                            if (val > highestNumber) {
                                highestNumber = val;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {}

        long nextNum = highestNumber + 1;
        return String.format("APP-%04d", nextNum);
    }
}
