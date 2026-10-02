package com.globalisor.backend.controller;

import com.globalisor.backend.model.PreRegSection;
import com.globalisor.backend.repository.PreRegSectionRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/prereg-sections")
public class PreRegSectionController {

    @Autowired
    PreRegSectionRepository preRegSectionRepository;

    private void seedDefaultSections() {
        // Special case: if names exists but uses old activities config, update it.
        Optional<PreRegSection> namesOpt = preRegSectionRepository.findById("sec-names");
        if (namesOpt.isPresent()) {
            PreRegSection namesSec = namesOpt.get();
            boolean hasPrimarySsic = false;
            if (namesSec.getFields() != null) {
                for (Map<String, Object> f : namesSec.getFields()) {
                    if ("activities.primary".equals(f.get("key"))) {
                        hasPrimarySsic = true;
                        break;
                    }
                }
            }
            if (!hasPrimarySsic) {
                List<Map<String, Object>> nameFields = List.of(
                    createField("names[0]", "Proposed Name Option 1", "text", true, "Primary preferred name", null),
                    createField("names[1]", "Proposed Name Option 2", "text", true, "Backup name if Option 1 is unavailable", null),
                    createField("names[2]", "Proposed Name Option 3", "text", false, "Alternative name or enter NA", null),
                    createField("activities.primary", "Primary Business Activity (SSIC Code)", "ssic-single", true, "Search by SSIC code or activity name", null),
                    createField("activities.secondary", "Secondary Business Activity (SSIC Code)", "ssic-single", false, "Search by SSIC code or activity name", null),
                    createField("names[3]", "Proposed Name Option 4", "text", false, "", null)
                );
                namesSec.setFields(nameFields);
                namesSec.setLastUpdatedBy("System Migration");
                namesSec.setLastUpdatedAt(new Date());

                if (namesSec.getPublishedData() != null) {
                    Map<String, Object> newPublishData = new HashMap<>(namesSec.getPublishedData());
                    newPublishData.put("fields", nameFields);
                    namesSec.setPublishedData(newPublishData);
                } else {
                    namesSec.setPublishedData(Map.of(
                        "id", namesSec.getId(),
                        "key", namesSec.getKey(),
                        "title", namesSec.getTitle(),
                        "description", namesSec.getDescription(),
                        "type", namesSec.getType(),
                        "sortOrder", namesSec.getSortOrder(),
                        "fields", nameFields,
                        "applicableServices", "All"
                    ));
                }
                preRegSectionRepository.save(namesSec);
            }
        }

        // Special case: if office exists but is using the old textarea format, update it.
        Optional<PreRegSection> officeOpt = preRegSectionRepository.findById("sec-office");
        if (officeOpt.isPresent()) {
            PreRegSection officeSec = officeOpt.get();
            boolean hasPostalCode = false;
            if (officeSec.getFields() != null) {
                for (Map<String, Object> f : officeSec.getFields()) {
                    if ("office.postalCode".equals(f.get("key"))) {
                        hasPostalCode = true;
                        break;
                    }
                }
            }
            if (!hasPostalCode) {
                List<Map<String, Object>> officeFields = new ArrayList<>();
                officeFields.add(createFieldWithHint("office.useService", "Registered Address Option *", "select", true, "$480 per year", List.of("false:Yes I have Office Address", "true:Corporate Secretary Address"), "Statutorily required. Real address in Singapore, mail scanned weekly."));
                officeFields.add(createField("office.postalCode", "Postal Code", "text", true, "e.g. 079903", null));
                officeFields.add(createField("office.block", "Block Number", "text", true, "e.g. 10", null));
                officeFields.add(createField("office.streetName", "Street Name", "text", true, "e.g. Anson Road", null));
                officeFields.add(createField("office.floor", "Floor", "text", true, "e.g. 16", null));
                officeFields.add(createField("office.unit", "Unit", "text", true, "e.g. 04", null));
                officeFields.add(createField("office.building", "Building", "text", false, "e.g. International Plaza", null));
                officeFields.add(createField("office.country", "Country", "text", false, "e.g. Singapore", null));

                officeSec.setFields(officeFields);
                officeSec.setLastUpdatedBy("System Migration");
                officeSec.setLastUpdatedAt(new Date());

                if (officeSec.getPublishedData() != null) {
                    Map<String, Object> newPublishData = new HashMap<>(officeSec.getPublishedData());
                    newPublishData.put("fields", officeFields);
                    officeSec.setPublishedData(newPublishData);
                } else {
                    officeSec.setPublishedData(Map.of(
                        "id", officeSec.getId(),
                        "key", officeSec.getKey(),
                        "title", officeSec.getTitle(),
                        "description", officeSec.getDescription(),
                        "type", officeSec.getType(),
                        "sortOrder", officeSec.getSortOrder(),
                        "fields", officeFields,
                        "applicableServices", "All"
                    ));
                }
                preRegSectionRepository.save(officeSec);
            }
        }

        // Correct sec-office options to remove commas and split correctly, and use Corporate Secretary Address
        List<PreRegSection> allSecs = preRegSectionRepository.findAll();
        for (PreRegSection s : allSecs) {
            boolean needsCorrection = false;
            List<Map<String, Object>> fields = s.getFields();
            if (fields != null) {
                for (Map<String, Object> f : fields) {
                    if ("office.useService".equals(f.get("key"))) {
                        Object optsObj = f.get("options");
                        if (optsObj instanceof List) {
                            List<?> opts = (List<?>) optsObj;
                            if (opts.size() > 2 || opts.stream().anyMatch(o -> o.toString().contains(",") || o.toString().contains("Globalisor Address"))) {
                                f.put("options", List.of("false:Yes I have Office Address", "true:Corporate Secretary Address"));
                                needsCorrection = true;
                            }
                        }
                    }
                }
            }
            if (needsCorrection) {
                s.setFields(fields);
                if (s.getPublishedData() != null) {
                    Map<String, Object> newPublishData = new HashMap<>(s.getPublishedData());
                    newPublishData.put("fields", fields);
                    s.setPublishedData(newPublishData);
                }
                preRegSectionRepository.save(s);
            }
        }

        // Ensure dummy checkout sections are removed if they exist
        if (preRegSectionRepository.existsById("sec-checkout")) {
            preRegSectionRepository.deleteById("sec-checkout");
        }
        if (preRegSectionRepository.existsById("sec-checkout-foreigner")) {
            preRegSectionRepository.deleteById("sec-checkout-foreigner");
        }

        List<PreRegSection> locals = preRegSectionRepository.findByJourneyType("LOCAL");
        if (locals.isEmpty()) {
            List<PreRegSection> defaults = new ArrayList<>();
            // 1. Company Details
            List<Map<String, Object>> nameFields = List.of(
                createField("names[0]", "Proposed Name Option 1", "text", true, "Primary preferred name", null),
                createField("names[1]", "Proposed Name Option 2", "text", true, "Backup name if Option 1 is unavailable", null),
                createField("names[2]", "Proposed Name Option 3", "text", false, "Alternative name or enter NA", null),
                createField("activities.primary", "Primary Business Activity (SSIC Code)", "ssic-single", true, "Search by SSIC code or activity name", null),
                createField("activities.secondary", "Secondary Business Activity (SSIC Code)", "ssic-single", false, "Search by SSIC code or activity name", null),
                createField("names[3]", "Proposed Name Option 4", "text", false, "", null)
            );
            defaults.add(new PreRegSection("sec-names", "names", "Company Details", "Proposed names and activities for ACRA verification", "form", 1, nameFields, "LOCAL"));

            // 2. Directors & Shareholders
            List<Map<String, Object>> directorFields = List.of(
                createFieldWithHint("secretary.required", "Corporate secretary", "switch", false, "$720 per year", null, "Required within 6 months. Handles annual filings and board minutes.")
            );
            defaults.add(new PreRegSection("sec-directors-shareholders", "directors-shareholders", "Directors & Shareholders", "Details of company directors and shareholders", "form", 2, directorFields, "LOCAL"));

            // 3. Add-on Services
            List<Map<String, Object>> addonFields = List.of(
                createFieldWithHint("addons.bankIntro", "Bank account introduction", "switch", false, "$350 one-time", null, "Warm intros to DBS, OCBC, HSBC, Aspire, Wio, Mashreq. We prepare KYC and stay on the call."),
                createFieldWithHint("addons.statCompliance", "Statutory & compliance package", "switch", false, "$480 per year", null, "Annual filings, AGM resolutions, statutory registers maintained, ESOP support when needed."),
                createFieldWithHint("addons.accounting", "Accounting & bookkeeping", "switch", false, "$220 per month", null, "Monthly bookkeeping in Xero, financial statements compiled to standards, payroll with CPF processing."),
                createFieldWithHint("addons.taxCompliance", "Tax compliance package", "switch", false, "$720 per year", null, "Compilation of corporate tax returns (Form C-S), filing of ECI, GST advisory and filings."),
                createFieldWithHint("addons.crossBorderTax", "Cross-Border Tax Structuring", "switch", false, "$4,500 one-time", null, "Advisory on IP holding, transfer pricing policy documentation, setup of offshore corporate wrappers."),
                createFieldWithHint("addons.apostille", "Apostille + Notarisation", "switch", false, "$280 one-time", null, "Legalisation of incorporation files for use in foreign countries. Includes courier fees.")
            );
            defaults.add(new PreRegSection("sec-addons", "addons", "Add-on Services", "Select additional corporate and compliance services", "form", 3, addonFields, "LOCAL"));

            // 4. Registered Office
            List<Map<String, Object>> officeFields = new ArrayList<>();
            officeFields.add(createFieldWithHint("office.useService", "Registered Address Option *", "select", true, "$480 per year", List.of("false:Yes I have Office Address", "true:Corporate Secretary Address"), "Statutorily required. Real address in Singapore, mail scanned weekly."));
            officeFields.add(createField("office.postalCode", "Postal Code", "text", true, "e.g. 079903", null));
            officeFields.add(createField("office.block", "Block Number", "text", true, "e.g. 10", null));
            officeFields.add(createField("office.streetName", "Street Name", "text", true, "e.g. Anson Road", null));
            officeFields.add(createField("office.floor", "Floor", "text", true, "e.g. 16", null));
            officeFields.add(createField("office.unit", "Unit", "text", true, "e.g. 04", null));
            officeFields.add(createField("office.building", "Building", "text", false, "e.g. International Plaza", null));
            officeFields.add(createField("office.country", "Country", "text", false, "e.g. Singapore", null));
            defaults.add(new PreRegSection("sec-office", "office", "Registered Office", "Singapore registered office details", "form", 4, officeFields, "LOCAL"));

            // 5. Your package is up Next
            List<Map<String, Object>> contactFields = List.of(
                createField("contact.firstName", "First Name", "text", true, "First name", null),
                createField("contact.lastName", "Last Name", "text", true, "Last name", null),
                createField("contact.phone", "Contact Number", "text", true, "1234 5678", null),
                createField("contact.email", "Email ID", "text", true, "email@example.com", null)
            );
            defaults.add(new PreRegSection("sec-package-next", "contact", "Your package is up Next", "Provide your contact information for package processing", "form", 5, contactFields, "LOCAL"));

            preRegSectionRepository.saveAll(defaults);
        }

        List<PreRegSection> foreigners = preRegSectionRepository.findByJourneyType("FOREIGNER");
        if (foreigners.isEmpty()) {
            List<PreRegSection> defaults = new ArrayList<>();
            // 1. Company Details
            List<Map<String, Object>> nameFields = List.of(
                createField("names[0]", "Proposed Name Option 1", "text", true, "Primary preferred name", null),
                createField("names[1]", "Proposed Name Option 2", "text", true, "Backup name if Option 1 is unavailable", null),
                createField("names[2]", "Proposed Name Option 3", "text", false, "Alternative name or enter NA", null),
                createField("activities.primary", "Primary Business Activity (SSIC Code)", "ssic-single", true, "Search by SSIC code or activity name", null),
                createField("activities.secondary", "Secondary Business Activity (SSIC Code)", "ssic-single", false, "Search by SSIC code or activity name", null),
                createField("names[3]", "Proposed Name Option 4", "text", false, "", null)
            );
            defaults.add(new PreRegSection("sec-names-foreigner", "names", "Company Details", "Proposed names and activities for ACRA verification", "form", 1, nameFields, "FOREIGNER"));

            // 2. Directors & Shareholders
            List<Map<String, Object>> directorFields = List.of(
                createFieldWithHint("secretary.required", "Corporate secretary", "switch", false, "$720 per year", null, "Required within 6 months. Handles annual filings and board minutes.")
            );
            defaults.add(new PreRegSection("sec-directors-shareholders-foreigner", "directors-shareholders", "Directors & Shareholders", "Details of company directors and shareholders", "form", 2, directorFields, "FOREIGNER"));

            // 3. Add-on Services
            List<Map<String, Object>> addonFields = List.of(
                createFieldWithHint("addons.bankIntro", "Bank account introduction", "switch", false, "$350 one-time", null, "Warm intros to DBS, OCBC, HSBC, Aspire, Wio, Mashreq. We prepare KYC and stay on the call."),
                createFieldWithHint("addons.statCompliance", "Statutory & compliance package", "switch", false, "$480 per year", null, "Annual filings, AGM resolutions, statutory registers maintained, ESOP support when needed."),
                createFieldWithHint("addons.accounting", "Accounting & bookkeeping", "switch", false, "$220 per month", null, "Monthly bookkeeping in Xero, financial statements compiled to standards, payroll with CPF processing."),
                createFieldWithHint("addons.taxCompliance", "Tax compliance package", "switch", false, "$720 per year", null, "Compilation of corporate tax returns (Form C-S), filing of ECI, GST advisory and filings."),
                createFieldWithHint("addons.crossBorderTax", "Cross-Border Tax Structuring", "switch", false, "$4,500 one-time", null, "Advisory on IP holding, transfer pricing policy documentation, setup of offshore corporate wrappers."),
                createFieldWithHint("addons.apostille", "Apostille + Notarisation", "switch", false, "$280 one-time", null, "Legalisation of incorporation files for use in foreign countries. Includes courier fees.")
            );
            defaults.add(new PreRegSection("sec-addons-foreigner", "addons", "Add-on Services", "Select additional corporate and compliance services", "form", 3, addonFields, "FOREIGNER"));

            // 4. Registered Office
            List<Map<String, Object>> officeFields = new ArrayList<>();
            officeFields.add(createFieldWithHint("office.useService", "Registered Address Option *", "select", true, "$480 per year", List.of("false:Yes I have Office Address", "true:Corporate Secretary Address"), "Statutorily required. Real address in Singapore, mail scanned weekly."));
            officeFields.add(createField("office.postalCode", "Postal Code", "text", true, "e.g. 079903", null));
            officeFields.add(createField("office.block", "Block Number", "text", true, "e.g. 10", null));
            officeFields.add(createField("office.streetName", "Street Name", "text", true, "e.g. Anson Road", null));
            officeFields.add(createField("office.floor", "Floor", "text", true, "e.g. 16", null));
            officeFields.add(createField("office.unit", "Unit", "text", true, "e.g. 04", null));
            officeFields.add(createField("office.building", "Building", "text", false, "e.g. International Plaza", null));
            officeFields.add(createField("office.country", "Country", "text", false, "e.g. Singapore", null));
            defaults.add(new PreRegSection("sec-office-foreigner", "office", "Registered Office", "Singapore registered office details", "form", 4, officeFields, "FOREIGNER"));

            // 5. Your package is up Next
            List<Map<String, Object>> contactFields = List.of(
                createField("contact.firstName", "First Name", "text", true, "First name", null),
                createField("contact.lastName", "Last Name", "text", true, "Last name", null),
                createField("contact.phone", "Contact Number", "text", true, "1234 5678", null),
                createField("contact.email", "Email ID", "text", true, "email@example.com", null)
            );
            defaults.add(new PreRegSection("sec-package-next-foreigner", "contact", "Your package is up Next", "Provide your contact information for package processing", "form", 5, contactFields, "FOREIGNER"));

            preRegSectionRepository.saveAll(defaults);
        }

        migrateExistingSections();
    }

