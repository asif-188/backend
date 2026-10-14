package com.globalisor.backend.payload.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
public class DashboardResponse {
    private List<ClientInfo> clients;
    private Map<String, Object> stats;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public DashboardResponse(List<ClientInfo> clients, Map<String, Object> stats, int page, int size, long totalElements, int totalPages) {
        this.clients = clients;
        this.stats = stats;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public DashboardResponse(List<ClientInfo> clients, Map<String, Object> stats) {
        this(clients, stats, 1, clients != null ? clients.size() : 0, clients != null ? clients.size() : 0, 1);
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class ClientInfo {
        private String clientId;
        private String name;
        private String email;
        private List<String> companyNames;
        private List<String> nomineeDirectors;
        private List<String> serviceTypes;
        private int serviceCount;
        private int pendingCount;
        private int approvedCount;
        private int rejectedCount;
        private String latestStatus;
        private String latestActivity;
        private String assignedStaffId;
        private String assignedStaffName;
        private boolean isOnline;
        private Long lastSeenTime;

        public ClientInfo(String clientId, String name, String email, List<String> companyNames,
                          List<String> nomineeDirectors, List<String> serviceTypes, int serviceCount,
                          int pendingCount, int approvedCount, int rejectedCount, String latestStatus,
                          String latestActivity, String assignedStaffId, String assignedStaffName,
                          boolean isOnline, Long lastSeenTime) {
            this.clientId = clientId;
            this.name = name;
            this.email = email;
            this.companyNames = companyNames;
            this.nomineeDirectors = nomineeDirectors;
            this.serviceTypes = serviceTypes;
            this.serviceCount = serviceCount;
            this.pendingCount = pendingCount;
            this.approvedCount = approvedCount;
            this.rejectedCount = rejectedCount;
            this.latestStatus = latestStatus;
            this.latestActivity = latestActivity;
            this.assignedStaffId = assignedStaffId;
            this.assignedStaffName = assignedStaffName;
            this.isOnline = isOnline;
            this.lastSeenTime = lastSeenTime;
        }
    }
}
