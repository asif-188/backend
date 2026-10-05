package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.payload.response.DashboardResponse;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

import com.globalisor.backend.websocket.ChatWebSocketHandler;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    com.globalisor.backend.repository.TaskRepository taskRepository;

    @Autowired
    com.globalisor.backend.repository.KycRepository kycRepository;

    @Autowired
    com.globalisor.backend.repository.ComplianceRepository complianceRepository;

    @Autowired
    ChatWebSocketHandler chatWebSocketHandler;

    @GetMapping
    public ResponseEntity<?> getDashboardData(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "0") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "staff", required = false) String staff) {
        List<User> allUsers = userRepository.findAll();
        Set<String> activeStaffNames = allUsers.stream()
                .filter(u -> "STAFF".equalsIgnoreCase(u.getRole()))
                .map(u -> ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim().toLowerCase())
                .collect(Collectors.toSet());

        List<User> users = allUsers.stream()
                .filter(u -> {
                    String role = u.getRole();
                    if (role == null) return true;
                    String trimmedRole = role.trim();
                    return !trimmedRole.equalsIgnoreCase("ADMIN") && !trimmedRole.equalsIgnoreCase("STAFF");
                })
                .collect(Collectors.toList());
        List<Requirement> requirements = requirementRepository.findAll();

        List<DashboardResponse.ClientInfo> clientInfos = users.stream().map(user -> {
            List<Requirement> userReqs = requirements.stream()
                    .filter(r -> r.getUserId().equals(user.getId()))
                    .collect(Collectors.toList());

            String latestStatus = userReqs.isEmpty() ? "pending" : userReqs.get(0).getStatus();
            String latestActivity = userReqs.isEmpty() ? "Joined" : userReqs.get(0).getUpdatedAt().toString();
            
            List<String> companyNames = new ArrayList<>();
            List<String> serviceTypes = new ArrayList<>();
            List<String> nomineeDirectors = new ArrayList<>();
            int pendingCount = 0, approvedCount = 0, rejectedCount = 0;
            String latestStaff = "Unassigned";

            for (Requirement r : userReqs) {
                Map<String, Object> data = r.getData();
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List) {
                        List<String> names = (List<String>) namesObj;
                        if (!names.isEmpty() && names.get(0) != null && !names.get(0).isEmpty()) {
                            companyNames.add(names.get(0));
                        }
                    }
                }
                
                // Parse nominee directors from excelData if present
                if (data != null && data.containsKey("excelData")) {
                    Object excelObj = data.get("excelData");
                    if (excelObj instanceof Map) {
                        Map<String, Object> excel = (Map<String, Object>) excelObj;
                        if (excel.containsKey("directors")) {
                            Object dirObj = excel.get("directors");
                            if (dirObj instanceof List) {
                                List<?> dirList = (List<?>) dirObj;
                                for (Object dObj : dirList) {
                                    if (dObj instanceof Map) {
                                        Map<String, Object> dir = (Map<String, Object>) dObj;
                                        Object typeObj = dir.get("type");
                                        Object nameObj = dir.get("name");
                                        if (typeObj != null && "Nominee Director".equalsIgnoreCase(typeObj.toString().trim()) && nameObj != null) {
                                            nomineeDirectors.add(nameObj.toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Service type
                Object sType = (data != null) ? data.get("serviceType") : null;
                serviceTypes.add(sType != null ? sType.toString() : "Company Incorporation");
                // Status counts
                String s = r.getStatus() != null ? r.getStatus().toLowerCase() : "pending";
                if (s.equals("approved") || s.equals("verified") || s.equals("completed")) approvedCount++;
                else if (s.equals("rejected")) rejectedCount++;
                else pendingCount++;
                // Staff
                if (r.getStaff() != null && !r.getStaff().isEmpty() && !r.getStaff().equals("Unassigned")) {
                    latestStaff = r.getStaff();
                }
            }

            boolean isOnline = chatWebSocketHandler.isUserOnline(user.getId());
            Long lastSeen = user.getLastSeenTime();

            String assignedStaffName = "Unassigned";
            if (user.getAssignedStaffName() != null && !user.getAssignedStaffName().trim().isEmpty() && !"Unassigned".equalsIgnoreCase(user.getAssignedStaffName())) {
                List<String> validNames = Arrays.stream(user.getAssignedStaffName().split(","))
                        .map(String::trim)
                        .filter(n -> !n.isEmpty() && !n.equalsIgnoreCase("Unassigned") && (activeStaffNames.isEmpty() || activeStaffNames.contains(n.toLowerCase())))
                        .collect(Collectors.toList());
                if (!validNames.isEmpty()) {
                    assignedStaffName = String.join(", ", validNames);
                }
            } else if (latestStaff != null && !latestStaff.trim().isEmpty() && !"Unassigned".equalsIgnoreCase(latestStaff) && !"Sarah Lim".equalsIgnoreCase(latestStaff)) {
                if (activeStaffNames.isEmpty() || activeStaffNames.contains(latestStaff.toLowerCase())) {
                    assignedStaffName = latestStaff;
                }
            }

            String assignedStaffId = user.getAssignedStaffId() != null ? user.getAssignedStaffId() : "";

            return new DashboardResponse.ClientInfo(
                    user.getId(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getEmail(),
                    companyNames,
                    nomineeDirectors,
                    serviceTypes,
                    userReqs.size(),
                    pendingCount,
                    approvedCount,
                    rejectedCount,
                    latestStatus,
                    latestActivity,
                    assignedStaffId,
                    assignedStaffName,
                    isOnline,
                    lastSeen
            );
        }).collect(Collectors.toList());

        long activeClientsCount = users.stream().filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()) || u.getStatus() == null).count();
        long totalTasksCount = taskRepository != null ? taskRepository.count() : 0L;
        long activeTasksCount = taskRepository != null ? taskRepository.countByStatusIn(Arrays.asList("PENDING", "ASSIGNED", "IN_PROGRESS", "WAITING_CLIENT_INPUT", "UNDER_REVIEW")) : 0L;
        long completedTasksCount = taskRepository != null ? taskRepository.countByStatusIn(Arrays.asList("COMPLETED", "RESOLVED")) : 0L;
        long pendingKycCount = kycRepository != null ? kycRepository.count() : 0L;
        long totalCompliancesCount = complianceRepository != null ? complianceRepository.count() : 0L;
        long incorporatedCount = requirements.stream().filter(r -> "approved".equalsIgnoreCase(r.getStatus()) || "completed".equalsIgnoreCase(r.getStatus())).count();
        long pendingOnboardingCount = requirements.stream().filter(r -> "pending".equalsIgnoreCase(r.getStatus()) || "review".equalsIgnoreCase(r.getStatus()) || "under review".equalsIgnoreCase(r.getStatus())).count();
        long rejectedCount = requirements.stream().filter(r -> "rejected".equalsIgnoreCase(r.getStatus())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClients", (long) users.size());
        stats.put("activeClients", activeClientsCount);
        stats.put("totalServices", (long) requirements.size());
        stats.put("pending", pendingOnboardingCount);
        stats.put("approved", incorporatedCount);
        stats.put("rejected", rejectedCount);
        stats.put("pendingOnboarding", pendingOnboardingCount);
        stats.put("pendingVerification", pendingKycCount);
        stats.put("activeTasks", activeTasksCount);
        stats.put("completedTasks", completedTasksCount);
        stats.put("totalTasks", totalTasksCount);
        stats.put("companiesIncorporated", incorporatedCount);
        stats.put("annualReturns", totalCompliancesCount);
        stats.put("slaCompliance", 98L);

        // 1. Filter by search
        if (search != null && !search.trim().isEmpty()) {
            String s = search.trim().toLowerCase();
            clientInfos = clientInfos.stream().filter(c ->
                (c.getName() != null && c.getName().toLowerCase().contains(s)) ||
                (c.getEmail() != null && c.getEmail().toLowerCase().contains(s)) ||
                (c.getCompanyNames() != null && c.getCompanyNames().stream().anyMatch(cn -> cn != null && cn.toLowerCase().contains(s))) ||
                (c.getNomineeDirectors() != null && c.getNomineeDirectors().stream().anyMatch(nd -> nd != null && nd.toLowerCase().contains(s)))
            ).collect(Collectors.toList());
        }

        // 2. Filter by status
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim())) {
            String st = status.trim().toLowerCase();
            clientInfos = clientInfos.stream().filter(c -> {
                String ls = c.getLatestStatus() != null ? c.getLatestStatus().toLowerCase() : "";
                if ("review".equals(st)) return "review".equals(ls) || "under review".equals(ls);
                if ("approved".equals(st)) return "approved".equals(ls) || "verified".equals(ls) || "completed".equals(ls);
                return ls.equals(st);
            }).collect(Collectors.toList());
        }

        // 3. Filter by staff
        if (staff != null && !staff.trim().isEmpty() && !"ALL".equalsIgnoreCase(staff.trim())) {
            String stf = staff.trim().toLowerCase();
            clientInfos = clientInfos.stream().filter(c -> {
                String as = c.getAssignedStaffName() != null ? c.getAssignedStaffName().toLowerCase() : "";
                if ("unassigned".equals(stf)) return as.isEmpty() || "unassigned".equals(as);
                return as.contains(stf);
            }).collect(Collectors.toList());
        }

        long totalElements = clientInfos.size();
        if (size > 0) {
            int safePage = Math.max(1, page);
            int totalPages = (int) Math.ceil((double) totalElements / size);
            if (totalPages == 0) totalPages = 1;
            int fromIndex = Math.min((safePage - 1) * size, (int) totalElements);
            int toIndex = Math.min(fromIndex + size, (int) totalElements);
            List<DashboardResponse.ClientInfo> pagedList = (fromIndex <= toIndex) ? clientInfos.subList(fromIndex, toIndex) : Collections.emptyList();
            return ResponseEntity.ok(new DashboardResponse(pagedList, stats, safePage, size, totalElements, totalPages));
        }

        DashboardResponse response = new DashboardResponse(clientInfos, stats);
        response.setTotalElements(totalElements);
        response.setTotalPages(1);
        response.setPage(1);
        response.setSize((int) totalElements);
        return ResponseEntity.ok(response);
    }
}
