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
        return syncApplication(applicationId, null);
    }

    public Map<String, Object> syncApplication(String applicationId, Map<String, Object> overrides) {
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

            // 1. Resolve company/client name (Corporate Entity Name)
            String name = null;
            if (overrides != null && overrides.get("clientName") != null && !overrides.get("clientName").toString().trim().isEmpty()) {
                name = overrides.get("clientName").toString().trim();
            } else if (overrides != null && overrides.get("name") != null && !overrides.get("name").toString().trim().isEmpty()) {
                name = overrides.get("name").toString().trim();
            } else if (overrides != null && overrides.get("companyName") != null && !overrides.get("companyName").toString().trim().isEmpty()) {
                name = overrides.get("companyName").toString().trim();
            } else if (data.containsKey("companyName") && data.get("companyName") != null && !data.get("companyName").toString().trim().isEmpty()) {
                name = data.get("companyName").toString().trim();
            } else if (data.containsKey("names") && data.get("names") != null) {
                Object namesObj = data.get("names");
                if (namesObj instanceof List && !((List<?>) namesObj).isEmpty()) {
                    Object first = ((List<?>) namesObj).get(0);
                    if (first != null && !first.toString().trim().isEmpty()) {
                        name = first.toString().trim();
                    }
                } else if (!namesObj.toString().trim().isEmpty()) {
                    name = namesObj.toString().trim();
                }
            }

            if (name == null || name.isEmpty()) {
                if (data.containsKey("proposedName1") && data.get("proposedName1") != null && !data.get("proposedName1").toString().trim().isEmpty()) {
                    name = data.get("proposedName1").toString().trim();
                } else if (data.containsKey("proposedName") && data.get("proposedName") != null && !data.get("proposedName").toString().trim().isEmpty()) {
                    name = data.get("proposedName").toString().trim();
                } else if (data.containsKey("business") && data.get("business") != null && !data.get("business").toString().trim().isEmpty()) {
                    name = data.get("business").toString().trim();
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
            }

            // 2. Resolve Contact Person (Individual Representative / Director / Key Officer)
            String contactPerson = null;
            if (overrides != null && overrides.get("contactPerson") != null && !overrides.get("contactPerson").toString().trim().isEmpty()) {
                contactPerson = overrides.get("contactPerson").toString().trim();
            } else if (data.containsKey("contactPerson") && data.get("contactPerson") != null && !data.get("contactPerson").toString().trim().isEmpty()) {
                contactPerson = data.get("contactPerson").toString().trim();
            } else if (data.containsKey("contact") && data.get("contact") instanceof Map) {
                Map<?, ?> contactMap = (Map<?, ?>) data.get("contact");
                String fn = contactMap.get("firstName") != null ? contactMap.get("firstName").toString().trim() : "";
                String ln = contactMap.get("lastName") != null ? contactMap.get("lastName").toString().trim() : "";
                String full = (fn + " " + ln).trim();
                if (!full.isEmpty()) contactPerson = full;
            }

            if (contactPerson == null || contactPerson.isEmpty()) {
                if (data.containsKey("applicantName") && data.get("applicantName") != null && !data.get("applicantName").toString().trim().isEmpty()) {
                    contactPerson = data.get("applicantName").toString().trim();
                } else if (data.containsKey("fullName") && data.get("fullName") != null && !data.get("fullName").toString().trim().isEmpty()) {
                    contactPerson = data.get("fullName").toString().trim();
                } else if (data.containsKey("directors") && data.get("directors") instanceof List && !((List<?>) data.get("directors")).isEmpty()) {
                    Object firstDir = ((List<?>) data.get("directors")).get(0);
                    if (firstDir instanceof Map) {
                        Map<?, ?> dirMap = (Map<?, ?>) firstDir;
                        if (dirMap.get("name") != null && !dirMap.get("name").toString().trim().isEmpty()) {
                            contactPerson = dirMap.get("name").toString().trim();
                        } else if (dirMap.get("fullName") != null && !dirMap.get("fullName").toString().trim().isEmpty()) {
                            contactPerson = dirMap.get("fullName").toString().trim();
                        }
                    }
                } else if (linkedUser != null) {
                    String fullName = ((linkedUser.getFirstName() != null ? linkedUser.getFirstName() : "") + " " + 
                                       (linkedUser.getLastName() != null ? linkedUser.getLastName() : "")).trim();
                    if (!fullName.isEmpty()) contactPerson = fullName;
                }
            }
            if (contactPerson == null || contactPerson.isEmpty()) {
                contactPerson = name;
            }

            // 3. Resolve Email
            String email = null;
            if (overrides != null && overrides.get("email") != null && !overrides.get("email").toString().trim().isEmpty()) {
                email = overrides.get("email").toString().trim();
            } else if (data.containsKey("contact") && data.get("contact") instanceof Map) {
                Map<?, ?> contactMap = (Map<?, ?>) data.get("contact");
                if (contactMap.get("email") != null && !contactMap.get("email").toString().trim().isEmpty()) {
                    email = contactMap.get("email").toString().trim();
                }
            }
            if (email == null || email.isEmpty()) {
                if (data.containsKey("email") && data.get("email") != null && !data.get("email").toString().trim().isEmpty()) {
                    email = data.get("email").toString().trim();
                } else if (data.containsKey("applicantEmail") && data.get("applicantEmail") != null && !data.get("applicantEmail").toString().trim().isEmpty()) {
                    email = data.get("applicantEmail").toString().trim();
                } else if (linkedUser != null && linkedUser.getEmail() != null) {
                    email = linkedUser.getEmail();
                }
            }
            if (email == null) email = "";

            // 4. Resolve Phone
            String phone = null;
            if (overrides != null && overrides.get("phone") != null && !overrides.get("phone").toString().trim().isEmpty()) {
                phone = overrides.get("phone").toString().trim();
            } else if (data.containsKey("contact") && data.get("contact") instanceof Map) {
                Map<?, ?> contactMap = (Map<?, ?>) data.get("contact");
                if (contactMap.get("phone") != null && !contactMap.get("phone").toString().trim().isEmpty()) {
                    String cc = contactMap.get("countryCode") != null ? contactMap.get("countryCode").toString().trim() : "";
                    phone = (cc + " " + contactMap.get("phone").toString().trim()).trim();
                }
            }
            if (phone == null || phone.isEmpty()) {
                if (data.containsKey("phone") && data.get("phone") != null && !data.get("phone").toString().trim().isEmpty()) {
                    phone = data.get("phone").toString().trim();
                } else if (data.containsKey("applicantPhone") && data.get("applicantPhone") != null && !data.get("applicantPhone").toString().trim().isEmpty()) {
                    phone = data.get("applicantPhone").toString().trim();
                } else if (linkedUser != null && linkedUser.getPhone() != null) {
                    phone = linkedUser.getPhone();
                }
            }
            if (phone == null) phone = "";

            // 5. Resolve Country
            String country = "Singapore";
            if (overrides != null && overrides.get("country") != null && !overrides.get("country").toString().trim().isEmpty()) {
                country = overrides.get("country").toString().trim();
            } else if (data.containsKey("country") && data.get("country") != null && !data.get("country").toString().trim().isEmpty()) {
                country = data.get("country").toString().trim();
            } else if (data.containsKey("incorporationCountry") && data.get("incorporationCountry") != null && !data.get("incorporationCountry").toString().trim().isEmpty()) {
                country = data.get("incorporationCountry").toString().trim();
            } else if (data.containsKey("nationality") && data.get("nationality") != null && !data.get("nationality").toString().trim().isEmpty()) {
                country = data.get("nationality").toString().trim();
            }

            // 6. Resolve Registered Address
            String registeredAddress = "";
            if (overrides != null && overrides.get("registeredAddress") != null && !overrides.get("registeredAddress").toString().trim().isEmpty()) {
                registeredAddress = overrides.get("registeredAddress").toString().trim();
            } else if (data.containsKey("registeredAddress") && data.get("registeredAddress") != null && !data.get("registeredAddress").toString().trim().isEmpty()) {
                registeredAddress = data.get("registeredAddress").toString().trim();
            } else if (data.containsKey("address") && data.get("address") != null) {
                Object addrObj = data.get("address");
                if (addrObj instanceof Map) {
                    Map<?, ?> addrMap = (Map<?, ?>) addrObj;
                    registeredAddress = ((addrMap.get("street") != null ? addrMap.get("street") : "") + " " +
                                         (addrMap.get("postalCode") != null ? addrMap.get("postalCode") : "")).trim();
                } else {
                    registeredAddress = addrObj.toString().trim();
                }
            }

            // 7. Resolve Nature of Business / Industry
            String natureOfBusiness = "";
            if (overrides != null && overrides.get("natureOfBusiness") != null && !overrides.get("natureOfBusiness").toString().trim().isEmpty()) {
                natureOfBusiness = overrides.get("natureOfBusiness").toString().trim();
            } else if (data.containsKey("customPrimaryActivity") && data.get("customPrimaryActivity") != null && !data.get("customPrimaryActivity").toString().trim().isEmpty()) {
                natureOfBusiness = data.get("customPrimaryActivity").toString().trim();
            } else if (data.containsKey("primarySsic") && data.get("primarySsic") instanceof Map) {
                Map<?, ?> ssicMap = (Map<?, ?>) data.get("primarySsic");
                natureOfBusiness = (ssicMap.get("name") != null ? ssicMap.get("name") : ssicMap.get("description") != null ? ssicMap.get("description") : "").toString().trim();
            } else if (data.containsKey("natureOfBusiness") && data.get("natureOfBusiness") != null && !data.get("natureOfBusiness").toString().trim().isEmpty()) {
                natureOfBusiness = data.get("natureOfBusiness").toString().trim();
            }

            String industry = "";
            if (overrides != null && overrides.get("industry") != null && !overrides.get("industry").toString().trim().isEmpty()) {
                industry = overrides.get("industry").toString().trim();
            } else if (data.containsKey("industry") && data.get("industry") != null && !data.get("industry").toString().trim().isEmpty()) {
                industry = data.get("industry").toString().trim();
            } else {
                industry = natureOfBusiness;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("contactPerson", contactPerson);
            payload.put("email", email);
            payload.put("phone", phone);
            payload.put("country", country);
            payload.put("registeredAddress", registeredAddress);
            payload.put("natureOfBusiness", natureOfBusiness);
            payload.put("industry", industry);

            // Execute sync runner
            Map<String, Object> syncResult = executeSyncRunner(payload);

            if (Boolean.TRUE.equals(syncResult.get("success"))) {
                String syncedTimestamp = new Date().toString();
                // Update requirement top-level and data map with sync status
                req.setComplianceBoxSynced(true);
                req.setComplianceBoxSyncedAt(syncedTimestamp);
                req.setComplianceBoxClientName(name);
                req.setComplianceBoxContactPerson(contactPerson);

                data.put("complianceBoxSynced", true);
                data.put("complianceBoxSyncedAt", syncedTimestamp);
                data.put("complianceBoxClientName", name);
                data.put("complianceBoxContactPerson", contactPerson);
                req.setData(data);
                req.setUpdatedAt(new Date());
                requirementRepository.save(req);

                response.put("success", true);
                response.put("complianceBoxSynced", true);
                response.put("message", "Client successfully synced to ComplianceBox");
                response.put("clientName", name);
                response.put("contactPerson", contactPerson);
                response.put("email", email);
                response.put("phone", phone);
                response.put("country", country);
                response.put("syncedAt", syncedTimestamp);
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
            String registeredAddress = payload.get("registeredAddress") != null ? payload.get("registeredAddress").toString().trim() : "";
            String natureOfBusiness = payload.get("natureOfBusiness") != null ? payload.get("natureOfBusiness").toString().trim() : "";
            String industry = payload.get("industry") != null ? payload.get("industry").toString().trim() : "";

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
            if (!country.isEmpty()) {
                requestBodyMap.put("countryOfIncorporation", country);
            }
            if (!registeredAddress.isEmpty()) {
                requestBodyMap.put("registeredAddress", registeredAddress);
                requestBodyMap.put("address", registeredAddress);
            }
            if (!natureOfBusiness.isEmpty()) {
                requestBodyMap.put("natureOfBusiness", natureOfBusiness);
            }
            if (!industry.isEmpty()) {
                requestBodyMap.put("industry", industry);
            }
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

            if (statusCode == 401) {
                logger.warn("ComplianceBox returned 401 Unauthorized. Attempting to fetch fresh token from active Chrome session...");
                Map<String, Object> refreshRes = refreshBrowserToken();
                if (Boolean.TRUE.equals(refreshRes.get("success")) && refreshRes.get("token") != null) {
                    String newToken = refreshRes.get("token").toString().trim();
                    if (!newToken.isEmpty()) {
                        logger.info("Found fresh token from browser. Retrying ComplianceBox sync...");
                        this.complianceBoxToken = newToken;
                        HttpRequest retryRequest = HttpRequest.newBuilder()
                                .uri(URI.create(complianceBoxApiUrl))
                                .timeout(Duration.ofSeconds(20))
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .header("Origin", "https://app.compliancebox.co")
                                .header("Referer", "https://app.compliancebox.co/")
                                .header("token", newToken)
                                .header("Access-Control-Allow-Origin", "https://app.compliancebox.co|https://api.compliancebox.co")
                                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36")
                                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                                .build();
                        HttpResponse<String> retryResponse = client.send(retryRequest, HttpResponse.BodyHandlers.ofString());
                        statusCode = retryResponse.statusCode();
                        respBody = retryResponse.body() != null ? retryResponse.body().trim() : "";
                        logger.info("Retry ComplianceBox API response status: {}, body: {}", statusCode, respBody);
                    }
                }
            }

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
                if (statusCode == 401) {
                    response.put("error", "ComplianceBox session expired or invalid. Please open a tab, log into https://app.compliancebox.co, and click Sync again.");
                } else {
                    response.put("error", "ComplianceBox API returned HTTP " + statusCode + (respBody.isEmpty() ? "" : ": " + respBody));
                }
                return response;
            }
        } catch (Exception e) {
            logger.error("Failed to sync client to ComplianceBox: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    private String findNodeExecutable() {
        String[] possiblePaths = {
            "/opt/homebrew/bin/node",
            "/usr/local/bin/node",
            "/usr/bin/node",
            "node"
        };
        for (String p : possiblePaths) {
            try {
                File f = new File(p);
                if (f.exists() && f.canExecute()) {
                    return p;
                }
            } catch (Exception ignored) {}
        }
        return "node";
    }

    public Map<String, Object> refreshBrowserToken() {
        Map<String, Object> result = new HashMap<>();
        try {
            File script = new File("scripts/extract_compliancebox_token.js");
            if (!script.exists()) {
                script = new File("../scripts/extract_compliancebox_token.js");
            }
            if (!script.exists()) {
                result.put("success", false);
                result.put("error", "Extractor script not found: scripts/extract_compliancebox_token.js");
                return result;
            }

            ProcessBuilder pb = new ProcessBuilder(findNodeExecutable(), script.getAbsolutePath());
            pb.directory(script.getParentFile());
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String errOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor();

            if (output.startsWith("{")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(output, Map.class);
                if (Boolean.TRUE.equals(parsed.get("success")) && parsed.get("token") != null) {
                    this.complianceBoxToken = parsed.get("token").toString().trim();
                }
                return parsed;
            } else {
                result.put("success", false);
                result.put("error", !errOutput.isEmpty() ? errOutput : output);
                return result;
            }
        } catch (Exception e) {
            logger.error("Error refreshing token from browser: ", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
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
            } else {
                // If token file not present, try quick check from active browser
                Map<String, Object> browserRes = refreshBrowserToken();
                if (Boolean.TRUE.equals(browserRes.get("success")) && browserRes.get("token") != null) {
                    String t = browserRes.get("token").toString().trim();
                    if (!t.isEmpty()) return t;
                }
            }
        } catch (Exception ignored) {}

        if (complianceBoxToken != null && !complianceBoxToken.trim().isEmpty()) {
            return complianceBoxToken.trim();
        }
        return "1efb62b2-4f40-4f7b-b08b-4fc641932eda";
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

    public String resolveApplicationDisplayId(Requirement req) {
        if (req == null) return "APP-0001";
        if (req.getApplicationReferenceNo() != null && !req.getApplicationReferenceNo().trim().isEmpty() 
                && req.getApplicationReferenceNo().trim().startsWith("APP-")) {
            return req.getApplicationReferenceNo().trim();
        }
        if (req.getData() != null) {
            Object ref = req.getData().get("applicationReferenceNo");
            if (ref != null && !ref.toString().trim().isEmpty() && ref.toString().trim().startsWith("APP-")) {
                return ref.toString().trim();
            }
            Object num = req.getData().get("applicationNumber");
            if (num != null && !num.toString().trim().isEmpty()) {
                String strNum = num.toString().trim();
                return strNum.startsWith("APP-") ? strNum : ("APP-" + strNum);
            }
        }
        String rawId = req.getId() != null ? req.getId().trim() : "";
        if (rawId.startsWith("APP-")) {
            return rawId;
        }
        if (rawId.startsWith("SRV-")) {
            return rawId.replace("SRV-", "APP-");
        }
        if (rawId.matches("^[0-9a-fA-F]{24}$")) {
            String last4 = rawId.substring(rawId.length() - 4);
            if (last4.matches("^\\d{4}$")) {
                return "APP-" + last4;
            }
            try {
                long hexVal = Long.parseLong(last4, 16);
                long appNum = (hexVal % 9000) + 1000;
                return String.format("APP-%04d", appNum);
            } catch (Exception e) {
                return "APP-" + last4.toUpperCase();
            }
        }
        if (rawId.startsWith("REQ-")) {
            return rawId.replace("REQ-", "APP-");
        }
        return rawId.isEmpty() ? ("APP-" + (System.currentTimeMillis() % 10000)) : ("APP-" + rawId);
    }

    private Requirement findRequirementFlexible(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        Optional<Requirement> r = requirementRepository.findById(trimmed);
        if (r.isPresent()) return r.get();
        
        String cleanId = trimmed.replace("APP-", "").trim();
        r = requirementRepository.findById(cleanId);
        if (r.isPresent()) return r.get();

        List<Requirement> all = requirementRepository.findAllLightweight();
        if (all == null || all.isEmpty()) {
            all = requirementRepository.findAll();
        }
        for (Requirement req : all) {
            if (req.getId() != null && (req.getId().equalsIgnoreCase(trimmed) || req.getId().equalsIgnoreCase(cleanId))) {
                return requirementRepository.findById(req.getId()).orElse(req);
            }
            if (req.getApplicationReferenceNo() != null && (req.getApplicationReferenceNo().equalsIgnoreCase(trimmed) || req.getApplicationReferenceNo().equalsIgnoreCase(cleanId))) {
                return requirementRepository.findById(req.getId()).orElse(req);
            }
            if (req.getData() != null) {
                Object appRef = req.getData().get("applicationReferenceNo");
                if (appRef != null && (appRef.toString().equalsIgnoreCase(trimmed) || appRef.toString().equalsIgnoreCase(cleanId))) {
                    return requirementRepository.findById(req.getId()).orElse(req);
                }
                Object appNum = req.getData().get("applicationNumber");
                if (appNum != null && (appNum.toString().equalsIgnoreCase(trimmed) || appNum.toString().equalsIgnoreCase(cleanId) || ("APP-" + appNum.toString()).equalsIgnoreCase(trimmed))) {
                    return requirementRepository.findById(req.getId()).orElse(req);
                }
            }
            // Check dynamic display ID (e.g. APP-4458, APP-4461)
            String dispId = resolveApplicationDisplayId(req);
            if (dispId != null && (dispId.equalsIgnoreCase(trimmed) || dispId.replace("APP-", "").equalsIgnoreCase(cleanId))) {
                return requirementRepository.findById(req.getId()).orElse(req);
            }
        }
        return null;
    }
}