    private Map<String, Object> createField(String key, String label, String type, boolean required, String placeholder, List<String> options) {
        Map<String, Object> f = new HashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("type", type);
        f.put("required", required);
        if (placeholder != null) f.put("placeholder", placeholder);
        if (options != null) f.put("options", options);
        return f;
    }

    private Map<String, Object> createFieldWithHint(String key, String label, String type, boolean required, String placeholder, List<String> options, String hint) {
        Map<String, Object> f = new HashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("type", type);
        f.put("required", required);
        if (placeholder != null) f.put("placeholder", placeholder);
        if (options != null) f.put("options", options);
        if (hint != null) f.put("hint", hint);
        return f;
    }

    private void migrateExistingSections() {
        List<PreRegSection> sections = preRegSectionRepository.findAll();
        boolean anyChanged = false;
        for (PreRegSection s : sections) {
            if (s.getPublishedData() == null || !"PUBLISHED".equals(s.getStatus())) {
                s.setStatus("PUBLISHED");
                s.setLastUpdatedBy("System Migration");
                s.setLastUpdatedAt(new Date());
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("id", s.getId());
                snapshot.put("key", s.getKey());
                snapshot.put("title", s.getTitle());
                snapshot.put("description", s.getDescription());
                snapshot.put("type", s.getType() != null ? s.getType() : "form");
                snapshot.put("sortOrder", s.getSortOrder() != null ? s.getSortOrder() : 99);
                snapshot.put("fields", s.getFields() != null ? s.getFields() : new ArrayList<>());
                snapshot.put("applicableServices", s.getApplicableServices() != null ? s.getApplicableServices() : "All");
                snapshot.put("checklists", s.getChecklists());
                snapshot.put("faqs", s.getFaqs());
                snapshot.put("attachments", s.getAttachments());
                snapshot.put("documents", s.getDocuments());
                snapshot.put("journeyType", s.getJourneyType() != null ? s.getJourneyType() : "LOCAL");
                s.setPublishedData(snapshot);
                anyChanged = true;
            }
        }
        if (anyChanged) {
            preRegSectionRepository.saveAll(sections);
        }
    }

