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
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        User user = userOpt.get();
        List<Requirement> requirements = requirementRepository.findAll();
        List<Map<String, Object>> userServices = new ArrayList<>();
        
        requirements.stream()
            .filter(r -> r.getUserId().equals(id))
            .forEach(r -> {
                Map<String, Object> service = new HashMap<>();
                service.put("serviceId", r.getId());
                service.put("status", r.getStatus());
                service.put("date", r.getUpdatedAt().toString());
                
                Map<String, Object> data = r.getData();
                String companyName = "New Incorporation";
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List) {
                        List<String> names = (List<String>) namesObj;
                        if (!names.isEmpty()) companyName = names.get(0);
                    }
                }
                
                service.put("companyName", companyName);
                service.put("serviceType", "Company Incorporation");
                service.put("details", data);
                service.put("sectionStatuses", r.getSectionStatuses());
                service.put("totalPrice", "SGD 1,500");
                service.put("staff", r.getStaff() != null ? r.getStaff() : "Unassigned");
                userServices.add(service);
            });

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> clientMap = new HashMap<>();
        clientMap.put("clientId", user.getId());
        clientMap.put("name", user.getFirstName() + " " + user.getLastName());
        clientMap.put("email", user.getEmail());
        clientMap.put("createdAt", new Date()); 
        
        response.put("client", clientMap);
        response.put("services", userServices);
        
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
            if (!companyName.isEmpty() && !"null".equalsIgnoreCase(companyName) && !"N/A".equalsIgnoreCase(companyName)) {
                data.put("names", Arrays.asList(companyName));
                
                // Only update User's first name if current name is empty, generic placeholder, or matches default client format
                if (user.getFirstName() == null || user.getFirstName().isEmpty() || user.getFirstName().startsWith("Client ") || "Client".equalsIgnoreCase(user.getFirstName())) {
                    user.setFirstName(companyName);
                    user.setLastName("");
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
}

