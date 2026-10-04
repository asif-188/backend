package com.globalisor.backend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private List<ClientInfo> clients;
    private Map<String, Object> stats;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public DashboardResponse(List<ClientInfo> clients, Map<String, Object> stats) {
        this.clients = clients;
        this.stats = stats;
        this.page = 1;
        this.size = clients != null ? clients.size() : 0;
        this.totalElements = clients != null ? clients.size() : 0;
        this.totalPages = 1;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientInfo {
        private String clientId;
        private String name;
        private String email;
        private int serviceCount;
        private String latestActivity;
        private String latestStatus;
        private List<String> companyNames;
        private String phone;
        private long createdAt;
        private int pendingCount;
        private int approvedCount;
        private int rejectedCount;
        private List<String> serviceTypes;
        private String latestStaff;
        private boolean isOnline;
        private Long lastSeenTime;
        private List<String> nomineeDirectors;
        private String assignedStaffId;
        private String assignedStaffName;
        private String assignedStaffEmail;
        private Long assignedAt;
        private String assignedBy;
    }
}