    private String getLoggedInAdminName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            return userDetails.getFirstName() + " " + userDetails.getLastName();
        }
        return "Admin";
    }

    @GetMapping
    public ResponseEntity<?> getAdminSections(@RequestParam(required = false, defaultValue = "LOCAL") String journeyType) {
        seedDefaultSections();
        List<PreRegSection> sections = preRegSectionRepository.findByJourneyType(journeyType);
        sections.sort(Comparator.comparingInt(PreRegSection::getSortOrder));
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/published")
    public ResponseEntity<?> getPublishedSections(@RequestParam(required = false, defaultValue = "LOCAL") String journeyType) {
        seedDefaultSections();
        List<PreRegSection> sections = preRegSectionRepository.findByJourneyType(journeyType);
        sections.sort(Comparator.comparingInt(PreRegSection::getSortOrder));

        List<Map<String, Object>> published = new ArrayList<>();
        for (PreRegSection s : sections) {
            if ("PUBLISHED".equals(s.getStatus()) && s.getPublishedData() != null) {
                published.add(s.getPublishedData());
            }
        }
        return ResponseEntity.ok(published);
    }

    @GetMapping("/preview")
    public ResponseEntity<?> getPreviewSections(@RequestParam(required = false, defaultValue = "LOCAL") String journeyType) {
        seedDefaultSections();
        List<PreRegSection> sections = preRegSectionRepository.findByJourneyType(journeyType);
        sections.sort(Comparator.comparingInt(PreRegSection::getSortOrder));
        
        // Map draft structures as they look
        List<Map<String, Object>> preview = new ArrayList<>();
        for (PreRegSection s : sections) {
            // Include everything except if explicitly unpublished
            if (!"UNPUBLISHED".equals(s.getStatus())) {
                Map<String, Object> sMap = new HashMap<>();
                sMap.put("id", s.getId());
                sMap.put("key", s.getKey());
                sMap.put("title", s.getTitle());
                sMap.put("description", s.getDescription());
                sMap.put("type", s.getType());
                sMap.put("sortOrder", s.getSortOrder());
                sMap.put("fields", s.getFields());
                sMap.put("applicableServices", s.getApplicableServices());
                sMap.put("checklists", s.getChecklists());
                sMap.put("faqs", s.getFaqs());
                sMap.put("attachments", s.getAttachments());
                sMap.put("documents", s.getDocuments());
                sMap.put("journeyType", s.getJourneyType());
                preview.add(sMap);
            }
        }
        return ResponseEntity.ok(preview);
    }

    @PostMapping
    public ResponseEntity<?> createSection(@RequestBody PreRegSection section) {
        String adminName = getLoggedInAdminName();
        section.setLastUpdatedBy(adminName);
        section.setLastUpdatedAt(new Date());
        section.setStatus("DRAFT");
        if (section.getJourneyType() == null || section.getJourneyType().isEmpty()) {
            section.setJourneyType("LOCAL");
        }

        // Set sort order to max + 1
        List<PreRegSection> sections = preRegSectionRepository.findByJourneyType(section.getJourneyType());
        int maxOrder = sections.stream().mapToInt(PreRegSection::getSortOrder).max().orElse(0);
        section.setSortOrder(maxOrder + 1);

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSection(@PathVariable String id, @RequestBody PreRegSection sectionUpdates) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        PreRegSection section = sectionOpt.get();
        boolean wasPublished = "PUBLISHED".equals(section.getStatus());

        section.setTitle(sectionUpdates.getTitle());
        section.setDescription(sectionUpdates.getDescription());
        section.setType(sectionUpdates.getType());
        section.setFields(sectionUpdates.getFields());
        section.setApplicableServices(sectionUpdates.getApplicableServices());
        section.setChecklists(sectionUpdates.getChecklists());
        section.setFaqs(sectionUpdates.getFaqs());
        section.setAttachments(sectionUpdates.getAttachments());
        section.setDocuments(sectionUpdates.getDocuments());
        if (sectionUpdates.getJourneyType() != null) {
            section.setJourneyType(sectionUpdates.getJourneyType());
        }

        // Don't allow changing key for default seeded sections
        if (!section.getId().startsWith("sec-")) {
            section.setKey(sectionUpdates.getKey());
        }

        section.setLastUpdatedBy(getLoggedInAdminName());
        section.setLastUpdatedAt(new Date());

        if (wasPublished) {
            section.setStatus("PUBLISHED");
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("id", section.getId());
            snapshot.put("key", section.getKey());
            snapshot.put("title", section.getTitle());
            snapshot.put("description", section.getDescription());
            snapshot.put("type", section.getType());
            snapshot.put("sortOrder", section.getSortOrder());
            snapshot.put("fields", section.getFields());
            snapshot.put("applicableServices", section.getApplicableServices());
            snapshot.put("checklists", section.getChecklists());
            snapshot.put("faqs", section.getFaqs());
            snapshot.put("attachments", section.getAttachments());
            snapshot.put("documents", section.getDocuments());
            snapshot.put("journeyType", section.getJourneyType());
            section.setPublishedData(snapshot);
        } else {
            section.setStatus("DRAFT");
        }

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSection(@PathVariable String id) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        preRegSectionRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publishSection(@PathVariable String id) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        PreRegSection section = sectionOpt.get();
        section.setStatus("PUBLISHED");
        section.setLastUpdatedBy(getLoggedInAdminName());
        section.setLastUpdatedAt(new Date());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", section.getId());
        snapshot.put("key", section.getKey());
        snapshot.put("title", section.getTitle());
        snapshot.put("description", section.getDescription());
        snapshot.put("type", section.getType());
        snapshot.put("sortOrder", section.getSortOrder());
        snapshot.put("fields", section.getFields());
        snapshot.put("applicableServices", section.getApplicableServices());
        snapshot.put("checklists", section.getChecklists());
        snapshot.put("faqs", section.getFaqs());
        snapshot.put("attachments", section.getAttachments());
        snapshot.put("documents", section.getDocuments());
        snapshot.put("journeyType", section.getJourneyType());
        section.setPublishedData(snapshot);

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublishSection(@PathVariable String id) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        PreRegSection section = sectionOpt.get();
        section.setStatus("UNPUBLISHED");
        section.setLastUpdatedBy(getLoggedInAdminName());
        section.setLastUpdatedAt(new Date());

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorderSections(@RequestBody List<String> orderIds) {
        List<PreRegSection> sections = preRegSectionRepository.findAll();
        String adminName = getLoggedInAdminName();
        Date now = new Date();

        for (int i = 0; i < orderIds.size(); i++) {
            String id = orderIds.get(i);
            for (PreRegSection s : sections) {
                if (s.getId().equals(id)) {
                    s.setSortOrder(i + 1);
                    s.setLastUpdatedBy(adminName);
                    s.setLastUpdatedAt(now);
                    
                    if (s.getPublishedData() != null) {
                        Map<String, Object> snapshot = new HashMap<>(s.getPublishedData());
                        snapshot.put("sortOrder", i + 1);
                        s.setPublishedData(snapshot);
                    }
                    break;
                }
            }
        }
        preRegSectionRepository.saveAll(sections);
        return ResponseEntity.ok().build();
    }
}
