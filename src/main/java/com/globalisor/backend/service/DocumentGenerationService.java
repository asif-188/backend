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
        private String documentType = "nominee_director"; // "director" or "nominee_director"
        private String companyName = "ABBEY HOLDINGS PTE. LTD.";
        private String companyAddress = "10 MARINA BOULEVARD, #39-00 MARINA BAY FINANCIAL CENTRE, SINGAPORE 018983";
        private String uen = "201601260K";
        private String effectiveDate = "the date of Incorporation";
        private String resolutionDate = "9th August 2026";
        
        // Director / Nominee Director
        private String nomineeName = "TANGATURU SUBRAMANIAN ANNAPOORANA";
        private String nomineeAddress = "234 #02-494,COMPASSVALE WALK ,SENGKANG ,SINGAPORE 540234";
        private String nomineeAlternateAddress = "-";
        private String nomineeIdNumber = "S8061258C";
        private String nomineeNationality = "INDIAN";
        private String nomineeEmail = "anu@globalisor.com";
        private String nomineePhone = "+65 81753514";
        private String nomineeDob = "25/08/1980";
        
        // Active Director (for DRIW signatures)
        private String activeDirectorName = "TANGATURU SUBRAMANIAN";
        private String activeDirectorIdNumber = "S8061258C";
        private String secondDirectorName = "TANGATURU SUBRAMANIAN ANNAPOORANA";
        private String secondDirectorIdNumber = "S8061258C";

        // Change of Registered Address
        private String newAddress = "10 MARINA BOULEVARD, #39-00 MARINA BAY FINANCIAL CENTRE, SINGAPORE 018983";
        
        // Nominator (for Nominee Director Letter)
        private String nominatorName = "ABBEY HOLDINGS PTE. LTD.";
        private String nominatorAddress = "10 MARINA BOULEVARD, #39-00 MARINA BAY FINANCIAL CENTRE, SINGAPORE 018983";
        private String nominatorNationality = "SINGAPOREAN";
        private String nominatorIdNumber = "201601260K";
        private String nominatorDob = "26/01/2016";
        private String nominatorEmail = "compliance@abbeyholdings.sg";
        private String nominatorPhone = "+65 67891234";
        private String dateOfBr = "9th August 2026";
        
        // Witness
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
        String docType = "nominee_director";
        if (query != null) {
            String qLower = query.toLowerCase();
            if (qLower.contains("change of address") || qLower.contains("change address") || qLower.contains("registered office")) {
                docType = "change_of_address";
            } else if (qLower.contains("nominee") || qLower.contains("nominie")) {
                docType = "nominee_director";
            } else if (qLower.contains("director") || qLower.contains("appointment of director")) {
                docType = "director";
            }
        }
        return createDocumentDataFromRequirement(requirement, query, docType);
    }

    public NomineeAppointmentDocumentData createDocumentDataFromRequirement(Requirement requirement, String query, String documentType) {
        NomineeAppointmentDocumentData doc = new NomineeAppointmentDocumentData();
        doc.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        if (documentType != null && documentType.equalsIgnoreCase("change_of_address")) {
            doc.setDocumentType("change_of_address");
        } else if (documentType != null && documentType.equalsIgnoreCase("director")) {
            doc.setDocumentType("director");
        } else {
            doc.setDocumentType("nominee_director");
        }

        LocalDate now = LocalDate.now();
        int day = now.getDayOfMonth();
        String suffix = getDaySuffix(day);
        doc.setDatedDay(day + suffix);
        doc.setDatedMonthYear(now.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        doc.setResolutionDate(day + suffix + " " + doc.getDatedMonthYear());
        doc.setDateOfBr(doc.getResolutionDate());

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
                
                // Company address
                if (excel.get("registeredOfficeAddress") != null) {
                    doc.setCompanyAddress(excel.get("registeredOfficeAddress").toString().trim());
                } else if (excel.get("address") != null) {
                    doc.setCompanyAddress(excel.get("address").toString().trim());
                } else if (excel.get("registeredAddress") != null) {
                    doc.setCompanyAddress(excel.get("registeredAddress").toString().trim());
                }
                doc.setNewAddress(doc.getCompanyAddress());

                // Check directors
                List<?> dirList = excel.get("directors") instanceof List ? (List<?>) excel.get("directors") : Collections.emptyList();
                Map<?, ?> chosenDirector = null;
                Map<?, ?> chosenActiveDir = null;

                for (Object dObj : dirList) {
                    if (dObj instanceof Map) {
                        Map<?, ?> d = (Map<?, ?>) dObj;
                        Boolean isNom = d.get("isNominee") != null ? Boolean.parseBoolean(d.get("isNominee").toString()) : false;
                        String type = d.get("type") != null ? d.get("type").toString() : "";
                        String name = d.get("name") != null ? d.get("name").toString() : "";

                        if (doc.getDocumentType().equals("nominee_director")) {
                            if (isNom || type.toLowerCase().contains("nominee") || name.toLowerCase().contains("nominee")) {
                                if (chosenDirector == null) chosenDirector = d;
                            } else {
                                if (chosenActiveDir == null) chosenActiveDir = d;
                            }
                        } else {
                            if (!isNom && !type.toLowerCase().contains("nominee")) {
                                if (chosenDirector == null) chosenDirector = d;
                            } else {
                                if (chosenActiveDir == null) chosenActiveDir = d;
                            }
                        }
                    }
                }

                if (chosenDirector == null && !dirList.isEmpty() && dirList.get(0) instanceof Map) {
                    chosenDirector = (Map<?, ?>) dirList.get(0);
                }
                if (chosenActiveDir == null && dirList.size() > 1 && dirList.get(1) instanceof Map) {
                    chosenActiveDir = (Map<?, ?>) dirList.get(1);
                }

                if (chosenDirector != null) {
                    if (chosenDirector.get("name") != null && !chosenDirector.get("name").toString().trim().isEmpty()) {
                        doc.setNomineeName(chosenDirector.get("name").toString().trim());
                    }
                    if (chosenDirector.get("address") != null && !chosenDirector.get("address").toString().trim().isEmpty()) {
                        doc.setNomineeAddress(chosenDirector.get("address").toString().trim());
                    }
                    if (chosenDirector.get("idNumber") != null && !chosenDirector.get("idNumber").toString().trim().isEmpty()) {
                        doc.setNomineeIdNumber(chosenDirector.get("idNumber").toString().trim());
                    }
                    if (chosenDirector.get("nationality") != null && !chosenDirector.get("nationality").toString().trim().isEmpty()) {
                        doc.setNomineeNationality(chosenDirector.get("nationality").toString().trim());
                    }
                    if (chosenDirector.get("email") != null && !chosenDirector.get("email").toString().trim().isEmpty()) {
                        doc.setNomineeEmail(chosenDirector.get("email").toString().trim());
                    }
                    if (chosenDirector.get("mobile") != null && !chosenDirector.get("mobile").toString().trim().isEmpty()) {
                        doc.setNomineePhone(chosenDirector.get("mobile").toString().trim());
                    } else if (chosenDirector.get("phone") != null && !chosenDirector.get("phone").toString().trim().isEmpty()) {
                        doc.setNomineePhone(chosenDirector.get("phone").toString().trim());
                    }
                    if (chosenDirector.get("dob") != null && !chosenDirector.get("dob").toString().trim().isEmpty()) {
                        doc.setNomineeDob(chosenDirector.get("dob").toString().trim());
                    }
                }

                if (chosenActiveDir != null && chosenActiveDir.get("name") != null) {
                    doc.setActiveDirectorName(chosenActiveDir.get("name").toString().trim());
                    if (chosenActiveDir.get("idNumber") != null) {
                        doc.setActiveDirectorIdNumber(chosenActiveDir.get("idNumber").toString().trim());
                    }
                    doc.setSecondDirectorName(doc.getNomineeName());
                    doc.setSecondDirectorIdNumber(doc.getNomineeIdNumber());
                } else {
                    doc.setActiveDirectorName(doc.getNomineeName());
                    doc.setActiveDirectorIdNumber(doc.getNomineeIdNumber());
                    doc.setSecondDirectorName(doc.getNomineeName());
                    doc.setSecondDirectorIdNumber(doc.getNomineeIdNumber());
                }

                // Check shareholders for Nominator
                List<?> memberList = excel.get("members") instanceof List ? (List<?>) excel.get("members") : Collections.emptyList();
                if (!memberList.isEmpty() && memberList.get(0) instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) memberList.get(0);
                    if (m.get("name") != null) doc.setNominatorName(m.get("name").toString().trim());
                    if (m.get("address") != null) doc.setNominatorAddress(m.get("address").toString().trim());
                    if (m.get("nationality") != null) doc.setNominatorNationality(m.get("nationality").toString().trim());
                    if (m.get("idNumber") != null) doc.setNominatorIdNumber(m.get("idNumber").toString().trim());
                } else {
                    doc.setNominatorName(doc.getCompanyName());
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
        NomineeAppointmentDocumentData doc = getDocumentData(docId);
        if (doc == null) {
            doc = new NomineeAppointmentDocumentData();
            doc.setId(docId != null ? docId : UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }

        if (updates.containsKey("documentType") && updates.get("documentType") != null) {
            doc.setDocumentType(updates.get("documentType").toString());
        }
        if (updates.containsKey("companyName") && updates.get("companyName") != null) {
            doc.setCompanyName(updates.get("companyName").toString());
        }
        if (updates.containsKey("companyAddress") && updates.get("companyAddress") != null) {
            doc.setCompanyAddress(updates.get("companyAddress").toString());
        }
        if (updates.containsKey("newAddress") && updates.get("newAddress") != null) {
            doc.setNewAddress(updates.get("newAddress").toString());
        }
        if (updates.containsKey("uen") && updates.get("uen") != null) {
            doc.setUen(updates.get("uen").toString());
        }
        if (updates.containsKey("effectiveDate") && updates.get("effectiveDate") != null) {
            doc.setEffectiveDate(updates.get("effectiveDate").toString());
        }
        if (updates.containsKey("resolutionDate") && updates.get("resolutionDate") != null) {
            doc.setResolutionDate(updates.get("resolutionDate").toString());
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
        if (updates.containsKey("activeDirectorName") && updates.get("activeDirectorName") != null) {
            doc.setActiveDirectorName(updates.get("activeDirectorName").toString());
        }
        if (updates.containsKey("activeDirectorIdNumber") && updates.get("activeDirectorIdNumber") != null) {
            doc.setActiveDirectorIdNumber(updates.get("activeDirectorIdNumber").toString());
        }
        if (updates.containsKey("secondDirectorName") && updates.get("secondDirectorName") != null) {
            doc.setSecondDirectorName(updates.get("secondDirectorName").toString());
        }
        if (updates.containsKey("secondDirectorIdNumber") && updates.get("secondDirectorIdNumber") != null) {
            doc.setSecondDirectorIdNumber(updates.get("secondDirectorIdNumber").toString());
        }
        if (updates.containsKey("nominatorName") && updates.get("nominatorName") != null) {
            doc.setNominatorName(updates.get("nominatorName").toString());
        }
        if (updates.containsKey("nominatorAddress") && updates.get("nominatorAddress") != null) {
            doc.setNominatorAddress(updates.get("nominatorAddress").toString());
        }
        if (updates.containsKey("nominatorNationality") && updates.get("nominatorNationality") != null) {
            doc.setNominatorNationality(updates.get("nominatorNationality").toString());
        }
        if (updates.containsKey("nominatorIdNumber") && updates.get("nominatorIdNumber") != null) {
            doc.setNominatorIdNumber(updates.get("nominatorIdNumber").toString());
        }
        if (updates.containsKey("nominatorDob") && updates.get("nominatorDob") != null) {
            doc.setNominatorDob(updates.get("nominatorDob").toString());
        }
        if (updates.containsKey("nominatorEmail") && updates.get("nominatorEmail") != null) {
            doc.setNominatorEmail(updates.get("nominatorEmail").toString());
        }
        if (updates.containsKey("nominatorPhone") && updates.get("nominatorPhone") != null) {
            doc.setNominatorPhone(updates.get("nominatorPhone").toString());
        }
        if (updates.containsKey("dateOfBr") && updates.get("dateOfBr") != null) {
            doc.setDateOfBr(updates.get("dateOfBr").toString());
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
        String docType = doc.getDocumentType() != null ? doc.getDocumentType().toLowerCase() : "nominee_director";
        List<String> templatePaths = new ArrayList<>();

        if ("change_of_address".equals(docType)) {
            templatePaths.add("change-of-address/DRIW - Change of address.docx");
        } else if ("director".equals(docType)) {
            templatePaths.add("appointment-of-director/DRIW - Appointment of Director.docx");
            templatePaths.add("appointment-of-director/Form 45 -Consent to Act as a Director.docx");
        } else {
            templatePaths.add("appointment-of-nominee-director/DRIW - Appointment of Director.docx");
            templatePaths.add("appointment-of-nominee-director/Form 45 -Consent to Act as a Director.docx");
            templatePaths.add("appointment-of-nominee-director/Nominee Director Letter.docx");
        }

        List<String> processedXmls = new ArrayList<>();
        Map<String, byte[]> baseOtherFiles = new HashMap<>();

        String companyClean = doc.getCompanyName() != null ? doc.getCompanyName().trim() : "ABBEY HOLDINGS PTE. LTD.";
        String companyBase = companyClean.replaceAll("(?i)\\s*PTE\\.?\\s*LTD\\.?", "").trim();
        String uen = doc.getUen() != null ? doc.getUen().trim() : "201601260K";
        String companyAddress = doc.getCompanyAddress() != null ? doc.getCompanyAddress().trim() : "10 MARINA BOULEVARD, SINGAPORE 018983";
        String newAddress = doc.getNewAddress() != null && !doc.getNewAddress().trim().isEmpty() ? doc.getNewAddress().trim() : companyAddress;
        String directorName = doc.getNomineeName() != null ? doc.getNomineeName().trim() : "TANGATURU SUBRAMANIAN ANNAPOORANA";
        String directorAddress = doc.getNomineeAddress() != null ? doc.getNomineeAddress().trim() : "234 #02-494,COMPASSVALE WALK ,SENGKANG ,SINGAPORE 540234";
        String directorId = doc.getNomineeIdNumber() != null ? doc.getNomineeIdNumber().trim() : "S8061258C";
        String directorNat = doc.getNomineeNationality() != null ? doc.getNomineeNationality().trim() : "INDIAN";
        String directorEmail = doc.getNomineeEmail() != null ? doc.getNomineeEmail().trim() : "anu@globalisor.com";
        String directorPhone = doc.getNomineePhone() != null ? doc.getNomineePhone().trim() : "+65 81753514";
        String directorDob = doc.getNomineeDob() != null ? doc.getNomineeDob().trim() : "25/08/1980";
        String activeDirName = doc.getActiveDirectorName() != null ? doc.getActiveDirectorName().trim() : directorName;
        String secondDirName = doc.getSecondDirectorName() != null ? doc.getSecondDirectorName().trim() : directorName;
        String nominatorName = doc.getNominatorName() != null ? doc.getNominatorName().trim() : companyClean;
        String nominatorAddress = doc.getNominatorAddress() != null ? doc.getNominatorAddress().trim() : companyAddress;
        String nominatorNat = doc.getNominatorNationality() != null ? doc.getNominatorNationality().trim() : "SINGAPOREAN";
        String nominatorId = doc.getNominatorIdNumber() != null ? doc.getNominatorIdNumber().trim() : uen;
        String nominatorDob = doc.getNominatorDob() != null ? doc.getNominatorDob().trim() : "26/01/2016";
        String nominatorEmail = doc.getNominatorEmail() != null ? doc.getNominatorEmail().trim() : "compliance@globalisor.com";
        String nominatorPhone = doc.getNominatorPhone() != null ? doc.getNominatorPhone().trim() : "+65 67891234";
        String datedDay = doc.getDatedDay() != null ? doc.getDatedDay().trim() : "9th";
        String datedMonthYear = doc.getDatedMonthYear() != null ? doc.getDatedMonthYear().trim() : "August 2026";
        String dateStr = datedDay + " " + datedMonthYear;
        String effectiveDate = doc.getEffectiveDate() != null ? doc.getEffectiveDate().trim() : "the date of Incorporation";

        for (int i = 0; i < templatePaths.size(); i++) {
            String path = templatePaths.get(i);
            ClassPathResource resource = new ClassPathResource(path);
            byte[] bytes;
            try (InputStream is = resource.getInputStream()) {
                bytes = is.readAllBytes();
            }

            String docXml = "";
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    byte[] buf = zis.readAllBytes();
                    if ("word/document.xml".equals(entry.getName())) {
                        docXml = new String(buf, StandardCharsets.UTF_8);
                    } else if (i == 0) {
                        baseOtherFiles.put(entry.getName(), buf);
                    }
                }
            }

            // Perform token replacements based on template type
            if (path.contains("Change of address") || path.contains("change-of-address")) {
                docXml = docXml.replace("&lt;Company Name&gt;", escapeXml(companyClean));
                docXml = docXml.replace("<Company Name>", escapeXml(companyClean));
                docXml = docXml.replace("&lt;UEN&gt;", escapeXml(uen));
                docXml = docXml.replace("<UEN>", escapeXml(uen));
                docXml = docXml.replace("&lt;NEW ADDRESS to be taken from the address proof &gt;", escapeXml(newAddress));
                docXml = docXml.replace("&lt;NEW ADDRESS to be taken from the address proof >", escapeXml(newAddress));
                docXml = docXml.replace("<NEW ADDRESS to be taken from the address proof >", escapeXml(newAddress));
                docXml = docXml.replace("&lt;Date&gt;", escapeXml(dateStr));
                docXml = docXml.replace("<Date>", escapeXml(dateStr));
                docXml = docXml.replace("&lt;Director&gt;", escapeXml(activeDirName));
                docXml = docXml.replace("<Director>", escapeXml(activeDirName));
                docXml = docXml.replace("&lt;director&gt;", escapeXml(secondDirName));
                docXml = docXml.replace("<director>", escapeXml(secondDirName));
            } else if (path.contains("DRIW")) {
                docXml = docXml.replace("&lt;Director name&gt;", escapeXml(directorName));
                docXml = docXml.replace("&lt;ID NO&gt;", escapeXml(directorId));
                docXml = docXml.replace("&lt;DATE&gt;", escapeXml(dateStr));
                docXml = docXml.replace("&lt;Active DIRECTOR Name &gt;", escapeXml(activeDirName));
                docXml = docXml.replace("&lt;New Director Name &gt;", escapeXml(directorName));
            } else if (path.contains("Form 45")) {
                docXml = docXml.replace("<w:t>***</w:t>", "<w:t>" + escapeXml(companyBase) + "</w:t>");
                docXml = docXml.replace("***", escapeXml(companyBase));
                docXml = docXml.replace("Company No:", "Company No: " + escapeXml(uen));
                docXml = docXml.replace("Company No :", "Company No : " + escapeXml(uen));
                docXml = docXml.replace("&lt;As per passport ,Surname First followed by Given Name&gt;", escapeXml(directorName));
                docXml = docXml.replace("&lt;As per NRIC&gt;", escapeXml(directorName));
                docXml = docXml.replace("&lt;as per address proof &gt;", escapeXml(directorAddress));
                docXml = docXml.replace("&lt;from Id&gt;", escapeXml(directorId));
                docXml = docXml.replace("&lt;from passport&gt;", escapeXml(directorNat));
                docXml = docXml.replace("&lt;from NRIC&gt;", escapeXml(directorNat));
                docXml = docXml.replace("&lt;email id&gt;", escapeXml(directorEmail));
                docXml = docXml.replace("&lt;+country code  mobile&gt;", escapeXml(directorPhone));
                docXml = docXml.replace("  day of", " " + escapeXml(datedDay) + " day of " + escapeXml(datedMonthYear));
                docXml = docXml.replace("the date of Incorporation", escapeXml(effectiveDate));
                if (doc.getWitnessName() != null && !doc.getWitnessName().isEmpty()) {
                    docXml = docXml.replace("Name:", "Name: " + escapeXml(doc.getWitnessName()));
                }
                if (doc.getWitnessAddress() != null && !doc.getWitnessAddress().isEmpty()) {
                    docXml = docXml.replace("Address:", "Address: " + escapeXml(doc.getWitnessAddress()));
                }
            } else if (path.contains("Nominee Director Letter")) {
                docXml = docXml.replace("&lt;nominee Director name from NRIC&gt;", escapeXml(directorName));
                docXml = docXml.replace("&lt;nominee director name &gt;", escapeXml(directorName));
                docXml = docXml.replace("&lt;Address from Address proof&gt;", escapeXml(directorAddress));
                docXml = docXml.replace("&lt;company name &gt;", escapeXml(companyClean));
                docXml = docXml.replace("&lt;company address -Ro Address from address tab&gt;", escapeXml(companyAddress));
                docXml = docXml.replace("&lt;address 2&gt;", "");
                docXml = docXml.replace("&lt;date of BR&gt;", escapeXml(doc.getDateOfBr() != null && !doc.getDateOfBr().isEmpty() ? doc.getDateOfBr() : dateStr));
                docXml = docXml.replace("&lt;Nominator name&gt;", escapeXml(nominatorName));
                docXml = docXml.replace("&lt;nominator name &gt;", escapeXml(nominatorName));
                docXml = docXml.replace("&lt;Address proof&gt;", escapeXml(nominatorAddress));
                docXml = docXml.replace("&lt;Nationality?", escapeXml(nominatorNat));
                docXml = docXml.replace("&lt;Nationality&gt;", escapeXml(nominatorNat));
                docXml = docXml.replace("&lt;id no&gt;", escapeXml(nominatorId));
                docXml = docXml.replace("&lt;DOB&gt;", escapeXml(nominatorDob));
                docXml = docXml.replace("EMAIL ID", escapeXml(nominatorEmail));
                docXml = docXml.replace("&lt;CONTACT&gt;", escapeXml(nominatorPhone));
            }

            processedXmls.add(docXml);
        }

        // Merge XML bodies into a single document.xml with page breaks
        String combinedXml = mergeXmlDocuments(processedXmls);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : baseOtherFiles.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(combinedXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    private String mergeXmlDocuments(List<String> xmlList) {
        if (xmlList == null || xmlList.isEmpty()) return "";
        if (xmlList.size() == 1) return xmlList.get(0);

        String firstXml = xmlList.get(0);
        int bodyStart = firstXml.indexOf("<w:body>");
        if (bodyStart == -1) bodyStart = firstXml.indexOf("<w:body ");
        int bodyEnd = firstXml.lastIndexOf("</w:body>");

        if (bodyStart == -1 || bodyEnd == -1) return firstXml;

        String prefix = firstXml.substring(0, bodyStart + "<w:body>".length());
        String firstBodyContent = firstXml.substring(bodyStart + "<w:body>".length(), bodyEnd);
        
        // Find sectPr in first body content
        int sectPrIdx = firstBodyContent.lastIndexOf("<w:sectPr");
        String firstSectPr = "";
        String firstCoreBody = firstBodyContent;
        if (sectPrIdx != -1) {
            firstCoreBody = firstBodyContent.substring(0, sectPrIdx);
            firstSectPr = firstBodyContent.substring(sectPrIdx);
        }

        StringBuilder combinedBody = new StringBuilder();
        combinedBody.append(firstCoreBody);

        String pageBreak = "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>";

        for (int i = 1; i < xmlList.size(); i++) {
            String nextXml = xmlList.get(i);
            int nStart = nextXml.indexOf("<w:body>");
            if (nStart == -1) nStart = nextXml.indexOf("<w:body ");
            int nEnd = nextXml.lastIndexOf("</w:body>");

            if (nStart != -1 && nEnd != -1) {
                int contentStart = nextXml.indexOf(">", nStart) + 1;
                String nextBody = nextXml.substring(contentStart, nEnd);
                int nSectPr = nextBody.lastIndexOf("<w:sectPr");
                if (nSectPr != -1) {
                    nextBody = nextBody.substring(0, nSectPr);
                }
                combinedBody.append(pageBreak);
                combinedBody.append(nextBody);
            }
        }

        combinedBody.append(firstSectPr);
        combinedBody.append("</w:body></w:document>");

        return prefix + combinedBody.toString();
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
