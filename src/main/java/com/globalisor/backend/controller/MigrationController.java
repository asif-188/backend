package com.globalisor.backend.controller;

import com.globalisor.backend.model.*;
import com.globalisor.backend.repository.*;
import com.globalisor.backend.security.EncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    MigrationJobRepository migrationJobRepository;

    @Autowired
    MigrationSettingRepository migrationSettingRepository;

    @Autowired
    MigrationTemplateRepository migrationTemplateRepository;

    @Autowired
    MigrationFailedRecordRepository migrationFailedRecordRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    EncryptionUtils encryptionUtils;

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long totalClients = userRepository.countByRoleIgnoreCase("CLIENT");
        if (totalClients == 0) {
            long staffAndAdmin = userRepository.countByRoleIn(List.of("ADMIN", "STAFF", "admin", "staff"));
            totalClients = Math.max(0, userRepository.count() - staffAndAdmin);
        }

        // Calculate imported today with timestamp query
        long startOfDayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long importedToday = migrationJobRepository.findByCreatedAtGreaterThanEqual(startOfDayMillis).stream()
                .mapToLong(MigrationJob::getSuccessCount)
                .sum();

        long failedImports = migrationFailedRecordRepository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClients", totalClients);
        stats.put("importedToday", importedToday);
        stats.put("failedImports", failedImports);
        stats.put("ocrAccuracy", 98.5);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        Optional<MigrationSetting> setting = migrationSettingRepository.findById("default");
        if (setting.isPresent()) {
            return ResponseEntity.ok(setting.get());
        } else {
            MigrationSetting defaultSetting = new MigrationSetting();
            migrationSettingRepository.save(defaultSetting);
            return ResponseEntity.ok(defaultSetting);
        }
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody MigrationSetting setting) {
        setting.setId("default");
        MigrationSetting saved = migrationSettingRepository.save(setting);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/templates")
    public ResponseEntity<?> getTemplates() {
        return ResponseEntity.ok(migrationTemplateRepository.findAll());
    }

    @PostMapping("/templates")
    public ResponseEntity<?> saveTemplate(@RequestBody MigrationTemplate template) {
        if (template.getId() == null) {
            template.setId("TPL-" + System.currentTimeMillis());
        }
        MigrationTemplate saved = migrationTemplateRepository.save(template);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/failed")
    public ResponseEntity<?> getFailedRecords() {
        return ResponseEntity.ok(migrationFailedRecordRepository.findAll());
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {
        List<MigrationJob> jobs = migrationJobRepository.findAll();
        // Sort descending by creation date
        jobs.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return ResponseEntity.ok(jobs);
    }

    @PostMapping("/job/{id}/{action}")
    public ResponseEntity<?> controlJob(@PathVariable String id, @PathVariable String action) {
        Optional<MigrationJob> jobOpt = migrationJobRepository.findById(id);
        if (jobOpt.isPresent()) {
            MigrationJob job = jobOpt.get();
            if (action.equalsIgnoreCase("pause")) {
                job.setStatus("PAUSED");
                job.getLogs().add("Migration job paused by administrator.");
            } else if (action.equalsIgnoreCase("resume")) {
                job.setStatus("IN_PROGRESS");
                job.getLogs().add("Migration job resumed.");
            } else if (action.equalsIgnoreCase("cancel")) {
                job.setStatus("CANCELLED");
                job.getLogs().add("Migration job cancelled by administrator.");
            }
            migrationJobRepository.save(job);
            return ResponseEntity.ok(job);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/job")
    public ResponseEntity<?> runJob(@RequestBody Map<String, Object> payload) {
        String jobName = (String) payload.getOrDefault("name", "Bulk Import Job");
        List<Map<String, Object>> records = (List<Map<String, Object>>) payload.get("records");

        MigrationJob job = new MigrationJob();
        job.setId("JOB-" + System.currentTimeMillis());
        job.setName(jobName);
        job.setTotalRecords(records != null ? records.size() : 0);
        job.setStatus("IN_PROGRESS");
        job.getLogs().add("Initiating bulk migration job: " + jobName);
        job.getLogs().add("Found " + job.getTotalRecords() + " candidates to import.");
        migrationJobRepository.save(job);

        int success = 0;
        int failed = 0;
        int duplicates = 0;

        if (records != null) {
            for (Map<String, Object> rec : records) {
                String clientName = (String) rec.get("clientName");
                String companyName = (String) rec.get("companyName");
                String email = (String) rec.get("email");
                Map<String, Object> data = (Map<String, Object>) rec.get("data");

                if (email == null || !email.contains("@")) {
                    failed++;
                    MigrationFailedRecord failedRec = new MigrationFailedRecord();
                    failedRec.setId("FAL-" + System.currentTimeMillis() + "-" + failed);
                    failedRec.setClientName(clientName != null ? clientName : "Unknown");
                    failedRec.setEmail(email != null ? email : "N/A");
                    failedRec.setErrorMessage("Invalid email format");
                    failedRec.setJobName(jobName);
                    migrationFailedRecordRepository.save(failedRec);
                    job.getLogs().add("Skipped candidate [" + clientName + "]: Invalid email format.");
                    continue;
                }

                String encryptedEmail = encryptionUtils.encryptQueryable(email.trim().toLowerCase());
                if (userRepository.findByEmail(encryptedEmail).isPresent()) {
                    duplicates++;
                    job.getLogs().add("Duplicate check: candidate with email [" + email + "] already exists.");
                    // Check settings strategy
                    Optional<MigrationSetting> setting = migrationSettingRepository.findById("default");
                    String strategy = setting.map(MigrationSetting::getDeduplicationStrategy).orElse("IGNORE_DUPLICATES");
                    
                    if (strategy.equalsIgnoreCase("OVERWRITE") || strategy.equalsIgnoreCase("MERGE")) {
                        User existingUser = userRepository.findByEmail(encryptedEmail).get();
                        Optional<Requirement> reqOpt = requirementRepository.findFirstByUserIdOrderByUpdatedAtDesc(existingUser.getId());
                        if (reqOpt.isPresent()) {
                            Requirement req = reqOpt.get();
                            if (strategy.equalsIgnoreCase("OVERWRITE")) {
                                req.setData(data != null ? data : new HashMap<>());
                            } else { // MERGE
                                Map<String, Object> merged = new HashMap<>(req.getData() != null ? req.getData() : new HashMap<>());
                                if (data != null) merged.putAll(data);
                                req.setData(merged);
                            }
                            req.setUpdatedAt(new Date());
                            requirementRepository.save(req);
                            job.getLogs().add("Updated existing client requirement data for [" + clientName + "] via strategy: " + strategy);
                        }
                    }
                    continue;
                }

                try {
                    // Create User
                    User user = new User();
                    // Generate unique client ID matching UEN or timestamp
                    String uen = (data != null && data.containsKey("uen")) ? (String) data.get("uen") : "";
                    String uenClean = uen.trim().replaceAll("\\s+", "");
                    String userId = !uenClean.isEmpty() ? "C-" + uenClean : "C-" + System.currentTimeMillis();
                    
                    user.setId(userId);
                    user.setEmail(email.trim().toLowerCase());
                    user.setPassword(encoder.encode("password123"));
                    
                    // Split clientName to first/last name
                    String firstName = clientName != null ? clientName : "Client";
                    String lastName = "";
                    if (clientName != null && clientName.contains(" ")) {
                        int spaceIdx = clientName.indexOf(" ");
                        firstName = clientName.substring(0, spaceIdx);
                        lastName = clientName.substring(spaceIdx + 1);
                    }
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    user.setRole("USER");
                    
                    userRepository.save(user);

                    // Create Requirement
                    Requirement requirement = new Requirement();
                    requirement.setId("SRV-" + System.currentTimeMillis() + "-" + (success + 1));
                    requirement.setUserId(userId);
                    requirement.setStatus("approved"); // Migrated clients are approved
                    requirement.setStaff("Sarah Lim");
                    
                    Map<String, Object> requirementData = data != null ? data : new HashMap<>();
                    // Set companyNames array
                    if (companyName != null) {
                        requirementData.put("names", Arrays.asList(companyName));
                    }
                    requirement.setData(requirementData);
                    requirementRepository.save(requirement);

                    success++;
                    job.getLogs().add("Successfully migrated client [" + clientName + "] and company [" + companyName + "].");
                } catch (Exception ex) {
                    failed++;
                    MigrationFailedRecord failedRec = new MigrationFailedRecord();
                    failedRec.setId("FAL-" + System.currentTimeMillis() + "-" + failed);
                    failedRec.setClientName(clientName);
                    failedRec.setEmail(email);
                    failedRec.setErrorMessage("Internal Save Error: " + ex.getMessage());
                    failedRec.setJobName(jobName);
                    migrationFailedRecordRepository.save(failedRec);
                    job.getLogs().add("Failed to save client [" + clientName + "]: " + ex.getMessage());
                }
            }
        }

        job.setProcessedRecords(success + failed + duplicates);
        job.setSuccessCount(success);
        job.setFailedCount(failed);
        job.setDuplicateCount(duplicates);
        job.setStatus("COMPLETED");
        job.getLogs().add("Bulk migration completed. Success: " + success + ", Failed: " + failed + ", Duplicates: " + duplicates + ".");
        migrationJobRepository.save(job);

        return ResponseEntity.ok(job);
    }
}
