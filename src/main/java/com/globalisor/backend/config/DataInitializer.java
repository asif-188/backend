package com.globalisor.backend.config;

import com.globalisor.backend.model.*;
import com.globalisor.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.globalisor.backend.security.EncryptionUtils;

import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    UserRepository userRepository;

    @Autowired
    AttendanceRepository attendanceRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    KycRepository kycRepository;

    @Autowired
    ComplianceRepository complianceRepository;

    @Autowired
    ClientDocumentRepository clientDocumentRepository;

    @Autowired
    StaticContentRepository staticContentRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    CatalogItemRepository catalogItemRepository;

    @Autowired
    SsicActivityRepository ssicActivityRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    EncryptionUtils encryptionUtils;

    @Value("${globalisor.app.seed:true}")
    private boolean shouldSeed;

    @Override
    public void run(String... args) throws Exception {
        // ALWAYS seed default admin/staff if not exists, regardless of shouldSeed flag
        try {
            String defaultPassword = encoder.encode("password123");
            if (!userRepository.existsById("staff-admin")) {
                User admin = new User("Admin", "Globalisor", "admin@globalisor.com", defaultPassword);
                admin.setId("staff-admin");
                admin.setRole("ADMIN");
                userRepository.save(admin);
                System.out.println("Seeded default admin@globalisor.com");
            }
        } catch (Exception e) {
            System.err.println("Failed to seed default admin: " + e.getMessage());
        }

        if (!shouldSeed) {
            System.out.println("Mock seeding is disabled by configuration flag.");
            return;
        }
        // Auto-seed guest user for testing/onboarding fallback
        try {
            String encryptedGuestEmail = encryptionUtils.encryptQueryable("guest@globalisor.com");
            if (!userRepository.existsByEmail(encryptedGuestEmail)) {
                String defaultPassword = encoder.encode("password123");
                User guest = new User("Guest", "Client", "guest@globalisor.com", defaultPassword);
                guest.setId("C-GUEST");
                guest.setRole("USER");
                userRepository.save(guest);

                if (requirementRepository.findFirstByUserId("C-GUEST").isEmpty()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("names", Arrays.asList("Guest Venture Pte. Ltd."));
                    Requirement req = new Requirement("C-GUEST", data);
                    req.setStatus("pending");
                    requirementRepository.save(req);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to seed guest@globalisor.com: " + e.getMessage());
        }
        // 1. Seed Users (Clients)
        if (userRepository.count() == 0) {
            String defaultPassword = encoder.encode("password123");

            User ethan = new User("Ethan", "Tan", "ethan.tan@lionpath.com", defaultPassword);
            ethan.setId("C-1001");
            ethan.setRole("USER");
            userRepository.save(ethan);

            User priya = new User("Priya", "Sharma", "priya.s@merlion.com", defaultPassword);
            priya.setId("C-1002");
            priya.setRole("USER");
            userRepository.save(priya);

            User ravi = new User("Ravi", "Kumar", "ravi.k@harbouredge.com", defaultPassword);
            ravi.setId("C-1007");
            ravi.setRole("USER");
            userRepository.save(ravi);

            User priya2 = new User("Priya", "Sharma", "priya.sharma@greenpath.eco", defaultPassword);
            priya2.setId("C-1010");
            priya2.setRole("USER");
            userRepository.save(priya2);

            User marcus = new User("Marcus", "Ng", "marcus.ng@cityscape.com", defaultPassword);
            marcus.setId("C-1013");
            marcus.setRole("USER");
            userRepository.save(marcus);

            User asif = new User("Mohammad", "Asif", "mohammad.a@example.com", defaultPassword);
            asif.setId("C-1004");
            asif.setRole("USER");
            userRepository.save(asif);

            // Also seed a default staff/admin user if not exists
            User admin = new User("Admin", "Globalisor", "admin@globalisor.com", defaultPassword);
            admin.setId("staff-admin");
            admin.setRole("ADMIN");
            userRepository.save(admin);
            
            User staff = new User("Sarah", "Lim", "staff.sarah@globalisor.com", defaultPassword);
            staff.setId("staff-sarah");
            staff.setRole("STAFF");
            staff.setPlainPassword("password123");
            staff.setEmployeeId("GLOB-STF-1001");
            staff.setDesignation("Lead Compliance Officer");
            staff.setDepartment("AML & Operations");
            staff.setCardStatus("ACTIVE");
            staff.setCardIssueDate("2026-01-01");
            staff.setCardValidUntil("2028-01-01");
            userRepository.save(staff);
        }

        // 2. Seed Requirements (Services)
        if (requirementRepository.count() == 0) {
            saveRequirement("C-1001", "LionPath Trading Pte. Ltd.", "In Progress");
            saveRequirement("C-1002", "Merlion Ventures Pte. Ltd.", "Completed");
            saveRequirement("C-1007", "HarbourEdge Logistics Pte. Ltd.", "In Progress");
            saveRequirement("C-1010", "GreenPath Eco Pte. Ltd.", "Submitted to ACRA");
            saveRequirement("C-1013", "CityScape Properties Pte. Ltd.", "New");
        }

        // 3. Seed Blogs
        if (blogRepository.count() == 0) {
            Blog blog1 = new Blog();
            blog1.setId("blog-1");
            blog1.setTitle("New ACRA Regulatory Updates for 2026");
            blog1.setExcerpt("Key changes to company filing requirements and digital signature policies you need to know.");
            blog1.setContent("Here is the detailed content for ACRA updates...");
            blog1.setCategory("Compliance");
            blog1.setAuthor("Admin Team");
            blog1.setDate("May 10, 2026");
            blog1.setPublished(true);
            blogRepository.save(blog1);
        }

        // 4. Seed KYC
        if (kycRepository.count() == 0) {
            saveKyc("KYC-001", "C-1001", "Ethan Tan", "Passport", "Singapore", "approved", "Low");
            saveKyc("KYC-002", "C-1002", "Priya Sharma", "NRIC", "Singapore", "approved", "Medium");
            saveKyc("KYC-007", "C-1007", "Ravi Kumar", "Passport", "India", "approved", "Low");
            saveKyc("KYC-010", "C-1010", "Priya Sharma", "Passport", "India", "approved", "Low");
            saveKyc("KYC-013", "C-1013", "Marcus Ng", "NRIC", "Singapore", "pending", "High");
        }

        // 5. Seed Compliance
        if (complianceRepository.count() == 0) {
            saveCompliance("COMP-001", "C-1001", "Ethan Tan", "Annual Return Filing", "pending", "Low");
            saveCompliance("COMP-002", "C-1002", "Priya Sharma", "AGM Declaration", "approved", "Low");
        }

        // 6. Seed Documents
        if (clientDocumentRepository.count() == 0) {
            saveDocument("DOC-101", "Passport", "ethan_passport.pdf", "approved", "C-1001", "2026-01-13");
            saveDocument("DOC-102", "Address Proof", "ethan_address.pdf", "approved", "C-1001", "2026-01-13");
            saveDocument("DOC-103", "Director ID", "ethan_director_id.pdf", "pending", "C-1001", "2026-03-04");
            saveDocument("DOC-104", "Passport", "priya_passport.pdf", "approved", "C-1002", "2026-01-19");
            saveDocument("DOC-105", "Address Proof", "priya_address.pdf", "approved", "C-1002", "2026-01-19");
            saveDocument("DOC-106", "Business Supporting Document", "merlion_biz_plan.pdf", "approved", "C-1002", "2026-01-20");
            saveDocument("DOC-107", "Passport", "ravi_passport.pdf", "approved", "C-1007", "2026-02-28");
            saveDocument("DOC-108", "Director ID", "ravi_director_id.pdf", "approved", "C-1007", "2026-02-28");
        }

        // 7. Seed Static Content
        if (staticContentRepository.count() == 0) {
            StaticContent sc1 = new StaticContent();
            sc1.setId("sc-1");
            sc1.setTitle("Internal SOP for KYC Verification");
            sc1.setDescription("Standard operating procedure for verifying identity documents for new applications.");
            sc1.setContent("Full SOP details for internal staff review...");
            sc1.setPortal("staff");
            sc1.setCategory("Operational Guidelines");
            sc1.setIsPublished(true);
            sc1.setIsPinned(true);
            staticContentRepository.save(sc1);

            StaticContent sc2 = new StaticContent();
            sc2.setId("sc-3");
            sc2.setTitle("Data Extraction Protocol");
            sc2.setDescription("Guideline for extracting information from ACRA Bizfiles.");
            sc2.setContent("Extraction steps and common pitfalls...");
            sc2.setPortal("staff");
            sc2.setCategory("Technical Docs");
            sc2.setIsPublished(true);
            sc2.setIsPinned(false);
            staticContentRepository.save(sc2);
        }

        // 8. Seed Messages
        if (messageRepository.count() == 0) {
            saveMessage("msg-1", "C-1001", "C-1001", "Ethan Tan", "client", "Hello, I have a question about my incorporation progress.", 1715580000000L);
            saveMessage("msg-2", "C-1001", "staff-sarah", "Sarah Lim", "staff", "Hello Ethan! I'm reviewing your documents right now. Everything looks good so far.", 1715580100000L);
            saveMessage("msg-3", "C-1001", "staff-sarah", "Sarah Lim", "staff", "Hello", 1778741843166L);
            saveMessage("msg-4", "C-1001", "C-1001", "Ethan Tan", "client", "Hello", 1778742050687L);
        }
        
        // 9. Seed Catalog
        if (catalogItemRepository.count() == 0) {
            saveCatalogItem("CAT-1", "Nominee Director", "SGD 3,000/yr", "Qualified local resident director to meet ACRA requirements.", "Corporate Secretarial");
            saveCatalogItem("CAT-2", "Registered Address", "SGD 300/yr", "Premium CBD business address for mail handling.", "Administrative");
        }

        // 10. Seed SSIC Activities
        if (ssicActivityRepository.count() == 0) {
            ssicActivityRepository.save(new SsicActivity("ssic-62011", "62011", "Development of software for interactive digital media", "Information and Communications", "Development of mobile apps, games, e-commerce platforms and interactive digital products.", "PUBLISHED"));
            ssicActivityRepository.save(new SsicActivity("ssic-62021", "62021", "Information technology consultancy", "Information and Communications", "Consultancy services for computer systems, network designs, and IT systems integration.", "PUBLISHED"));
            ssicActivityRepository.save(new SsicActivity("ssic-46900", "46900", "General wholesale trade (including general importers and exporters)", "Wholesale Trade", "Import, export, and wholesale of a wide variety of goods without a dominant product line.", "PUBLISHED"));
            ssicActivityRepository.save(new SsicActivity("ssic-70201", "70201", "Management consultancy services", "Professional, Scientific and Technical Activities", "Providing advisory and operational assistance to businesses on management, strategy, and logistics.", "PUBLISHED"));
            ssicActivityRepository.save(new SsicActivity("ssic-64201", "64201", "Holding companies", "Financial and Insurance Activities", "Investment holding companies that hold shares in subsidiary companies.", "PUBLISHED"));
        }

        // Seed Attendance History for Sarah Lim
        if (attendanceRepository.count() == 0) {
            for (int i = 15; i >= 1; i--) {
                Attendance att = new Attendance();
                att.setId("att-mock-" + i);
                att.setUserId("staff-sarah");
                
                LocalDate d = LocalDate.now().minusDays(i);
                att.setDate(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
                
                long baseTime = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long signInOffset = (long)(8.75 * 3600000) + (long)(Math.random() * 45 * 60000);
                long signIn = baseTime + signInOffset;
                
                long signOutOffset = (long)(17.5 * 3600000) + (long)(Math.random() * 60 * 60000);
                long signOut = baseTime + signOutOffset;
                
                att.setSignInTime(signIn);
                att.setSignOutTime(signOut);
                att.setTimeZone(ZoneId.systemDefault().getId());
                att.setIpAddress("192.168.1." + (100 + i));
                att.setDevice("Windows Desktop (Chrome)");
                att.setLastActivity(signOut);
                att.setStatus("SIGNED_OUT");
                
                long breakIn = baseTime + (long)(12.5 * 3600000);
                long breakOut = baseTime + (long)(13.5 * 3600000);
                Attendance.BreakSession bs = new Attendance.BreakSession(breakIn, breakOut);
                att.setBreaks(Arrays.asList(bs));
                
                double hours = (signOut - signIn - (breakOut - breakIn)) / 3600000.0;
                att.setTotalWorkingHours(Math.max(0.0, Math.round(hours * 100.0) / 100.0));
                
                attendanceRepository.save(att);
            }
        }
    }

    private void saveRequirement(String userId, String companyName, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("names", Arrays.asList(companyName));
        Requirement req = new Requirement(userId, data);
        req.setStatus(status);
        requirementRepository.save(req);
    }

    private void saveKyc(String id, String clientId, String name, String idType, String nation, String status, String risk) {
        Kyc kyc = new Kyc();
        kyc.setId(id);
        kyc.setClientId(clientId);
        kyc.setName(name);
        kyc.setIdType(idType);
        kyc.setNation(nation);
        kyc.setStatus(status);
        kyc.setRisk(risk);
        kycRepository.save(kyc);
    }

    private void saveCompliance(String id, String clientId, String name, String type, String status, String risk) {
        Compliance comp = new Compliance();
        comp.setId(id);
        comp.setClientId(clientId);
        comp.setName(name);
        comp.setType(type);
        comp.setStatus(status);
        comp.setRisk(risk);
        complianceRepository.save(comp);
    }

    private void saveDocument(String id, String title, String file, String status, String clientId, String date) {
        ClientDocument doc = new ClientDocument();
        doc.setId(id);
        doc.setTitle(title);
        doc.setFile(file);
        doc.setStatus(status);
        doc.setClientId(clientId);
        doc.setDate(date);
        clientDocumentRepository.save(doc);
    }

    private void saveMessage(String id, String clientId, String senderId, String senderName, String senderRole, String text, long timestamp) {
        Message msg = new Message();
        msg.setId(id);
        msg.setClientId(clientId);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setSenderRole(senderRole);
        msg.setText(text);
        msg.setTimestamp(timestamp);
        messageRepository.save(msg);
    }
    
    private void saveCatalogItem(String id, String name, String price, String description, String category) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setName(name);
        item.setPrice(price);
        item.setDescription(description);
        item.setCategory(category);
        catalogItemRepository.save(item);
    }
}
