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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class AdminController {

    @Autowired
    private com.globalisor.backend.service.TaskService taskService;

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

        Optional<Requirement> reqOpt = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(id);
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
            requirement.setStaff("Unassigned");
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

    @PutMapping("/clients/{id}/company")
    public ResponseEntity<?> updateClientCompanyProfile(@PathVariable String id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findAll().stream()
                .filter(u -> u.getId() != null && u.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);

        Optional<Requirement> reqOpt = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(id);
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
            requirement.setStaff("Unassigned");
        }

        Map<String, Object> data = requirement.getData() != null ? requirement.getData() : new HashMap<>();
        if (body.containsKey("details")) {
            Object detailsObj = body.get("details");
            if (detailsObj instanceof Map) {
                data.putAll((Map<String, Object>) detailsObj);
            }
        }
        if (body.containsKey("excelData")) {
            data.put("excelData", body.get("excelData"));
        }

        if (body.containsKey("companyName") && body.get("companyName") != null) {
            String cName = String.valueOf(body.get("companyName")).trim();
            if (!cName.isEmpty() && !"null".equalsIgnoreCase(cName) && !"N/A".equalsIgnoreCase(cName)) {
                if (user != null) {
                    user.setFirstName(cName);
                    user.setLastName("");
                    user.setCompanyName(cName);
                    userRepository.save(user);
                }
                data.put("names", Arrays.asList(cName));
            }
        }

        if (body.containsKey("uen") && body.get("uen") != null) {
            data.put("uen", String.valueOf(body.get("uen")).trim());
        }

        requirement.setData(data);
        requirement.setUpdatedAt(new Date());
        requirementRepository.save(requirement);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Company profile updated successfully.");
        response.put("details", data);
        return ResponseEntity.ok(response);
    }

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

        if (body.containsKey("details")) {
            Object detailsObj = body.get("details");
            if (detailsObj instanceof Map) {
                Map<String, Object> newDetails = (Map<String, Object>) detailsObj;
                req.setData(newDetails);

                if (newDetails.containsKey("excelData")) {
                    Object exObj = newDetails.get("excelData");
                    if (exObj instanceof Map) {
                        Map<?, ?> ex = (Map<?, ?>) exObj;
                        if (ex.containsKey("companyName") && ex.get("companyName") != null) {
                            String cName = String.valueOf(ex.get("companyName")).trim();
                            if (!cName.isEmpty() && !"null".equalsIgnoreCase(cName) && !"N/A".equalsIgnoreCase(cName)) {
                                userRepository.findAll().stream()
                                        .filter(u -> u.getId() != null && u.getId().equalsIgnoreCase(req.getUserId()))
                                        .findFirst()
                                        .ifPresent(u -> {
                                            u.setFirstName(cName);
                                            u.setLastName("");
                                            u.setCompanyName(cName);
                                            userRepository.save(u);
                                        });
                            }
                        }
                    }
                }
            }
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

    @GetMapping("/admin/applications/pending")
    public ResponseEntity<?> getPendingApplications() {
        List<Requirement> requirements = requirementRepository.findAll();
        List<Requirement> pendingReqs = requirements.stream()
                .filter(r -> {
                    String s = r.getStatus() != null ? r.getStatus().toLowerCase() : "";
                    return !s.contains("approved") && !s.contains("completed") && !s.contains("rejected");
                })
                .collect(Collectors.toList());

        Set<String> userIds = pendingReqs.stream()
                .map(Requirement::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Requirement req : pendingReqs) {
            Map<String, Object> item = new HashMap<>();
            User client = userMap.get(req.getUserId());
            item.put("id", req.getId());
            item.put("applicationId", req.getId() != null ? req.getId().replace("SRV-", "APP-") : "");
            item.put("userId", req.getUserId());
            item.put("status", req.getStatus());
            item.put("staff", req.getStaff());
            item.put("assignedStaffId", req.getAssignedStaffId());
            item.put("assignedStaffName", req.getAssignedStaffName());
            item.put("createdAt", req.getCreatedAt());
            item.put("updatedAt", req.getUpdatedAt());
            item.put("data", req.getData());
            item.put("sectionStatuses", req.getSectionStatuses());

            if (client != null) {
                item.put("clientName", ((client.getFirstName() != null ? client.getFirstName() : "") + " " + (client.getLastName() != null ? client.getLastName() : "")).trim());
                item.put("clientEmail", client.getEmail());
                item.put("clientPhone", client.getPhone());
                item.put("accountStatus", client.getStatus());
            } else {
                item.put("clientName", "Applicant");
                item.put("clientEmail", "");
                item.put("clientPhone", "");
            }

            // Extract company name option 1
            String companyName = "Singapore Pte Ltd";
            if (req.getData() != null && req.getData().containsKey("names")) {
                Object namesObj = req.getData().get("names");
                if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                    companyName = String.valueOf(((List<?>) namesObj).get(0));
                }
            }
            item.put("proposedCompanyName", companyName);

            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    private Optional<Requirement> findRequirementFlexible(String id) {
        if (id == null || id.isEmpty()) return Optional.empty();
        Optional<Requirement> req = requirementRepository.findById(id);
        if (req.isPresent()) return req;
        if (id.startsWith("APP-")) {
            req = requirementRepository.findById(id.replace("APP-", "SRV-"));
            if (req.isPresent()) return req;
        }
        if (id.startsWith("SRV-")) {
            req = requirementRepository.findById(id.replace("SRV-", "APP-"));
            if (req.isPresent()) return req;
        }
        req = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(id);
        if (req.isPresent()) return req;
        return Optional.empty();
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

    @GetMapping("/admin/applications/{id}")
    public ResponseEntity<?> getApplicationDetails(@PathVariable String id) {
        Optional<Requirement> reqOpt = findRequirementFlexible(id);
        if (reqOpt.isPresent()) {
            Requirement req = reqOpt.get();
            Map<String, Object> res = new HashMap<>();
            res.put("id", req.getId());
            res.put("userId", req.getUserId());
            res.put("status", req.getStatus());
            res.put("staff", req.getStaff());
            res.put("assignedStaffId", req.getAssignedStaffId());
            res.put("assignedStaffName", req.getAssignedStaffName());
            res.put("rejectionReason", req.getRejectionReason());
            res.put("reviewedAt", req.getReviewedAt());
            res.put("createdAt", req.getCreatedAt());
            res.put("updatedAt", req.getUpdatedAt());
            Map<String, Object> reqData = req.getData() != null ? new HashMap<>(req.getData()) : new HashMap<>();
            normalizeSsicData(reqData);
            res.put("data", reqData);
            res.put("sectionStatuses", req.getSectionStatuses());
            
            if (req.getUserId() != null) {
                userRepository.findById(req.getUserId()).ifPresent(u -> {
                    res.put("clientFirstName", u.getFirstName());
                    res.put("clientLastName", u.getLastName());
                    res.put("clientEmail", u.getEmail());
                    res.put("clientPhone", u.getPhone());
                    res.put("clientStatus", u.getStatus());
                    res.put("clientCompanyName", u.getCompanyName());
                });
            }
            if (!res.containsKey("clientEmail") || res.get("clientEmail") == null || String.valueOf(res.get("clientEmail")).trim().isEmpty()) {
                if (reqData.containsKey("contact") && reqData.get("contact") instanceof Map) {
                    Map<?, ?> c = (Map<?, ?>) reqData.get("contact");
                    if (c.get("email") != null) res.put("clientEmail", c.get("email"));
                    if (c.get("firstName") != null) res.put("clientFirstName", c.get("firstName"));
                    if (c.get("lastName") != null) res.put("clientLastName", c.get("lastName"));
                    if (c.get("phone") != null) res.put("clientPhone", c.get("phone"));
                }
            }
            return ResponseEntity.ok(res);
        }

        // Fallback to Onboarding if standalone
        Optional<Onboarding> obOpt = onboardingRepository.findById(id);
        if (obOpt.isEmpty()) {
            obOpt = onboardingRepository.findFirstByClientIdOrderByCreatedAtDesc(id);
        }
        if (obOpt.isPresent()) {
            Onboarding ob = obOpt.get();
            Map<String, Object> res = new HashMap<>();
            res.put("id", ob.getId());
            res.put("userId", ob.getClientId());
            res.put("status", ob.getStatus());
            res.put("staff", "Unassigned");
            res.put("assignedStaffId", null);
            res.put("assignedStaffName", "Unassigned");
            res.put("createdAt", ob.getCreatedAt());
            res.put("updatedAt", ob.getUpdatedAt());
            
            Map<String, Object> synData = new HashMap<>();
            synData.put("journeyType", ob.getJourneyType());
            res.put("data", synData);

            if (ob.getClientId() != null) {
                userRepository.findById(ob.getClientId()).ifPresent(u -> {
                    res.put("clientFirstName", u.getFirstName());
                    res.put("clientLastName", u.getLastName());
                    res.put("clientEmail", u.getEmail());
                    res.put("clientPhone", u.getPhone());
                    res.put("clientStatus", u.getStatus());
                    res.put("clientCompanyName", u.getCompanyName());
                });
            }
            return ResponseEntity.ok(res);
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping("/admin/applications/{id}/approve")
    public ResponseEntity<?> approveApplication(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Optional<Requirement> reqOpt = findRequirementFlexible(id);
        if (!reqOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Requirement req = reqOpt.get();
        req.setStatus("approved");
        req.setReviewedAt(new Date());

        String staffId = body != null && body.containsKey("staffId") ? String.valueOf(body.get("staffId")) : null;
        String staffName = body != null && body.containsKey("staffName") ? String.valueOf(body.get("staffName")) : null;
        String priority = body != null && body.containsKey("priority") ? String.valueOf(body.get("priority")) : "HIGH";
        String notes = body != null && body.containsKey("notes") ? String.valueOf(body.get("notes")) : "";

        // Resolve staff details if only id or only name passed
        if (staffId != null && !staffId.isEmpty() && (staffName == null || staffName.isEmpty())) {
            userRepository.findById(staffId).ifPresent(u -> {
                req.setAssignedStaffName(((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim());
            });
        }
        if (staffName != null && !staffName.isEmpty()) {
            req.setStaff(staffName);
            req.setAssignedStaffName(staffName);
        }
        if (staffId != null && !staffId.isEmpty()) {
            req.setAssignedStaffId(staffId);
        }

        requirementRepository.save(req);

        // 1. Activate client user
        Optional<User> clientOpt = userRepository.findById(req.getUserId());
        String companyName = "New Company";
        String clientFullName = "Client";
        String clientEmail = "";
        
        if (req.getData() != null && req.getData().containsKey("names")) {
            Object namesObj = req.getData().get("names");
            if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                companyName = String.valueOf(((List<?>) namesObj).get(0));
            }
        }

        if (clientOpt.isPresent()) {
            User client = clientOpt.get();
            client.setStatus("ACTIVE");
            client.setRole("CLIENT");
            if (companyName != null && !companyName.isEmpty() && !"New Company".equals(companyName)) {
                client.setCompanyName(companyName);
            }
            if (staffId != null && !staffId.isEmpty()) {
                client.setAssignedStaffId(staffId);
                client.setAssignedStaffName(req.getAssignedStaffName());
                client.setAssignedAt(System.currentTimeMillis());
            }
            userRepository.save(client);
            clientFullName = ((client.getFirstName() != null ? client.getFirstName() : "") + " " + (client.getLastName() != null ? client.getLastName() : "")).trim();
            clientEmail = client.getEmail();
        }

        // Activate Onboarding and unlock Client Portal
        try {
            final String fClientFullName = clientFullName;
            final String fClientEmail = clientEmail;
            Optional<Onboarding> obOpt = onboardingRepository.findFirstByClientIdOrderByCreatedAtDesc(req.getUserId());
            Onboarding ob = obOpt.orElseGet(() -> {
                Onboarding newOb = new Onboarding();
                newOb.setClientId(req.getUserId());
                newOb.setDisplayClientId("CL-" + (System.currentTimeMillis() % 100000));
                newOb.setClientName(fClientFullName);
                newOb.setClientEmail(fClientEmail);
                newOb.setCreatedAt(System.currentTimeMillis());
                return newOb;
            });
            ob.setStatus("approved");
            ob.setPortalActivated(true);
            ob.setProgressPercent(100);
            ob.setUpdatedAt(System.currentTimeMillis());
            ob.getAuditLogs().add("Application verified and approved by admin. Client portal access activated at " + new Date());
            onboardingRepository.save(ob);
        } catch (Exception obEx) {
            System.err.println("Could not activate onboarding on approval: " + obEx.getMessage());
        }

        // 2. Create Task in Kanban / Task Management
        try {
            com.globalisor.backend.model.Task task = new com.globalisor.backend.model.Task();
            task.setTitle("Incorporate " + companyName + " (ACRA Name Reservation & Filing)");
            task.setDescription("Application approved by Admin. Review shareholder details, draft Constitution, and proceed with ACRA incorporation filing.\n\n" + (notes != null && !notes.isEmpty() ? "Admin Notes: " + notes : ""));
            task.setCategory("Incorporation");
            task.setType("REQUEST");
            task.setTaskScope("CLIENT");
            task.setIsInternal(false);
            task.setStatus("PENDING");
            task.setPriority(priority != null && !priority.isEmpty() ? priority.toUpperCase() : "HIGH");
            task.setClientId(req.getUserId());
            task.setClientName(clientFullName);
            task.setClientEmail(clientEmail);
            task.setCompanyName(companyName);
            
            if (staffId != null && !staffId.isEmpty()) {
                task.setAssignedTo(com.globalisor.backend.model.Task.UserRef.builder()
                        .id(staffId)
                        .name(req.getAssignedStaffName() != null ? req.getAssignedStaffName() : "Staff Member")
                        .role("STAFF")
                        .build());
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            task.setDueDate(sdf.format(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))); // 7 days from now
            taskService.createTask(task);
        } catch (Exception e) {
            System.err.println("Could not auto-create Task on approval: " + e.getMessage());
        }

        // 3. Send notifications
        try {
            notificationService.sendNotification(
                    req.getUserId(),
                    "Application Approved!",
                    "Congratulations! Your incorporation application for " + companyName + " has been approved.",
                    "application_approved",
                    req.getId(),
                    "Critical"
            );

            if (staffId != null && !staffId.isEmpty()) {
                notificationService.sendNotification(
                        staffId,
                        "New Incorporation Task Assigned",
                        "You have been assigned to handle the incorporation for " + companyName + " (" + clientFullName + ").",
                        "task_assignment",
                        req.getId(),
                        "Info"
                );
            }
        } catch (Exception e) {}

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Application approved successfully, client activated, and staff task created.");
        resp.put("requirement", req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/admin/applications/{id}/reject")
    public ResponseEntity<?> rejectApplication(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Optional<Requirement> reqOpt = findRequirementFlexible(id);
        if (!reqOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Requirement req = reqOpt.get();
        req.setStatus("rejected");
        req.setReviewedAt(new Date());

        String reason = body != null && body.containsKey("reason") ? String.valueOf(body.get("reason")) : "Application returned for revision.";
        req.setRejectionReason(reason);

        requirementRepository.save(req);

        // Update user status
        userRepository.findById(req.getUserId()).ifPresent(u -> {
            u.setStatus("REJECTED");
            userRepository.save(u);
        });

        // Sync Onboarding status
        try {
            Optional<Onboarding> obOpt = onboardingRepository.findFirstByClientIdOrderByCreatedAtDesc(req.getUserId());
            if (obOpt.isPresent()) {
                Onboarding ob = obOpt.get();
                ob.setStatus("rejected");
                ob.setPortalActivated(false);
                ob.getAuditLogs().add("Application marked as changes required by admin. Reason: " + reason + " at " + new Date());
                onboardingRepository.save(ob);
            }
        } catch (Exception obEx) {
            System.err.println("Could not update onboarding on rejection: " + obEx.getMessage());
        }

        // Send notification to Client with reason
        try {
            notificationService.sendNotification(
                    req.getUserId(),
                    "Application Needs Revision",
                    "Your application requires attention: " + reason,
                    "application_rejected",
                    req.getId(),
                    "Warning"
            );
        } catch (Exception e) {}

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Application marked as rejected / needs revision.");
        resp.put("requirement", req);
        return ResponseEntity.ok(resp);
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
        String firstName = body.get("firstName") != null ? body.get("firstName").trim() : "";
        String lastName = body.get("lastName") != null ? body.get("lastName").trim() : "";
        String email = body.get("email") != null ? body.get("email").trim() : "";
        String department = body.get("department") != null ? body.get("department").trim() : "Corporate Secretarial & Incorporation";
        String designation = body.get("designation") != null ? body.get("designation").trim() : "Senior Operations Specialist";
        String rawPassword = body.get("password") != null && !body.get("password").trim().isEmpty() ? body.get("password").trim() : null;
        String phone = body.get("phone") != null ? body.get("phone").trim() : "";

        // If email not provided, generate email: firstname.lastname@globalisor.com
        if (email.isEmpty()) {
            String baseEmail = (firstName + "." + lastName).toLowerCase().replaceAll("[^a-z0-9]", "");
            if (baseEmail.isEmpty()) baseEmail = "staff";
            email = baseEmail + "@globalisor.com";
            String encryptedEmail = encryptionUtils.encryptQueryable(email);
            int suffix = 1;
            while (userRepository.existsByEmail(encryptedEmail)) {
                email = baseEmail + suffix + "@globalisor.com";
                encryptedEmail = encryptionUtils.encryptQueryable(email);
                suffix++;
            }
        } else {
            String encryptedEmail = encryptionUtils.encryptQueryable(email);
            if (userRepository.existsByEmail(encryptedEmail)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Error: Email is already registered."));
            }
        }

        // Generate password if none provided: Glob-[4-digit-number]
        if (rawPassword == null || rawPassword.isEmpty()) {
            int randomNum = (int) (Math.random() * 9000) + 1000;
            rawPassword = "Glob-" + randomNum;
        }
        String encodedPassword = encoder.encode(rawPassword);

        User staff = new User(firstName, lastName, email, encodedPassword);
        staff.setRole("STAFF");
        staff.setPlainPassword(rawPassword);
        staff.setDepartment(department);
        staff.setDesignation(designation);
        staff.setPhone(phone);
        staff.setOnlineStatus("ONLINE");
        staff.setCardStatus("ACTIVE");
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

        Map<String, Object> response = new HashMap<>();
        response.put("email", email);
        response.put("password", rawPassword);
        response.put("id", staff.getId());
        response.put("firstName", firstName);
        response.put("lastName", lastName);
        response.put("department", department);
        response.put("designation", designation);
        response.put("phone", phone);
        response.put("onlineStatus", staff.getOnlineStatus());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/staff")
    public ResponseEntity<?> getStaff() {
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
                staff.put("department", u.getDepartment() != null ? u.getDepartment() : "Corporate Secretarial & Incorporation");
                staff.put("designation", u.getDesignation() != null ? u.getDesignation() : "Senior Operations Specialist");
                staff.put("onlineStatus", u.getOnlineStatus() != null ? u.getOnlineStatus() : "ONLINE");
                staff.put("phone", u.getPhone() != null ? u.getPhone() : "");
                staff.put("lastLoginTime", u.getLastLoginTime() != null ? u.getLastLoginTime() : (u.getLastSeenTime() != null ? u.getLastSeenTime() : null));
                staff.put("lastLoginAt", u.getLastLoginAt());
                staff.put("lastSeenTime", u.getLastSeenTime());
                staffList.add(staff);
            }
        }
        return ResponseEntity.ok(staffList);
    }

    @PutMapping("/admin/staff/update")
    public ResponseEntity<?> updateStaff(@RequestBody Map<String, String> body) {
        String id = body.get("id");
        String origEmail = body.get("origEmail");
        String email = body.get("email");
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");
        String department = body.get("department");
        String designation = body.get("designation");
        String password = body.get("password");
        String onlineStatus = body.get("onlineStatus");
        String phone = body.get("phone");

        User staff = null;
        if (id != null && !id.trim().isEmpty()) {
            staff = userRepository.findById(id).orElse(null);
        }
        if (staff == null && origEmail != null && !origEmail.trim().isEmpty()) {
            String encryptedOrigEmail = encryptionUtils.encryptQueryable(origEmail);
            staff = userRepository.findByEmail(encryptedOrigEmail).orElse(null);
            if (staff == null) {
                staff = userRepository.findAll().stream()
                        .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(origEmail))
                        .findFirst().orElse(null);
            }
        }
        if (staff == null && email != null && !email.trim().isEmpty()) {
            String encryptedEmail = encryptionUtils.encryptQueryable(email);
            staff = userRepository.findByEmail(encryptedEmail).orElse(null);
            if (staff == null) {
                staff = userRepository.findAll().stream()
                        .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email))
                        .findFirst().orElse(null);
            }
        }

        if (staff == null) {
            return ResponseEntity.notFound().build();
        }

        if (firstName != null) staff.setFirstName(firstName.trim());
        if (lastName != null) staff.setLastName(lastName.trim());
        if (department != null) staff.setDepartment(department.trim());
        if (designation != null) staff.setDesignation(designation.trim());
        if (onlineStatus != null) staff.setOnlineStatus(onlineStatus.trim());
        if (phone != null) staff.setPhone(phone.trim());

        if (email != null && !email.trim().isEmpty() && !email.equalsIgnoreCase(staff.getEmail())) {
            String checkEmail = email.trim();
            String encryptedCheck = encryptionUtils.encryptQueryable(checkEmail);
            Optional<User> existingOpt = userRepository.findByEmail(encryptedCheck);
            if (existingOpt.isPresent() && !existingOpt.get().getId().equals(staff.getId())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Error: New email is already used by another account."));
            }
            staff.setEmail(checkEmail);
        }

        if (password != null && !password.trim().isEmpty()) {
            staff.setPlainPassword(password.trim());
            staff.setPassword(encoder.encode(password.trim()));
        }

        userRepository.save(staff);

        Map<String, Object> response = new HashMap<>();
        response.put("id", staff.getId());
        response.put("email", staff.getEmail());
        response.put("firstName", staff.getFirstName());
        response.put("lastName", staff.getLastName());
        response.put("department", staff.getDepartment());
        response.put("designation", staff.getDesignation());
        response.put("password", staff.getPlainPassword());
        response.put("onlineStatus", staff.getOnlineStatus());
        response.put("phone", staff.getPhone());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/admin/staff/{id}")
    public ResponseEntity<?> deleteStaff(@PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User staffUser = userOpt.get();
        String staffName = ((staffUser.getFirstName() != null ? staffUser.getFirstName() : "") + " " + (staffUser.getLastName() != null ? staffUser.getLastName() : "")).trim();

        userRepository.deleteById(id);

        // Clean up client assignments referencing deleted staff
        List<User> allClients = userRepository.findAll().stream()
                .filter(u -> !"STAFF".equalsIgnoreCase(u.getRole()) && !"ADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        for (User client : allClients) {
            boolean changed = false;
            if (client.getAssignedStaffId() != null && client.getAssignedStaffId().contains(id)) {
                List<String> ids = Arrays.stream(client.getAssignedStaffId().split(","))
                        .map(String::trim)
                        .filter(s -> !s.equalsIgnoreCase(id) && !s.isEmpty())
                        .collect(Collectors.toList());
                client.setAssignedStaffId(ids.isEmpty() ? null : String.join(", ", ids));
                changed = true;
            }
            if (client.getAssignedStaffName() != null && !staffName.isEmpty() && client.getAssignedStaffName().toLowerCase().contains(staffName.toLowerCase())) {
                List<String> names = Arrays.stream(client.getAssignedStaffName().split(","))
                        .map(String::trim)
                        .filter(s -> !s.equalsIgnoreCase(staffName) && !s.isEmpty() && !s.equalsIgnoreCase("Unassigned"))
                        .collect(Collectors.toList());
                client.setAssignedStaffName(names.isEmpty() ? null : String.join(", ", names));
                changed = true;
            }
            if (changed) {
                userRepository.save(client);
            }
        }

        return ResponseEntity.ok().build();
    }



    @GetMapping("/admin/clients")
    public ResponseEntity<?> getClientList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "search", required = false) String search) {

        List<User> users = userRepository.findAll();

        List<User> clients = users.stream().filter(u -> {
            String role = u.getRole();
            if (role != null) {
                String trimmedRole = role.trim();
                if (trimmedRole.equalsIgnoreCase("ADMIN") || trimmedRole.equalsIgnoreCase("STAFF")) {
                    return false;
                }
            }
            if ("PENDING_APPROVAL".equalsIgnoreCase(u.getStatus())) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toLowerCase();
            clients = clients.stream().filter(u -> {
                String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).toLowerCase();
                String email = (u.getEmail() != null ? u.getEmail() : "").toLowerCase();
                String comp = (u.getCompanyName() != null ? u.getCompanyName() : "").toLowerCase();
                String id = (u.getId() != null ? u.getId() : "").toLowerCase();
                return name.contains(q) || email.contains(q) || comp.contains(q) || id.contains(q);
            }).collect(Collectors.toList());
        }

        int totalElements = clients.size();
        int pageSize = size > 0 ? size : 10;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        if (totalPages == 0) totalPages = 1;

        int currentPage = page > 0 ? page : 1;
        if (currentPage > totalPages) currentPage = totalPages;

        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        List<User> pageUsers = (fromIndex >= 0 && fromIndex < totalElements)
                ? clients.subList(fromIndex, toIndex)
                : Collections.emptyList();

        List<Map<String, Object>> clientList = new ArrayList<>();
        for (User u : pageUsers) {
            Map<String, Object> client = new HashMap<>();
            String userId = u.getId();

            client.put("id", userId);
            client.put("firstName", u.getFirstName() != null ? u.getFirstName() : "");
            client.put("lastName", u.getLastName() != null ? u.getLastName() : "");
            client.put("name", ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim());
            client.put("email", u.getEmail() != null ? u.getEmail() : "");
            client.put("password", u.getPlainPassword() != null ? u.getPlainPassword() : "");
            client.put("hasPassword", u.getPlainPassword() != null && !u.getPlainPassword().isEmpty());
            client.put("phone", u.getPhone() != null ? u.getPhone() : "");
            client.put("role", u.getRole() != null ? u.getRole() : "USER");
            client.put("loginUrl", "/login.html");

            String companyName = u.getCompanyName();
            if (companyName == null || companyName.isEmpty() || "null".equalsIgnoreCase(companyName)) {
                companyName = "Globalisor Entity (" + userId + ")";
            }
            client.put("companyName", companyName);
            client.put("status", "Active");
            client.put("docsCount", 8L);
            client.put("portalActivated", true);

            // Assigned Staff
            client.put("assignedStaffId", u.getAssignedStaffId() != null ? u.getAssignedStaffId() : "");
            client.put("assignedStaffName", u.getAssignedStaffName() != null ? u.getAssignedStaffName() : "Unassigned");
            client.put("assignedStaffEmail", u.getAssignedStaffEmail() != null ? u.getAssignedStaffEmail() : "");
            client.put("assignedAt", u.getAssignedAt());
            client.put("assignedBy", u.getAssignedBy());

            clientList.add(client);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", clientList);
        response.put("clients", clientList);
        response.put("totalElements", totalElements);
        response.put("totalClients", totalElements);
        response.put("totalPages", totalPages);
        response.put("currentPage", currentPage);
        response.put("page", currentPage);
        response.put("pageSize", pageSize);

        return ResponseEntity.ok(response);
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
            Optional<Onboarding> obOpt = onboardingRepository.findFirstByClientIdOrderByCreatedAtDesc(u.getId());
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
            if (kycRepository.findFirstByClientIdOrderByLastUpdatedDesc(u.getId()).isEmpty()) {
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
            if (complianceRepository.findFirstByClientIdOrderByLastUpdatedDesc(u.getId()).isEmpty()) {
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
        req.setStaff("Unassigned");
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
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findAll().stream()
                    .filter(u -> u.getId() != null && u.getId().equalsIgnoreCase(id))
                    .findFirst();
        }
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        String actualId = userOpt.get().getId();
        userRepository.deleteById(actualId);

        // Delete associated records
        List<Requirement> reqs = requirementRepository.findAll();
        for (Requirement r : reqs) {
            if (r.getUserId() != null && (r.getUserId().equalsIgnoreCase(actualId) || r.getUserId().equalsIgnoreCase(id))) {
                requirementRepository.deleteById(r.getId());
            }
        }
        onboardingRepository.findFirstByClientIdOrderByCreatedAtDesc(actualId).ifPresent(ob -> onboardingRepository.deleteById(ob.getId()));
        onboardingRepository.findFirstByClientIdOrderByCreatedAtDesc(id).ifPresent(ob -> onboardingRepository.deleteById(ob.getId()));
        kycRepository.findFirstByClientIdOrderByLastUpdatedDesc(actualId).ifPresent(k -> kycRepository.deleteById(k.getId()));
        kycRepository.findFirstByClientIdOrderByLastUpdatedDesc(id).ifPresent(k -> kycRepository.deleteById(k.getId()));
        complianceRepository.findFirstByClientIdOrderByLastUpdatedDesc(actualId).ifPresent(c -> complianceRepository.deleteById(c.getId()));
        complianceRepository.findFirstByClientIdOrderByLastUpdatedDesc(id).ifPresent(c -> complianceRepository.deleteById(c.getId()));
        if (clientDocumentRepository != null) {
            clientDocumentRepository.findByClientId(actualId).forEach(d -> clientDocumentRepository.deleteById(d.getId()));
            if (!actualId.equalsIgnoreCase(id)) {
                clientDocumentRepository.findByClientId(id).forEach(d -> clientDocumentRepository.deleteById(d.getId()));
            }
        }
        if (messageRepository != null) {
            messageRepository.findByClientId(actualId).forEach(m -> messageRepository.deleteById(m.getId()));
            if (!actualId.equalsIgnoreCase(id)) {
                messageRepository.findByClientId(id).forEach(m -> messageRepository.deleteById(m.getId()));
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Client deleted successfully."));
    }

    @GetMapping("/admin/staff-list")
    public ResponseEntity<?> getStaffList() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> staffList = users.stream()
                .filter(u -> "STAFF".equalsIgnoreCase(u.getRole()))
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
                    if (name.isEmpty()) name = u.getEmail() != null ? u.getEmail() : "Staff Specialist";
                    map.put("id", u.getId());
                    map.put("name", name);
                    map.put("email", u.getEmail() != null ? u.getEmail() : "");
                    map.put("department", u.getDepartment() != null ? u.getDepartment() : "Corporate Services");
                    map.put("designation", u.getDesignation() != null ? u.getDesignation() : "Staff Specialist");
                    map.put("onlineStatus", u.getOnlineStatus() != null ? u.getOnlineStatus() : "OFFLINE");
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(staffList);
    }

    @PutMapping("/admin/clients/{id}/assign-staff")
    public ResponseEntity<?> assignStaffToClient(@PathVariable String id, @RequestBody Map<String, Object> req) {
        Optional<User> uOpt = userRepository.findById(id);
        if (uOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User u = uOpt.get();

        Object staffIdsObj = req.get("staffIds");
        Object staffNamesObj = req.get("staffNames");
        Object staffEmailsObj = req.get("staffEmails");

        String staffId = null;
        String staffName = null;
        String staffEmail = null;

        if (staffIdsObj instanceof List) {
            List<?> list = (List<?>) staffIdsObj;
            staffId = list.stream().map(Object::toString).map(String::trim).filter(s -> !s.isEmpty() && !"unassign".equalsIgnoreCase(s)).collect(Collectors.joining(", "));
        } else if (req.get("staffId") != null) {
            staffId = (String) req.get("staffId");
        }

        if (staffNamesObj instanceof List) {
            List<?> list = (List<?>) staffNamesObj;
            staffName = list.stream().map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
        } else if (req.get("staffName") != null) {
            staffName = (String) req.get("staffName");
        }

        if (staffEmailsObj instanceof List) {
            List<?> list = (List<?>) staffEmailsObj;
            staffEmail = list.stream().map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
        } else if (req.get("staffEmail") != null) {
            staffEmail = (String) req.get("staffEmail");
        }

        String assignedBy = (String) req.getOrDefault("assignedBy", "Admin");

        if (staffId == null || staffId.trim().isEmpty() || "unassign".equalsIgnoreCase(staffId)) {
            u.setAssignedStaffId(null);
            u.setAssignedStaffName(null);
            u.setAssignedStaffEmail(null);
            u.setAssignedAt(null);
            u.setAssignedBy(null);
        } else {
            u.setAssignedStaffId(staffId);
            u.setAssignedStaffName(staffName != null && !staffName.trim().isEmpty() ? staffName : "Staff Specialist");
            u.setAssignedStaffEmail(staffEmail != null ? staffEmail : "");
            u.setAssignedAt(System.currentTimeMillis());
            u.setAssignedBy(assignedBy);
        }
        userRepository.save(u);

        // Also update associated requirements
        List<Requirement> reqs = requirementRepository.findAll();
        for (Requirement r : reqs) {
            if (r.getUserId() != null && r.getUserId().equalsIgnoreCase(id)) {
                r.setStaff(u.getAssignedStaffName() != null ? u.getAssignedStaffName() : "Unassigned");
                requirementRepository.save(r);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", u.getAssignedStaffId() != null ? ("Client assigned to " + u.getAssignedStaffName()) : "Client unassigned successfully.",
                "client", u
        ));
    }

    @PostMapping("/admin/clients/batch-assign-staff")
    public ResponseEntity<?> batchAssignStaff(@RequestBody Map<String, Object> req) {
        Object clientIdsObj = req.get("clientIds");
        String staffId = (String) req.get("staffId");
        String staffName = (String) req.get("staffName");
        String staffEmail = (String) req.get("staffEmail");
        String assignedBy = (String) req.getOrDefault("assignedBy", "Admin");

        if (!(clientIdsObj instanceof List)) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientIds array is required"));
        }
        List<String> clientIds = (List<String>) clientIdsObj;
        if (clientIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientIds array cannot be empty"));
        }

        int updatedCount = 0;
        for (String cid : clientIds) {
            Optional<User> uOpt = userRepository.findById(cid);
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                if (staffId == null || staffId.trim().isEmpty() || "unassign".equalsIgnoreCase(staffId)) {
                    u.setAssignedStaffId(null);
                    u.setAssignedStaffName(null);
                    u.setAssignedStaffEmail(null);
                    u.setAssignedAt(null);
                    u.setAssignedBy(null);
                } else {
                    u.setAssignedStaffId(staffId);
                    u.setAssignedStaffName(staffName != null ? staffName : "Staff Specialist");
                    u.setAssignedStaffEmail(staffEmail != null ? staffEmail : "");
                    u.setAssignedAt(System.currentTimeMillis());
                    u.setAssignedBy(assignedBy);
                }
                userRepository.save(u);
                updatedCount++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "updatedCount", updatedCount,
                "message", "Successfully updated " + updatedCount + " clients"
        ));
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


