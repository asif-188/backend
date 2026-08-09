package com.globalisor.backend.service;

import com.globalisor.backend.model.Requirement;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class DocumentGenerationService {

    private final Map<String, NomineeAppointmentDocumentData> documentCache = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    public static class NomineeAppointmentDocumentData {
        private String id;
        private String companyName = "ABBEY HOLDINGS PTE. LTD.";
        private String uen = "201601260K";
        private String effectiveDate = "the date of Incorporation";
        private String nomineeName = "TANGATURU SUBRAMANIAN ANNAPOORANA";
        private String nomineeAddress = "234 #02-494,COMPASSVALE WALK ,SENGKANG ,SINGAPORE 540234";
        private String nomineeAlternateAddress = "-";
        private String nomineeIdNumber = "S8061258C";
        private String nomineeNationality = "INDIAN";
        private String nomineeEmail = "anu@globalisor.com";
        private String nomineePhone = "+65 81753514";
        private String nomineeDob = "25/08/1980";
        private String witnessName = "";
        private String witnessAddress = "";
        private String witnessIdNumber = "";
        private String witnessOccupation = "";
        private String datedDay = "9th";
        private String datedMonthYear = "August 2026";
        private Date createdAt = new Date();
        private Date updatedAt = new Date();
    }

    public NomineeAppointmentDocumentData createDocumentDataFromRequirement(Requirement requirement, String query) {
        NomineeAppointmentDocumentData doc = new NomineeAppointmentDocumentData();
        doc.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));

        LocalDate now = LocalDate.now();
        int day = now.getDayOfMonth();
        String suffix = getDaySuffix(day);
        doc.setDatedDay(day + suffix);
        doc.setDatedMonthYear(now.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        if (requirement != null && requirement.getData() != null) {
            Map<String, Object> data = requirement.getData();
            Object excelObj = data.get("excelData");
            if (excelObj instanceof Map) {
                Map<?, ?> excel = (Map<?, ?>) excelObj;
                if (excel.get("companyName") != null) {
                    doc.setCompanyName(excel.get("companyName").toString().trim());
                }
                if (excel.get("uen") != null) {
                    doc.setUen(excel.get("uen").toString().trim());
                }
                if (excel.get("incorporationDate") != null) {
                    doc.setEffectiveDate(excel.get("incorporationDate").toString().trim());
                } else if (excel.get("dateOfIncorporation") != null) {
                    doc.setEffectiveDate(excel.get("dateOfIncorporation").toString().trim());
                }

                // Check directors for nominee director
                List<?> dirList = excel.get("directors") instanceof List ? (List<?>) excel.get("directors") : Collections.emptyList();
                Map<?, ?> chosenNominee = null;
                for (Object dObj : dirList) {
                    if (dObj instanceof Map) {
                        Map<?, ?> d = (Map<?, ?>) dObj;
                        Boolean isNom = d.get("isNominee") != null ? Boolean.parseBoolean(d.get("isNominee").toString()) : false;
                        String type = d.get("type") != null ? d.get("type").toString() : "";
                        String name = d.get("name") != null ? d.get("name").toString() : "";
                        if (isNom || type.toLowerCase().contains("nominee") || name.toLowerCase().contains("nominee")) {
                            chosenNominee = d;
                            break;
                        }
                    }
                }

                // If no explicit nominee found, check if query specifically named a director or fallback to first director or default
                if (chosenNominee != null) {
                    if (chosenNominee.get("name") != null && !chosenNominee.get("name").toString().trim().isEmpty()) {
                        doc.setNomineeName(chosenNominee.get("name").toString().trim());
                    }
                    if (chosenNominee.get("address") != null && !chosenNominee.get("address").toString().trim().isEmpty()) {
                        doc.setNomineeAddress(chosenNominee.get("address").toString().trim());
                    }
                    if (chosenNominee.get("idNumber") != null && !chosenNominee.get("idNumber").toString().trim().isEmpty()) {
                        doc.setNomineeIdNumber(chosenNominee.get("idNumber").toString().trim());
                    }
                    if (chosenNominee.get("nationality") != null && !chosenNominee.get("nationality").toString().trim().isEmpty()) {
                        doc.setNomineeNationality(chosenNominee.get("nationality").toString().trim());
                    }
                    if (chosenNominee.get("email") != null && !chosenNominee.get("email").toString().trim().isEmpty()) {
                        doc.setNomineeEmail(chosenNominee.get("email").toString().trim());
                    }
                    if (chosenNominee.get("mobile") != null && !chosenNominee.get("mobile").toString().trim().isEmpty()) {
                        doc.setNomineePhone(chosenNominee.get("mobile").toString().trim());
                    } else if (chosenNominee.get("phone") != null && !chosenNominee.get("phone").toString().trim().isEmpty()) {
                        doc.setNomineePhone(chosenNominee.get("phone").toString().trim());
                    }
                    if (chosenNominee.get("dob") != null && !chosenNominee.get("dob").toString().trim().isEmpty()) {
                        doc.setNomineeDob(chosenNominee.get("dob").toString().trim());
                    }
                }
            }
        }

        documentCache.put(doc.getId(), doc);
        return doc;
    }

    public NomineeAppointmentDocumentData getDocumentData(String docId) {
        if (docId == null) return null;
        return documentCache.get(docId);
    }

    public NomineeAppointmentDocumentData updateDocumentData(String docId, Map<String, Object> updates) {
        NomineeAppointmentDocumentData doc = documentCache.get(docId);
        if (doc == null) {
            doc = new NomineeAppointmentDocumentData();
            doc.setId(docId != null ? docId : UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }

        if (updates.containsKey("companyName") && updates.get("companyName") != null) {
            doc.setCompanyName(updates.get("companyName").toString());
        }
        if (updates.containsKey("uen") && updates.get("uen") != null) {
            doc.setUen(updates.get("uen").toString());
        }
        if (updates.containsKey("effectiveDate") && updates.get("effectiveDate") != null) {
            doc.setEffectiveDate(updates.get("effectiveDate").toString());
        }
        if (updates.containsKey("nomineeName") && updates.get("nomineeName") != null) {
            doc.setNomineeName(updates.get("nomineeName").toString());
        }
        if (updates.containsKey("nomineeAddress") && updates.get("nomineeAddress") != null) {
            doc.setNomineeAddress(updates.get("nomineeAddress").toString());
        }
        if (updates.containsKey("nomineeAlternateAddress") && updates.get("nomineeAlternateAddress") != null) {
            doc.setNomineeAlternateAddress(updates.get("nomineeAlternateAddress").toString());
        }
        if (updates.containsKey("nomineeIdNumber") && updates.get("nomineeIdNumber") != null) {
            doc.setNomineeIdNumber(updates.get("nomineeIdNumber").toString());
        }
        if (updates.containsKey("nomineeNationality") && updates.get("nomineeNationality") != null) {
            doc.setNomineeNationality(updates.get("nomineeNationality").toString());
        }
        if (updates.containsKey("nomineeEmail") && updates.get("nomineeEmail") != null) {
            doc.setNomineeEmail(updates.get("nomineeEmail").toString());
        }
        if (updates.containsKey("nomineePhone") && updates.get("nomineePhone") != null) {
            doc.setNomineePhone(updates.get("nomineePhone").toString());
        }
        if (updates.containsKey("nomineeDob") && updates.get("nomineeDob") != null) {
            doc.setNomineeDob(updates.get("nomineeDob").toString());
        }
        if (updates.containsKey("witnessName") && updates.get("witnessName") != null) {
            doc.setWitnessName(updates.get("witnessName").toString());
        }
        if (updates.containsKey("witnessAddress") && updates.get("witnessAddress") != null) {
            doc.setWitnessAddress(updates.get("witnessAddress").toString());
        }
        if (updates.containsKey("witnessIdNumber") && updates.get("witnessIdNumber") != null) {
            doc.setWitnessIdNumber(updates.get("witnessIdNumber").toString());
        }
        if (updates.containsKey("witnessOccupation") && updates.get("witnessOccupation") != null) {
            doc.setWitnessOccupation(updates.get("witnessOccupation").toString());
        }
        if (updates.containsKey("datedDay") && updates.get("datedDay") != null) {
            doc.setDatedDay(updates.get("datedDay").toString());
        }
        if (updates.containsKey("datedMonthYear") && updates.get("datedMonthYear") != null) {
            doc.setDatedMonthYear(updates.get("datedMonthYear").toString());
        }

        doc.setUpdatedAt(new Date());
        documentCache.put(doc.getId(), doc);
        return doc;
    }

    public byte[] generateDocxBytes(NomineeAppointmentDocumentData doc) throws Exception {
        ClassPathResource resource = new ClassPathResource("Nominee-Director-appointment.docx");
        byte[] templateBytes;
        try (InputStream is = resource.getInputStream()) {
            templateBytes = is.readAllBytes();
        }

        String companyClean = doc.getCompanyName() != null ? doc.getCompanyName().trim() : "ABBEY HOLDINGS PTE. LTD.";
        String companyBase = companyClean.replaceAll("(?i)\\s*PTE\\.?\\s*LTD\\.?", "").trim();
        String uen = doc.getUen() != null ? doc.getUen().trim() : "201601260K";
        String nomineeName = doc.getNomineeName() != null ? doc.getNomineeName().trim() : "TANGATURU SUBRAMANIAN ANNAPOORANA";
        String nomineeAddress = doc.getNomineeAddress() != null ? doc.getNomineeAddress().trim() : "234 #02-494,COMPASSVALE WALK ,SENGKANG ,SINGAPORE 540234";
        String nomineeAlternateAddress = doc.getNomineeAlternateAddress() != null ? doc.getNomineeAlternateAddress().trim() : "-";
        String nomineeIdNumber = doc.getNomineeIdNumber() != null ? doc.getNomineeIdNumber().trim() : "S8061258C";
        String nomineeNationality = doc.getNomineeNationality() != null ? doc.getNomineeNationality().trim() : "INDIAN";
        String nomineeEmail = doc.getNomineeEmail() != null ? doc.getNomineeEmail().trim() : "anu@globalisor.com";
        String nomineePhone = doc.getNomineePhone() != null ? doc.getNomineePhone().trim() : "+65 81753514";
        String nomineeDob = doc.getNomineeDob() != null ? doc.getNomineeDob().trim() : "25/08/1980";
        String datedDay = doc.getDatedDay() != null ? doc.getDatedDay().trim() : "9th";
        String datedMonthYear = doc.getDatedMonthYear() != null ? doc.getDatedMonthYear().trim() : "August 2026";
        String witnessName = doc.getWitnessName() != null ? doc.getWitnessName().trim() : "";
        String witnessAddress = doc.getWitnessAddress() != null ? doc.getWitnessAddress().trim() : "";
        String witnessIdNumber = doc.getWitnessIdNumber() != null ? doc.getWitnessIdNumber().trim() : "";
        String witnessOccupation = doc.getWitnessOccupation() != null ? doc.getWitnessOccupation().trim() : "";

        ByteArrayOutputStream outBaos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(templateBytes));
             ZipOutputStream zos = new ZipOutputStream(outBaos)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                byte[] buffer = zis.readAllBytes();

                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(buffer, StandardCharsets.UTF_8);

                    // 1. Company Name replacements: *** is replaced by base company name
                    xml = xml.replace("<w:t>***</w:t>", "<w:t>" + escapeXml(companyBase) + "</w:t>");
                    xml = xml.replace("***", escapeXml(companyBase));

                    // 2. Company No / UEN
                    xml = xml.replace("<w:t>Company No</w:t></w:r><w:r w:rsidR=\"00A302BA\" w:rsidRPr=\"008D053C\"><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/><w:b/><w:sz w:val=\"20\"/></w:rPr><w:t>:</w:t>",
                            "<w:t>Company No</w:t></w:r><w:r w:rsidR=\"00A302BA\" w:rsidRPr=\"008D053C\"><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/><w:b/><w:sz w:val=\"20\"/></w:rPr><w:t>: " + escapeXml(uen) + "</w:t>");
                    xml = xml.replace("<w:t>Company No:</w:t>", "<w:t>Company No: " + escapeXml(uen) + "</w:t>");

                    // 3. Nominee details replacements
                    xml = xml.replace("<w:t>TANGATURU SUBRAMANIAN ANNAPOORANA</w:t>", "<w:t>" + escapeXml(nomineeName) + "</w:t>");
                    xml = xml.replace("TANGATURU SUBRAMANIAN ANNAPOORANA", escapeXml(nomineeName));

                    xml = xml.replace("<w:t>234 #02-494,COMPASSVALE WALK ,SENGKANG ,SINGAPORE 540234</w:t>", "<w:t>" + escapeXml(nomineeAddress) + "</w:t>");
                    xml = xml.replace("234 #02-494,COMPASSVALE WALK ,SENGKANG ,SINGAPORE 540234", escapeXml(nomineeAddress));

                    xml = xml.replace("<w:t>S8061258C</w:t>", "<w:t>" + escapeXml(nomineeIdNumber) + "</w:t>");
                    xml = xml.replace("S8061258C", escapeXml(nomineeIdNumber));

                    xml = xml.replace("<w:t>INDIAN</w:t>", "<w:t>" + escapeXml(nomineeNationality) + "</w:t>");
                    xml = xml.replace("<w:t>anu@globalisor.com</w:t>", "<w:t>" + escapeXml(nomineeEmail) + "</w:t>");
                    xml = xml.replace("anu@globalisor.com", escapeXml(nomineeEmail));

                    // Phone
                    xml = xml.replace("<w:t>81753514</w:t>", "<w:t>" + escapeXml(nomineePhone.replace("+65", "").trim()) + "</w:t>");

                    // DOB (25/08 + /19 + 80)
                    if (nomineeDob.contains("/")) {
                        String[] parts = nomineeDob.split("/");
                        if (parts.length >= 3) {
                            xml = xml.replace("<w:t>25/08</w:t>", "<w:t>" + escapeXml(parts[0] + "/" + parts[1]) + "</w:t>");
                            String year = parts[2];
                            if (year.length() == 4) {
                                xml = xml.replace("<w:t>/19</w:t>", "<w:t>/" + escapeXml(year.substring(0, 2)) + "</w:t>");
                                xml = xml.replace("<w:t>80</w:t>", "<w:t>" + escapeXml(year.substring(2)) + "</w:t>");
                            }
                        } else {
                            xml = xml.replace("<w:t>25/08</w:t>", "<w:t>" + escapeXml(nomineeDob) + "</w:t>");
                        }
                    } else {
                        xml = xml.replace("<w:t>25/08</w:t>", "<w:t>" + escapeXml(nomineeDob) + "</w:t>");
                    }

                    // Dated this [day] day of [month year]
                    xml = xml.replace("<w:t>  day of</w:t>", "<w:t> " + escapeXml(datedDay) + " day of " + escapeXml(datedMonthYear) + "</w:t>");

                    // Witness details if provided
                    if (!witnessName.isEmpty()) {
                        xml = xml.replace("<w:t>Name:</w:t>", "<w:t>Name: " + escapeXml(witnessName) + "</w:t>");
                    }
                    if (!witnessAddress.isEmpty()) {
                        xml = xml.replace("<w:t>Address:</w:t>", "<w:t>Address: " + escapeXml(witnessAddress) + "</w:t>");
                    }

                    buffer = xml.getBytes(StandardCharsets.UTF_8);
                }

                zos.write(buffer);
                zos.closeEntry();
            }
        }

        return outBaos.toByteArray();
    }

    private String getDaySuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        switch (day % 10) {
            case 1:  return "st";
            case 2:  return "nd";
            case 3:  return "rd";
            default: return "th";
        }
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
