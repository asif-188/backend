package com.globalisor.backend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
    }
}
