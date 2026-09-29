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
        
        Optional<Requirement> req = requirementRepository.findByUserId(userDetails.getId());
        if (req.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", req.get().getStatus());
            response.put("data", req.get().getData());
            response.put("sectionStatuses", req.get().getSectionStatuses());
            response.put("applicationId", req.get().getId());
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
        
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
        Requirement requirement;
        boolean isNew = !reqOpt.isPresent();
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            requirement.setData(data);
            requirement.setUpdatedAt(new java.util.Date());
        } else {
            requirement = new Requirement(userDetails.getId(), data);
        }
        requirementRepository.save(requirement);
        
        if (isNew) {
            try {
                notificationService.sendNotification(
                        "admin",
                        "New Application Created",
                        userDetails.getFirstName() + " " + userDetails.getLastName() + " created a new application.",
                        "application",
                        requirement.getId(),
                        "Info"
                );
            } catch (Exception e) {}
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        response.put("sectionStatuses", requirement.getSectionStatuses());
        response.put("applicationId", requirement.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
        if (reqOpt.isPresent()) {
            Requirement requirement = reqOpt.get();
            requirement.setStatus("under review");
            requirement.setUpdatedAt(new java.util.Date());
            requirementRepository.save(requirement);
            
            Optional<User> userOpt = userRepository.findById(userDetails.getId());
            userOpt.ifPresent(user -> syncWithOnboardingAndKyc(user, requirement.getData(), requirement));
            
            try {
                // Admin notification
                notificationService.sendNotification(
                        "admin",
                        "New Pre-Registration Submission",
                        userDetails.getFirstName() + " " + userDetails.getLastName() + " submitted pre-registration requirements.",
                        "pre-registration",
                        requirement.getId(),
                        "Info"
                );
                // Client notification
                notificationService.sendNotification(
                        userDetails.getId(),
                        "Application Submitted Successfully",
                        "Your pre-registration requirements have been submitted successfully.",
                        "pre-registration",
                        requirement.getId(),
                        "Info"
                );
            } catch (Exception e) {}

            Map<String, Object> response = new HashMap<>();
            response.put("status", requirement.getStatus());
            response.put("data", requirement.getData());
            response.put("sectionStatuses", requirement.getSectionStatuses());
            response.put("applicationId", requirement.getId());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body("No requirement found to submit");
        }
    }

    @PostMapping("/pay")
    public ResponseEntity<?> payRequirement(@RequestBody Map<String, Object> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
        Requirement requirement;
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            requirement.setData(data);
            requirement.setUpdatedAt(new java.util.Date());
        } else {
            requirement = new Requirement(userDetails.getId(), data);
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
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
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
        // 1. Extract contact details
        @SuppressWarnings("unchecked")
        Map<String, Object> contact = (Map<String, Object>) data.get("contact");
        if (contact == null) {
            return ResponseEntity.badRequest().body("Contact information is required");
        }
        String email = (String) contact.get("email");
        String firstName = (String) contact.get("firstName");
        String lastName = (String) contact.get("lastName");
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Contact email is required");
        }
        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

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
            clientUser.setStatus("PENDING_APPROVAL");
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

        // 3. Ensure Onboarding record exists
        // 3. Save the Requirement record (pre-registration application)
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(clientUser.getId());
        Requirement requirement;
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            requirement.setData(data);
            requirement.setStatus("under review");
            requirement.setUpdatedAt(new Date());
        } else {
            requirement = new Requirement(clientUser.getId(), data);
            requirement.setStatus("under review");
        }
        requirementRepository.save(requirement);

        // 4. Synchronize with Onboarding and KYC records
        syncWithOnboardingAndKyc(clientUser, data, requirement);

        // 5. Notify admin/staff
        try {
            notificationService.sendNotification(
                    "admin",
                    "New Pre-Registration Submission",
                    clientUser.getFirstName() + " " + clientUser.getLastName() + " submitted pre-registration requirements.",
                    "pre-registration",
                    requirement.getId(),
                    "Info"
            );
        } catch (Exception e) {}

        // 6. Build response
        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        response.put("applicationId", requirement.getId());
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
            Optional<Onboarding> onboardingOpt = onboardingRepository.findByClientId(clientUser.getId());
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
            Optional<Kyc> kycOpt = kycRepository.findByClientId(clientUser.getId());
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
}
