package com.globalisor.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

@Service
public class ComplianceBoxService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceBoxService.class);

    @Value("${compliancebox.api.url:https://api.compliancebox.co/api/clients}")
    private String complianceBoxApiUrl;

    @Value("${compliancebox.api.token:4d3693b0-201c-4204-9729-e7d779a613b6}")
    private String complianceBoxToken;

    @Value("${compliancebox.api.company-id:629dbca7374a1ac76b559ee7}")
    private String complianceBoxCompanyId;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> syncApplication(String applicationId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Requirement req = findRequirementFlexible(applicationId);
            if (req == null) {
                response.put("success", false);
                response.put("error", "Application not found: " + applicationId);
                return response;
            }

            Map<String, Object> data = req.getData() != null ? req.getData() : new HashMap<>();
            
            // Check linked user for fallback details
            User linkedUser = null;
            if (req.getUserId() != null) {
                linkedUser = userRepository.findById(req.getUserId()).orElse(null);
            }

            // Resolve company/client name
            String name = null;
            if (data.containsKey("companyName") && data.get("companyName") != null && !data.get("companyName").toString().trim().isEmpty()) {
                name = data.get("companyName").toString().trim();
            } else if (data.containsKey("proposedName1") && data.get("proposedName1") != null && !data.get("proposedName1").toString().trim().isEmpty()) {
                name = data.get("proposedName1").toString().trim();
            } else if (data.containsKey("proposedName") && data.get("proposedName") != null && !data.get("proposedName").toString().trim().isEmpty()) {
                name = data.get("proposedName").toString().trim();
            } else if (data.containsKey("clientName") && data.get("clientName") != null && !data.get("clientName").toString().trim().isEmpty()) {
                name = data.get("clientName").toString().trim();
            } else if (linkedUser != null && linkedUser.getCompanyName() != null && !linkedUser.getCompanyName().trim().isEmpty()) {
                name = linkedUser.getCompanyName().trim();
            } else if (linkedUser != null) {
                String fullName = ((linkedUser.getFirstName() != null ? linkedUser.getFirstName() : "") + " " + 
                                   (linkedUser.getLastName() != null ? linkedUser.getLastName() : "")).trim();
                name = !fullName.isEmpty() ? fullName : "Client " + req.getId();
            } else {
                name = "Client Application " + req.getId();
            }

            // Resolve contact person
            String contactPerson = null;
            if (data.containsKey("contactPerson") && data.get("contactPerson") != null && !data.get("contactPerson").toString().trim().isEmpty()) {
                contactPerson = data.get("contactPerson").toString().trim();
            } else if (data.containsKey("applicantName") && data.get("applicantName") != null && !data.get("applicantName").toString().trim().isEmpty()) {
                contactPerson = data.get("applicantName").toString().trim();
            } else if (data.containsKey("fullName") && data.get("fullName") != null && !data.get("fullName").toString().trim().isEmpty()) {
                contactPerson = data.get("fullName").toString().trim();
            } else if (linkedUser != null) {
                String fullName = ((linkedUser.getFirstName() != null ? linkedUser.getFirstName() : "") + " " + 
                                   (linkedUser.getLastName() != null ? linkedUser.getLastName() : "")).trim();
                if (!fullName.isEmpty()) contactPerson = fullName;
            }

            // Resolve email
            String email = null;
            if (data.containsKey("email") && data.get("email") != null && !data.get("email").toString().trim().isEmpty()) {
                email = data.get("email").toString().trim();
            } else if (data.containsKey("applicantEmail") && data.get("applicantEmail") != null && !data.get("applicantEmail").toString().trim().isEmpty()) {
                email = data.get("applicantEmail").toString().trim();
            } else if (linkedUser != null && linkedUser.getEmail() != null) {
                email = linkedUser.getEmail();
            }

            // Resolve phone
            String phone = null;
            if (data.containsKey("phone") && data.get("phone") != null && !data.get("phone").toString().trim().isEmpty()) {
                phone = data.get("phone").toString().trim();
            } else if (data.containsKey("applicantPhone") && data.get("applicantPhone") != null && !data.get("applicantPhone").toString().trim().isEmpty()) {
                phone = data.get("applicantPhone").toString().trim();
            } else if (linkedUser != null && linkedUser.getPhone() != null) {
                phone = linkedUser.getPhone();
            }

            // Resolve country
            String country = "Singapore";
            if (data.containsKey("country") && data.get("country") != null && !data.get("country").toString().trim().isEmpty()) {
                country = data.get("country").toString().trim();
            } else if (data.containsKey("incorporationCountry") && data.get("incorporationCountry") != null && !data.get("incorporationCountry").toString().trim().isEmpty()) {
                country = data.get("incorporationCountry").toString().trim();
            } else if (data.containsKey("nationality") && data.get("nationality") != null && !data.get("nationality").toString().trim().isEmpty()) {
                country = data.get("nationality").toString().trim();
            }

            if (contactPerson == null || contactPerson.isEmpty()) contactPerson = name;
            if (email == null) email = "";
            if (phone == null) phone = "";

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("contactPerson", contactPerson);
            payload.put("email", email);
            payload.put("phone", phone);
            payload.put("country", country);

            // Execute sync runner
            Map<String, Object> syncResult = executeSyncRunner(payload);

            if (Boolean.TRUE.equals(syncResult.get("success"))) {
                // Update requirement with sync status
                data.put("complianceBoxSynced", true);
                data.put("complianceBoxSyncedAt", new Date().toString());
                data.put("complianceBoxClientName", name);
                data.put("complianceBoxContactPerson", contactPerson);
                req.setData(data);
                req.setUpdatedAt(new Date());
                requirementRepository.save(req);

                response.put("success", true);
                response.put("message", "Client successfully synced to ComplianceBox");
                response.put("clientName", name);
                response.put("contactPerson", contactPerson);
                response.put("email", email);
                response.put("phone", phone);
                response.put("country", country);
                response.put("syncedAt", new Date().toString());
            } else {
                response.put("success", false);
                response.put("error", syncResult.getOrDefault("error", "ComplianceBox sync failed"));
            }

        } catch (Exception e) {
            logger.error("Error syncing application to ComplianceBox: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    public Map<String, Object> syncCustom(Map<String, Object> clientPayload) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (clientPayload == null || !clientPayload.containsKey("name") || clientPayload.get("name") == null) {
                response.put("success", false);
                response.put("error", "Client name is required");
                return response;
            }

            Map<String, Object> syncResult = executeSyncRunner(clientPayload);
            return syncResult;
        } catch (Exception e) {
            logger.error("Error in custom ComplianceBox sync: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    private Map<String, Object> executeSyncRunner(Map<String, Object> payload) {
        return syncViaDirectHttp(payload);
    }

    private Map<String, Object> syncViaDirectHttp(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String clientName = payload.get("name") != null ? payload.get("name").toString().trim() : "";
            String contactPerson = payload.get("contactPerson") != null ? payload.get("contactPerson").toString().trim() : clientName;
            String email = payload.get("email") != null ? payload.get("email").toString().trim() : "";
            String phone = payload.get("phone") != null ? payload.get("phone").toString().trim() : "";
            String country = payload.get("country") != null ? payload.get("country").toString().trim() : "Singapore";

            String token = resolveToken();
            String companyId = resolveCompanyId();
            String clientId = generateMongoId();

            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyMap.put("clientType", 2); // Corporate
            requestBodyMap.put("name", clientName);
            requestBodyMap.put("contactPerson", contactPerson);
            requestBodyMap.put("email", email);
            requestBodyMap.put("phone", phone);
            requestBodyMap.put("country", country);
            requestBodyMap.put("servicesSought", Collections.emptyList());
            requestBodyMap.put("access", Collections.emptyList());
            requestBodyMap.put("companyId", companyId);
            requestBodyMap.put("id", clientId);

            String jsonPayload = objectMapper.writeValueAsString(requestBodyMap);
            logger.info("Syncing client '{}' to ComplianceBox at URL: {}", clientName, complianceBoxApiUrl);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(complianceBoxApiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Origin", "https://app.compliancebox.co")
                    .header("Referer", "https://app.compliancebox.co/")
                    .header("token", token)
                    .header("Access-Control-Allow-Origin", "https://app.compliancebox.co|https://api.compliancebox.co")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = httpResponse.statusCode();
            String respBody = httpResponse.body() != null ? httpResponse.body().trim() : "";
            logger.info("ComplianceBox API response status: {}, body: {}", statusCode, respBody);

            if (statusCode >= 200 && statusCode < 300) {
                response.put("success", true);
                response.put("statusCode", statusCode);
                response.put("clientName", clientName);
                response.put("contactPerson", contactPerson);
                response.put("email", email);
                response.put("phone", phone);
                response.put("country", country);
                response.put("complianceBoxId", clientId);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = objectMapper.readValue(respBody, Map.class);
                    response.put("data", data);
                } catch (Exception ignored) {
                    response.put("data", respBody);
                }
                return response;
            } else {
                logger.error("ComplianceBox API error: status={}, body={}", statusCode, respBody);
                response.put("success", false);
                response.put("statusCode", statusCode);
                response.put("error", "ComplianceBox API returned HTTP " + statusCode + (respBody.isEmpty() ? "" : ": " + respBody));
                return response;
            }
        } catch (Exception e) {
            logger.error("Failed to sync client to ComplianceBox: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    private String resolveToken() {
        String envToken = System.getenv("COMPLIANCEBOX_TOKEN");
        if (envToken != null && !envToken.trim().isEmpty()) return envToken.trim();

        String sysToken = System.getProperty("compliancebox.token");
        if (sysToken != null && !sysToken.trim().isEmpty()) return sysToken.trim();

        try {
            File tokenFile = new File("scripts/compliancebox_token.json");
            if (!tokenFile.exists()) {
                tokenFile = new File("../scripts/compliancebox_token.json");
            }
            if (tokenFile.exists()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(tokenFile, Map.class);
                if (map != null && map.containsKey("token") && map.get("token") != null) {
                    String t = map.get("token").toString().trim();
                    if (!t.isEmpty()) return t;
                }
            }
        } catch (Exception ignored) {}

        if (complianceBoxToken != null && !complianceBoxToken.trim().isEmpty()) {
            return complianceBoxToken.trim();
        }
        return "4d3693b0-201c-4204-9729-e7d779a613b6";
    }

    private String resolveCompanyId() {
        String envComp = System.getenv("COMPLIANCEBOX_COMPANY_ID");
        if (envComp != null && !envComp.trim().isEmpty()) return envComp.trim();

        String sysComp = System.getProperty("compliancebox.company_id");
        if (sysComp != null && !sysComp.trim().isEmpty()) return sysComp.trim();

        if (complianceBoxCompanyId != null && !complianceBoxCompanyId.trim().isEmpty()) {
            return complianceBoxCompanyId.trim();
        }
        return "629dbca7374a1ac76b559ee7";
    }

    private String generateMongoId() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(24);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private Requirement findRequirementFlexible(String id) {
        if (id == null) return null;
        Optional<Requirement> r = requirementRepository.findById(id);
        if (r.isPresent()) return r.get();
        
        String cleanId = id.replace("APP-", "").trim();
        r = requirementRepository.findById(cleanId);
        if (r.isPresent()) return r.get();

        for (Requirement req : requirementRepository.findAll()) {
            if (req.getId() != null && (req.getId().equalsIgnoreCase(id) || req.getId().equalsIgnoreCase(cleanId))) {
                return req;
            }
            if (req.getApplicationReferenceNo() != null && (req.getApplicationReferenceNo().equalsIgnoreCase(id) || req.getApplicationReferenceNo().equalsIgnoreCase(cleanId))) {
                return req;
            }
            if (req.getData() != null) {
                Object appRef = req.getData().get("applicationReferenceNo");
                if (appRef != null && (appRef.toString().equalsIgnoreCase(id) || appRef.toString().equalsIgnoreCase(cleanId))) {
                    return req;
                }
                Object appNum = req.getData().get("applicationNumber");
                if (appNum != null && (appNum.toString().equalsIgnoreCase(id) || appNum.toString().equalsIgnoreCase(cleanId) || ("APP-" + appNum.toString()).equalsIgnoreCase(id))) {
                    return req;
                }
            }
        }
        return null;
    }
}
