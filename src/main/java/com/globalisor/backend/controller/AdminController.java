package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.model.Notification;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.repository.NotificationRepository;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.globalisor.backend.security.EncryptionUtils;
import com.globalisor.backend.model.Onboarding;
import com.globalisor.backend.model.Kyc;
import com.globalisor.backend.model.Compliance;
import com.globalisor.backend.model.ClientDocument;
import com.globalisor.backend.model.Message;
import com.globalisor.backend.model.StarredMessage;
import com.globalisor.backend.model.Invoice;
import com.globalisor.backend.model.CallHistory;
import com.globalisor.backend.repository.OnboardingRepository;
import com.globalisor.backend.repository.KycRepository;
import com.globalisor.backend.repository.ComplianceRepository;
import com.globalisor.backend.repository.ClientDocumentRepository;
import com.globalisor.backend.repository.MessageRepository;
import com.globalisor.backend.repository.StarredMessageRepository;
import com.globalisor.backend.repository.InvoiceRepository;
import com.globalisor.backend.repository.CallHistoryRepository;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class AdminController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    EncryptionUtils encryptionUtils;

    @Autowired
    OnboardingRepository onboardingRepository;

    @Autowired
    KycRepository kycRepository;

    @Autowired
    ComplianceRepository complianceRepository;

    @Autowired
    ClientDocumentRepository clientDocumentRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    StarredMessageRepository starredMessageRepository;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    CallHistoryRepository callHistoryRepository;

    @GetMapping("/clients/{id}/services")
    public ResponseEntity<?> getClientServices(@PathVariable String id) {
        User user = userRepository.findAll().stream()
                .filter(u -> u.getId() != null && u.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);

        if (user == null) {
            user = new User();
            user.setId(id);
            user.setFirstName("Client " + id);
            user.setLastName("");
            user.setEmail(id.toLowerCase() + "@globalisor.com");
        }

        List<Requirement> requirements = requirementRepository.findAll();
        List<Map<String, Object>> userServices = new ArrayList<>();

        for (Requirement r : requirements) {
            if (r.getUserId() != null && r.getUserId().equalsIgnoreCase(id)) {
                Map<String, Object> service = new HashMap<>();
                service.put("serviceId", r.getId());
                service.put("status", r.getStatus());
                service.put("date", r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : new Date().toString());

                Map<String, Object> data = r.getData();
                String compName = (user != null && user.getCompanyName() != null) ? user.getCompanyName() : "New Incorporation";
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List) {
                        List<String> names = (List<String>) namesObj;
                        if (!names.isEmpty()) compName = names.get(0);
                    }
                }
                if (data != null && data.containsKey("excelData")) {
                    Object excelObj = data.get("excelData");
                    if (excelObj instanceof Map && ((Map<?, ?>) excelObj).containsKey("companyName")) {
                        compName = String.valueOf(((Map<?, ?>) excelObj).get("companyName"));
                    }
                }

                service.put("companyName", compName);
                service.put("serviceType", "Company Incorporation");
                service.put("details", data);
                service.put("sectionStatuses", r.getSectionStatuses());
                service.put("totalPrice", "SGD 1,500");
                service.put("staff", r.getStaff() != null ? r.getStaff() : "Unassigned");
                userServices.add(service);
            }
        }

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> clientMap = new HashMap<>();
        clientMap.put("clientId", user.getId());
        clientMap.put("name", ((user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "")).trim());
        clientMap.put("email", user.getEmail());
        clientMap.put("companyName", user.getCompanyName());
        clientMap.put("createdAt", new Date());

        response.put("client", clientMap);
        response.put("services", userServices);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/clients/{id}/company")
    public ResponseEntity<?> getClientCompanyProfile(@PathVariable String id) {
        User user = userRepository.findAll().stream()
                .filter(u -> u.getId() != null && u.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);

        Optional<Requirement> reqOpt = requirementRepository.findAll().stream()
                .filter(r -> r.getUserId() != null && r.getUserId().equalsIgnoreCase(id))
                .findFirst();

        Map<String, Object> response = new HashMap<>();
        if (user != null) {
            response.put("id", user.getId());
            response.put("clientId", user.getId());
            response.put("name", ((user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "")).trim());
            response.put("email", user.getEmail());
            response.put("companyName", user.getCompanyName());
            response.put("phone", user.getPhone());
        } else {
            response.put("id", id);
            response.put("clientId", id);
        }

        if (reqOpt.isPresent()) {
            Requirement req = reqOpt.get();
            response.put("serviceId", req.getId());
            response.put("status", req.getStatus());
            response.put("staff", req.getStaff());
            Map<String, Object> data = req.getData();
            if (data != null) {
                response.put("details", data);
                if (data.containsKey("excelData")) {
                    response.put("excelData", data.get("excelData"));
                }
                if (data.containsKey("names")) {
                    response.put("names", data.get("names"));
                }
                if (data.containsKey("uen")) {
                    response.put("uen", data.get("uen"));
                }
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/clients/{id}/excel-upload")
    public ResponseEntity<?> uploadClientExcelData(@PathVariable String id, @RequestBody Map<String, Object> excelData) {
        User user;
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else {
            user = userRepository.findAll().stream()
                    .filter(u -> u.getId() != null && u.getId().equalsIgnoreCase(id))
                    .findFirst()
                    .orElseGet(() -> {
                        User newUser = new User();
                        newUser.setId(id);
                        newUser.setFirstName("Client " + id);
                        newUser.setLastName("");
                        newUser.setEmail(id.toLowerCase().replaceAll("[^a-z0-9]", "") + "@example.com");
                        newUser.setPassword(encoder.encode("password123"));
                        newUser.setRole("USER");
                        return newUser;
                    });
        }

        Optional<Requirement> reqOpt = requirementRepository.findByUserId(id);
        if (reqOpt.isEmpty()) {
            reqOpt = requirementRepository.findAll().stream()
                    .filter(r -> r.getUserId() != null && r.getUserId().equalsIgnoreCase(id))
                    .findFirst();
        }

        Requirement requirement;
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
        } else {
            requirement = new Requirement();
            requirement.setId("SRV-" + System.currentTimeMillis());
            requirement.setUserId(id);
            requirement.setStatus("approved");
            requirement.setStaff("Sarah Lim");
        }

        Map<String, Object> data = requirement.getData() != null ? requirement.getData() : new HashMap<>();
        data.put("excelData", excelData);

        if (excelData.containsKey("companyName") && excelData.get("companyName") != null) {
            String companyName = String.valueOf(excelData.get("companyName")).trim();
            boolean isPersonName = companyName.length() < 3 || (!companyName.contains(" ") && !companyName.toUpperCase().endsWith("LTD"));
            boolean isPteLtd = companyName.toUpperCase().contains("PTE") || companyName.toUpperCase().contains("LTD") || companyName.toUpperCase().contains("LIMITED") || companyName.toUpperCase().contains("INC");

            if (!companyName.isEmpty() && !"null".equalsIgnoreCase(companyName) && !"N/A".equalsIgnoreCase(companyName) && (!isPersonName || isPteLtd)) {
                String existingName = user.getFirstName();
                boolean isGeneric = (existingName == null || existingName.isEmpty() || existingName.startsWith("Client ") || "Client".equalsIgnoreCase(existingName) || (!existingName.contains(" ") && existingName.length() < 10));

                if (isGeneric || isPteLtd) {
                    user.setFirstName(companyName);
                    user.setLastName("");
                    data.put("names", Arrays.asList(companyName));
                } else if (!data.containsKey("names") || data.get("names") == null) {
                    data.put("names", Arrays.asList(existingName));
                }
            }
        }
        if (excelData.containsKey("uen") && excelData.get("uen") != null) {
            String uenStr = String.valueOf(excelData.get("uen")).trim();
            if (!uenStr.isEmpty() && !"null".equalsIgnoreCase(uenStr) && !"N/A".equalsIgnoreCase(uenStr)) {
                data.put("uen", uenStr);
            }
        }

        userRepository.save(user);

        requirement.setData(data);
        requirement.setUpdatedAt(new Date());
        requirementRepository.save(requirement);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Client Excel register data updated successfully.");
        response.put("details", data);
        return ResponseEntity.ok(response);
    }

    @Autowired
    private com.globalisor.backend.service.NotificationService notificationService;

    @PatchMapping("/services/{id}")
    public ResponseEntity<?> updateServiceStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<Requirement> reqOpt = requirementRepository.findById(id);
        if (reqOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        Requirement req = reqOpt.get();
        String oldStaff = req.getStaff();
        boolean statusChanged = false;
        String newStatus = null;
        if (body.containsKey("status")) {
            newStatus = (String) body.get("status");
            if (!Objects.equals(newStatus, req.getStatus())) {
                req.setStatus(newStatus);
                statusChanged = true;
            }
        }
        
        boolean staffChanged = false;
        String newStaff = null;
        if (body.containsKey("staff")) {
            newStaff = (String) body.get("staff");
            if (!Objects.equals(newStaff, req.getStaff())) {
                req.setStaff(newStaff);
                staffChanged = true;
            }
        }
        
        if (body.containsKey("sectionStatuses")) {
            req.setSectionStatuses((Map<String, Map<String, Object>>) body.get("sectionStatuses"));
        }
        req.setUpdatedAt(new Date());
        Requirement saved = requirementRepository.save(req);

        String companyName = "Your incorporation application";
        Map<String, Object> data = req.getData();
        if (data != null && data.containsKey("names")) {
            Object namesObj = data.get("names");
            if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                companyName = ((List<?>) namesObj).get(0).toString();
            }
        }

        if (statusChanged) {
            String clientPriority = "Info";
            if ("approved".equalsIgnoreCase(newStatus) || "completed".equalsIgnoreCase(newStatus)) {
                clientPriority = "Critical";
            } else if ("rejected".equalsIgnoreCase(newStatus)) {
                clientPriority = "Warning";
            }
            
            try {
                // Notify Client
                notificationService.sendNotification(
                        req.getUserId(),
                        "Application Status Update",
                        "The status of " + companyName + " has been updated to '" + newStatus + "'.",
                        "status_update",
                        req.getId(),
                        clientPriority
                );
                
                // Notify Admin / Staff
                if ("approved".equalsIgnoreCase(newStatus) || "completed".equalsIgnoreCase(newStatus)) {
                    notificationService.sendNotification(
                            "admin",
                            "Application Approved",
                            companyName + " has been approved/completed.",
                            "application",
                            req.getId(),
                            "Critical"
                    );
                } else if ("rejected".equalsIgnoreCase(newStatus)) {
                    notificationService.sendNotification(
                            "admin",
                            "Application Rejected",
                            companyName + " has been rejected.",
                            "application",
                            req.getId(),
                            "Critical"
                    );
                }
                
                // Staff Assignment Accepted / Rejected
                if ("In Progress".equalsIgnoreCase(newStatus) && req.getStaff() != null && !req.getStaff().equalsIgnoreCase("Unassigned")) {
                    notificationService.sendNotification(
                            "admin",
                            "Staff Assignment Accepted",
                            req.getStaff() + " has accepted the assignment for " + companyName + ".",
                            "assignment",
                            req.getId(),
                            "Info"
                    );
                } else if (("escalated".equalsIgnoreCase(newStatus) || "rejected".equalsIgnoreCase(newStatus)) && req.getStaff() != null) {
                    notificationService.sendNotification(
                            "admin",
                            "Staff Assignment Rejected",
                            req.getStaff() + " has returned/rejected the assignment for " + companyName + ".",
                            "assignment",
                            req.getId(),
                            "Warning"
                    );
                }
            } catch (Exception e) {}
        }

        if (staffChanged && newStaff != null) {
            String staffId = null;
            if (!newStaff.equalsIgnoreCase("Unassigned")) {
                List<User> users = userRepository.findAll();
                for (User u : users) {
                    if ("STAFF".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole())) {
                        String fullName = (u.getFirstName() + " " + u.getLastName()).trim();
                        if (fullName.equalsIgnoreCase(newStaff.trim())) {
                            staffId = u.getId();
                            break;
                        }
                    }
                }
            }
            
            try {
                // 1. Notify newly assigned staff
                if (staffId != null) {
                    notificationService.sendNotification(
                            staffId,
                            "New Application Assigned",
                            "You have been assigned to " + companyName + ".",
                            "assignment",
                            req.getId(),
                            "Info"
                    );
                }
                
                // 2. Notify old staff if reassigned
                if (oldStaff != null && !oldStaff.equalsIgnoreCase("Unassigned") && !oldStaff.equalsIgnoreCase(newStaff)) {
                    String oldStaffId = null;
                    List<User> users = userRepository.findAll();
                    for (User u : users) {
                        String fullName = (u.getFirstName() + " " + u.getLastName()).trim();
                        if (fullName.equalsIgnoreCase(oldStaff.trim())) {
                            oldStaffId = u.getId();
                            break;
                        }
                    }
                    if (oldStaffId != null) {
                        notificationService.sendNotification(
                                oldStaffId,
                                "Application Reassigned",
                                "Application " + companyName + " has been reassigned to " + newStaff + ".",
                                "assignment",
                                req.getId(),
                                "Warning"
                        );
                    }
                }
                
                // 3. Notify Client
                notificationService.sendNotification(
                        req.getUserId(),
                        "Staff Assigned",
                        newStaff.equalsIgnoreCase("Unassigned") 
                            ? "Specialist has been unassigned from your application."
                            : newStaff + " has been assigned to guide you through your " + companyName + " application.",
                        "assignment",
                        req.getId(),
                        "Info"
                );
            } catch (Exception e) {}
        }
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/documents/request")
    public ResponseEntity<?> requestDocument(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String documentType = body.get("documentType");
        
        if (clientId == null || documentType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId and documentType are required"));
        }
        
        try {
            // Client Notification
            notificationService.sendNotification(
                    clientId,
                    "Document Upload Request",
                    "Please upload your " + documentType + " as requested.",
                    "document_request",
                    documentType,
                    "Warning"
            );
        } catch (Exception e) {}
        
        return ResponseEntity.ok(Map.of("success", true));
    }



    @GetMapping("/services/debug/{id}")
    public ResponseEntity<?> debugService(@PathVariable String id) {
        Optional<Requirement> reqOpt = requirementRepository.findById(id);
        if (reqOpt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(reqOpt.get());
    }

    @PostMapping("/admin/staff")
    public ResponseEntity<?> createStaff(@RequestBody Map<String, String> body) {
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");
        
        // Generate email: firstname.lastname@globalisor.com
        String baseEmail = (firstName + "." + lastName).toLowerCase().replaceAll("[^a-z0-9]", "");
        String email = baseEmail + "@globalisor.com";
        
        // Check uniqueness and append suffix if exists
        String encryptedEmail = encryptionUtils.encryptQueryable(email);
        int suffix = 1;
        while (userRepository.existsByEmail(encryptedEmail)) {
            email = baseEmail + suffix + "@globalisor.com";
            encryptedEmail = encryptionUtils.encryptQueryable(email);
            suffix++;
        }
        
        // Generate password: Glob-[4-digit-number]
        int randomNum = (int) (Math.random() * 9000) + 1000;
        String rawPassword = "Glob-" + randomNum;
        String encodedPassword = encoder.encode(rawPassword);
        
        User staff = new User(firstName, lastName, email, encodedPassword);
        staff.setRole("STAFF");
        staff.setPlainPassword(rawPassword);
        userRepository.save(staff);
        
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "new_user");
            Map<String, Object> uMap = new HashMap<>();
            uMap.put("id", staff.getId());
            uMap.put("name", (staff.getFirstName() + " " + staff.getLastName()).trim());
            uMap.put("role", staff.getRole());
            uMap.put("email", staff.getEmail());
            event.put("user", uMap);
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {
            // ignore
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("email", email);
        response.put("password", rawPassword);
        response.put("id", staff.getId());
        response.put("firstName", firstName);
        response.put("lastName", lastName);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/staff")
    public ResponseEntity<?> getStaffList() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> staffList = new ArrayList<>();
        for (User u : users) {
            if ("STAFF".equalsIgnoreCase(u.getRole())) {
                Map<String, Object> staff = new HashMap<>();
                staff.put("id", u.getId());
                staff.put("firstName", u.getFirstName());
                staff.put("lastName", u.getLastName());
                staff.put("email", u.getEmail());
                staff.put("password", u.getPlainPassword() != null ? u.getPlainPassword() : "password123");
                staffList.add(staff);
            }
        }
        return ResponseEntity.ok(staffList);
    }

    @PutMapping("/admin/staff/update")
    public ResponseEntity<?> updateStaff(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");
        
        String encryptedEmail = encryptionUtils.encryptQueryable(email);
        Optional<User> userOpt = userRepository.findByEmail(encryptedEmail);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User staff = userOpt.get();
        if (firstName != null) staff.setFirstName(firstName);
        if (lastName != null) staff.setLastName(lastName);
        
        userRepository.save(staff);
        
        Map<String, String> response = new HashMap<>();
        response.put("email", email);
        response.put("id", staff.getId());
        response.put("firstName", staff.getFirstName());
        response.put("lastName", staff.getLastName());
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/admin/staff/{id}")
    public ResponseEntity<?> deleteStaff(@PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }



    @GetMapping("/admin/clients")
    public ResponseEntity<?> getClientList() {
        List<User> users = userRepository.findAll();
        List<Requirement> allRequirements = requirementRepository.findAll();
        List<ClientDocument> allDocuments = clientDocumentRepository.findAll();
        List<Onboarding> allOnboardings = onboardingRepository.findAll();

        List<Map<String, Object>> clientList = new ArrayList<>();
        for (User u : users) {
            String role = u.getRole();
            boolean isClient = true;
            if (role != null) {
                String trimmedRole = role.trim();
                if (trimmedRole.equalsIgnoreCase("ADMIN") || trimmedRole.equalsIgnoreCase("STAFF")) {
                    isClient = false;
                }
            }
            if (!isClient) continue;

            Map<String, Object> client = new HashMap<>();
            client.put("id", u.getId());
            client.put("firstName", u.getFirstName() != null ? u.getFirstName() : "");
            client.put("lastName", u.getLastName() != null ? u.getLastName() : "");
            client.put("name", ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim());
            client.put("email", u.getEmail() != null ? u.getEmail() : "");
            client.put("password", u.getPlainPassword() != null ? u.getPlainPassword() : "");
            client.put("hasPassword", u.getPlainPassword() != null && !u.getPlainPassword().isEmpty());
            client.put("phone", u.getPhone() != null ? u.getPhone() : "");
            client.put("role", u.getRole() != null ? u.getRole() : "USER");
            client.put("loginUrl", "/login.html");

            // Company Name resolution
            String companyName = u.getCompanyName();
            String status = "Active";
            for (Requirement r : allRequirements) {
                if (r.getUserId() != null && r.getUserId().equalsIgnoreCase(u.getId())) {
                    if (r.getStatus() != null && !r.getStatus().isEmpty()) {
                        status = r.getStatus();
                    }
                    if (companyName == null || companyName.isEmpty()) {
                        Map<String, Object> data = r.getData();
                        if (data != null && data.containsKey("names")) {
                            Object namesObj = data.get("names");
                            if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                                companyName = String.valueOf(((List<?>) namesObj).get(0));
                            }
                        }
                        if ((companyName == null || companyName.isEmpty()) && data != null && data.containsKey("excelData")) {
                            Object excelObj = data.get("excelData");
                            if (excelObj instanceof Map && ((Map<?, ?>) excelObj).containsKey("companyName")) {
                                companyName = String.valueOf(((Map<?, ?>) excelObj).get("companyName"));
                            }
                        }
                    }
                }
            }
            if (companyName == null || companyName.isEmpty() || "null".equalsIgnoreCase(companyName)) {
                companyName = "Globalisor Entity (" + u.getId() + ")";
            }
            client.put("companyName", companyName);
            client.put("status", status);

            // Document count & initialization
            String clientFullName = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
            long docCount = ensureClientDocumentSuite(u.getId(), clientFullName, companyName);
            client.put("docsCount", docCount);

            // Onboarding portal activation
            boolean portalActivated = true;
            for (Onboarding ob : allOnboardings) {
                if (ob.getClientId() != null && ob.getClientId().equalsIgnoreCase(u.getId())) {
                    portalActivated = ob.isPortalActivated();
                    break;
                }
            }
            client.put("portalActivated", portalActivated);

            clientList.add(client);
        }
        return ResponseEntity.ok(clientList);
    }

    @PostMapping("/admin/clients/batch-generate")
    public ResponseEntity<?> batchGenerateClientCredentials(@RequestBody(required = false) Map<String, Object> body) {
        boolean forceAll = body != null && Boolean.TRUE.equals(body.get("forceRegenerate"));
        List<User> allUsers = userRepository.findAll();
        List<Requirement> allRequirements = requirementRepository.findAll();

        int generatedCount = 0;
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (User u : allUsers) {
            String role = u.getRole();
            boolean isClient = true;
            if (role != null) {
                String trimmedRole = role.trim();
                if (trimmedRole.equalsIgnoreCase("ADMIN") || trimmedRole.equalsIgnoreCase("STAFF")) {
                    isClient = false;
                }
            }
            if (!isClient) continue;

            boolean needsPassword = forceAll || u.getPlainPassword() == null || u.getPlainPassword().isEmpty() || "password123".equals(u.getPlainPassword());
            String rawPassword = u.getPlainPassword();
            if (needsPassword) {
                int randomNum = (int) (Math.random() * 9000) + 1000;
                rawPassword = "Glob-" + randomNum;
                u.setPlainPassword(rawPassword);
                u.setPassword(encoder.encode(rawPassword));
                generatedCount++;
            }

            // Ensure valid email
            String email = u.getEmail();
            if (email == null || !email.contains("@") || email.endsWith("@example.com") || email.startsWith("client_sync_")) {
                String fName = u.getFirstName() != null ? u.getFirstName() : "client";
                String lName = u.getLastName() != null ? u.getLastName() : "";
                String base = (fName + (lName.isEmpty() ? "" : "." + lName)).toLowerCase().replaceAll("[^a-z0-9]", "");
                if (base.isEmpty()) base = "client." + (u.getId() != null ? u.getId().toLowerCase().replaceAll("[^a-z0-9]", "") : "user");
                email = base + "@client.globalisor.com";
                u.setEmail(email);
            }

            // Company Name
            String companyName = u.getCompanyName();
            if (companyName == null || companyName.isEmpty()) {
                for (Requirement r : allRequirements) {
                    if (r.getUserId() != null && r.getUserId().equalsIgnoreCase(u.getId())) {
                        Map<String, Object> data = r.getData();
                        if (data != null && data.containsKey("names")) {
                            Object namesObj = data.get("names");
                            if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                                companyName = String.valueOf(((List<?>) namesObj).get(0));
                            }
                        }
                    }
                }
            }
            if (companyName != null && !companyName.isEmpty()) {
                u.setCompanyName(companyName);
            }

            u.setRole("USER");
            userRepository.save(u);

            // Ensure Onboarding Record is activated
            Optional<Onboarding> obOpt = onboardingRepository.findByClientId(u.getId());
            if (obOpt.isEmpty()) {
                Onboarding ob = new Onboarding();
                ob.setClientId(u.getId());
                ob.setClientName(u.getFirstName() + " " + u.getLastName());
                ob.setClientEmail(u.getEmail());
                ob.setStatus("in_progress");
                ob.setPortalActivated(true);
                ob.setProgressPercent(10);
                ob.setUpdatedAt(System.currentTimeMillis());
                ob.getAuditLogs().add("Migrated client credentials initialized by administrator.");
                onboardingRepository.save(ob);
            } else {
                Onboarding ob = obOpt.get();
                ob.setPortalActivated(true);
                ob.setClientEmail(u.getEmail());
                ob.setClientName(u.getFirstName() + " " + u.getLastName());
                onboardingRepository.save(ob);
            }

            // Ensure KYC profile exists
            if (kycRepository.findByClientId(u.getId()).isEmpty()) {
                Kyc kyc = new Kyc();
                kyc.setId("KYC-" + System.currentTimeMillis() + "-" + u.getId());
                kyc.setClientId(u.getId());
                kyc.setName(u.getFirstName() + " " + u.getLastName());
                kyc.setIdType("Passport / NRIC");
                kyc.setIdNum("Verified on Migration");
                kyc.setNation("Singapore");
                kyc.setStatus("approved");
                kyc.setRisk("Low");
                kyc.setLastUpdated(System.currentTimeMillis());
                kyc.getAuditLogs().add("Migrated KYC record active.");
                kycRepository.save(kyc);
            }

            // Ensure Compliance record exists
            if (complianceRepository.findByClientId(u.getId()).isEmpty()) {
                Compliance comp = new Compliance();
                comp.setId("COMP-" + System.currentTimeMillis() + "-" + u.getId());
                comp.setClientId(u.getId());
                comp.setName(u.getFirstName() + " " + u.getLastName());
                comp.setType("AML Screening & Annual Filing");
                comp.setStatus("approved");
                comp.setRisk("Low");
                comp.setLastUpdated(System.currentTimeMillis());
                comp.getAuditLogs().add("Migrated compliance monitoring active.");
                complianceRepository.save(comp);
            }

            // Ensure Documents Suite exists
            long docsCount = ensureClientDocumentSuite(u.getId(), u.getFirstName() + " " + u.getLastName(), companyName);

            Map<String, Object> cMap = new HashMap<>();
            cMap.put("id", u.getId());
            cMap.put("firstName", u.getFirstName());
            cMap.put("lastName", u.getLastName());
            cMap.put("name", u.getFirstName() + " " + u.getLastName());
            cMap.put("email", u.getEmail());
            cMap.put("password", rawPassword);
            cMap.put("companyName", companyName != null ? companyName : "Globalisor Entity");
            cMap.put("docsCount", docsCount);
            cMap.put("loginUrl", "/login.html");
            cMap.put("status", "Active");
            resultList.add(cMap);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalClients", resultList.size());
        response.put("updatedCount", generatedCount);
        response.put("message", "Generated credentials for " + resultList.size() + " migrated clients.");
        response.put("clients", resultList);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/clients")
    public ResponseEntity<?> createClient(@RequestBody Map<String, String> body) {
        String firstName = body.get("firstName");
        String lastName = body.getOrDefault("lastName", "");
        String email = body.get("email");
        String companyName = body.get("companyName");
        String customPassword = body.get("password");
        String phone = body.getOrDefault("phone", "");

        if (firstName == null || firstName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "First name is required."));
        }

        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            String base = (firstName + (lastName.isEmpty() ? "" : "." + lastName)).toLowerCase().replaceAll("[^a-z0-9]", "");
            email = base + "@client.globalisor.com";
        }

        String encryptedEmail = encryptionUtils.encryptQueryable(email);
        int suffix = 1;
        String baseEmail = email.contains("@") ? email.split("@")[0] : email;
        String domain = email.contains("@") ? email.split("@")[1] : "client.globalisor.com";
        while (userRepository.existsByEmail(encryptedEmail)) {
            email = baseEmail + suffix + "@" + domain;
            encryptedEmail = encryptionUtils.encryptQueryable(email);
            suffix++;
        }

        String rawPassword;
        if (customPassword != null && !customPassword.trim().isEmpty()) {
            rawPassword = customPassword.trim();
        } else {
            int randomNum = (int) (Math.random() * 9000) + 1000;
            rawPassword = "Glob-" + randomNum;
        }

        String encodedPassword = encoder.encode(rawPassword);
        User client = new User(firstName, lastName, email, encodedPassword);
        client.setId("C-" + System.currentTimeMillis());
        client.setRole("USER");
        client.setPlainPassword(rawPassword);
        client.setCompanyName(companyName != null && !companyName.isEmpty() ? companyName : firstName + " Venture Pte. Ltd.");
        client.setPhone(phone);
        userRepository.save(client);

        // Auto-initialize Requirement (Company entity)
        Requirement req = new Requirement();
        req.setId("SRV-" + System.currentTimeMillis());
        req.setUserId(client.getId());
        req.setStatus("approved");
        req.setStaff("Sarah Lim");
        Map<String, Object> data = new HashMap<>();
        data.put("names", Arrays.asList(client.getCompanyName()));
        data.put("serviceType", "Company Incorporation");
        req.setData(data);
        req.setUpdatedAt(new Date());
        requirementRepository.save(req);

        // Auto-initialize KYC
        Kyc kyc = new Kyc();
        kyc.setId("KYC-" + System.currentTimeMillis());
        kyc.setClientId(client.getId());
        kyc.setName(client.getFirstName() + " " + client.getLastName());
        kyc.setIdType("Passport / NRIC");
        kyc.setIdNum("Pending Submission");
        kyc.setNation("Singapore");
        kyc.setStatus("pending");
        kyc.setRisk("Low");
        kyc.setLastUpdated(System.currentTimeMillis());
        kyc.getAuditLogs().add("KYC profile initialized on client creation.");
        kycRepository.save(kyc);

        // Auto-initialize Compliance
        Compliance comp = new Compliance();
        comp.setId("COMP-" + System.currentTimeMillis());
        comp.setClientId(client.getId());
        comp.setName(client.getFirstName() + " " + client.getLastName());
        comp.setType("AML Screening & Corporate Secretarial");
        comp.setStatus("pending");
        comp.setRisk("Low");
        comp.setLastUpdated(System.currentTimeMillis());
        comp.getAuditLogs().add("Compliance initialized.");
        complianceRepository.save(comp);

        // Auto-initialize Onboarding
        Onboarding ob = new Onboarding();
        ob.setClientId(client.getId());
        ob.setClientName(client.getFirstName() + " " + client.getLastName());
        ob.setClientEmail(client.getEmail());
        ob.setStatus("in_progress");
        ob.setPortalActivated(true);
        ob.setProgressPercent(10);
        ob.setUpdatedAt(System.currentTimeMillis());
        onboardingRepository.save(ob);

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "new_user");
            Map<String, Object> uMap = new HashMap<>();
            uMap.put("id", client.getId());
            uMap.put("name", (client.getFirstName() + " " + client.getLastName()).trim());
            uMap.put("role", client.getRole());
            uMap.put("email", client.getEmail());
            event.put("user", uMap);
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {}

        Map<String, Object> response = new HashMap<>();
        response.put("id", client.getId());
        response.put("firstName", client.getFirstName());
        response.put("lastName", client.getLastName());
        response.put("name", client.getFirstName() + " " + client.getLastName());
        response.put("email", client.getEmail());
        response.put("password", rawPassword);
        response.put("companyName", client.getCompanyName());
        response.put("phone", client.getPhone());
        response.put("loginUrl", "/login.html");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/clients/{id}/reset-password")
    public ResponseEntity<?> resetClientPassword(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        User client = userOpt.get();
        String rawPassword;
        if (body != null && body.containsKey("password") && body.get("password") != null && !body.get("password").trim().isEmpty()) {
            rawPassword = body.get("password").trim();
        } else {
            int randomNum = (int) (Math.random() * 9000) + 1000;
            rawPassword = "Glob-" + randomNum;
        }

        client.setPlainPassword(rawPassword);
        client.setPassword(encoder.encode(rawPassword));
        userRepository.save(client);

        Map<String, Object> response = new HashMap<>();
        response.put("id", client.getId());
        response.put("name", client.getFirstName() + " " + client.getLastName());
        response.put("email", client.getEmail());
        response.put("password", rawPassword);
        response.put("companyName", client.getCompanyName());
        response.put("loginUrl", "/login.html");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/admin/clients/{id}")
    public ResponseEntity<?> updateClient(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        User client = userOpt.get();
        if (body.containsKey("firstName")) client.setFirstName(body.get("firstName"));
        if (body.containsKey("lastName")) client.setLastName(body.get("lastName"));
        if (body.containsKey("email")) client.setEmail(body.get("email"));
        if (body.containsKey("phone")) client.setPhone(body.get("phone"));
        if (body.containsKey("companyName")) {
            String cName = body.get("companyName");
            client.setCompanyName(cName);
            // Also update requirement company name if present
            List<Requirement> reqs = requirementRepository.findAll();
            for (Requirement r : reqs) {
                if (r.getUserId() != null && r.getUserId().equalsIgnoreCase(id)) {
                    Map<String, Object> data = r.getData() != null ? r.getData() : new HashMap<>();
                    data.put("names", Arrays.asList(cName));
                    r.setData(data);
                    requirementRepository.save(r);
                }
            }
        }
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").trim().isEmpty()) {
            String rawPassword = body.get("password").trim();
            client.setPlainPassword(rawPassword);
            client.setPassword(encoder.encode(rawPassword));
        }

        userRepository.save(client);

        Map<String, Object> response = new HashMap<>();
        response.put("id", client.getId());
        response.put("firstName", client.getFirstName());
        response.put("lastName", client.getLastName());
        response.put("name", client.getFirstName() + " " + client.getLastName());
        response.put("email", client.getEmail());
        response.put("password", client.getPlainPassword() != null ? client.getPlainPassword() : "");
        response.put("companyName", client.getCompanyName());
        response.put("phone", client.getPhone());
        response.put("loginUrl", "/login.html");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/admin/clients/{id}")
    public ResponseEntity<?> deleteClient(@PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        userRepository.deleteById(id);

        // Delete associated records
        List<Requirement> reqs = requirementRepository.findAll();
        for (Requirement r : reqs) {
            if (r.getUserId() != null && r.getUserId().equalsIgnoreCase(id)) {
                requirementRepository.deleteById(r.getId());
            }
        }
        onboardingRepository.findByClientId(id).ifPresent(ob -> onboardingRepository.deleteById(ob.getId()));
        kycRepository.findByClientId(id).ifPresent(k -> kycRepository.deleteById(k.getId()));
        complianceRepository.findByClientId(id).ifPresent(c -> complianceRepository.deleteById(c.getId()));

        return ResponseEntity.ok(Map.of("success", true, "message", "Client deleted successfully."));
    }

    @DeleteMapping("/clients")
    public ResponseEntity<?> clearAllClients() {
        List<User> allUsers = userRepository.findAll();
        for (User u : allUsers) {
            String role = u.getRole();
            boolean isClient = true;
            if (role != null) {
                String trimmedRole = role.trim();
                if (trimmedRole.equalsIgnoreCase("ADMIN") || trimmedRole.equalsIgnoreCase("STAFF")) {
                    isClient = false;
                }
            }
            if (isClient) {
                userRepository.deleteById(u.getId());
            }
        }

        requirementRepository.deleteAll();
        onboardingRepository.deleteAll();
        kycRepository.deleteAll();
        complianceRepository.deleteAll();
        clientDocumentRepository.deleteAll();
        messageRepository.deleteAll();
        starredMessageRepository.deleteAll();
        invoiceRepository.deleteAll();
        callHistoryRepository.deleteAll();
        notificationRepository.deleteAll();

        return ResponseEntity.ok(Map.of("success", true, "message", "Cleared all clients and all associated records."));
    }

    private long ensureClientDocumentSuite(String clientId, String clientName, String companyName) {
        List<ClientDocument> existing = clientDocumentRepository.findByClientId(clientId);
        if (existing != null && !existing.isEmpty()) {
            return existing.size();
        }

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
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

        String name = (clientName != null && !clientName.isEmpty()) ? clientName : "Valued Client";
        String comp = (companyName != null && !companyName.isEmpty()) ? companyName : "Globalisor Entity (" + clientId + ")";

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
            doc.setClientName(name);
            doc.setCompanyName(comp);
            doc.setApplicationId(clientId.replace("C-", "APP-"));
            doc.setService("Company Incorporation");
            doc.setDate(today);
            doc.setFile("/api/documents/" + doc.getId() + "/download");
            doc.setSuggestedModule(t[1]);
            doc.getActivityLogs().add("Document verified and initialized for " + name + " on " + today);
            clientDocumentRepository.save(doc);
        }
        return templates.length;
    }
}


