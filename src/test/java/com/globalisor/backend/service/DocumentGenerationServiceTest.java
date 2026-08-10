package com.globalisor.backend.service;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.service.DocumentGenerationService.NomineeAppointmentDocumentData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentGenerationServiceTest {

    private DocumentGenerationService service;

    @BeforeEach
    public void setup() {
        service = new DocumentGenerationService();
    }

    @Test
    public void testCreateDocumentDataFromRequirementNominee() {
        Requirement req = new Requirement();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> excel = new HashMap<>();
        excel.put("companyName", "ABBEY HOLDINGS PTE. LTD.");
        excel.put("uen", "201601260K");
        excel.put("incorporationDate", "2016-01-26");

        List<Map<String, Object>> dirs = new ArrayList<>();
        Map<String, Object> dir = new HashMap<>();
        dir.put("name", "TANGATURU SUBRAMANIAN ANNAPOORANA");
        dir.put("type", "Nominee Director");
        dir.put("isNominee", true);
        dir.put("idNumber", "S8061258C");
        dir.put("nationality", "INDIAN");
        dir.put("email", "anu@globalisor.com");
        dir.put("mobile", "+65 81753514");
        dir.put("address", "234 #02-494,COMPASSVALE WALK ,SENGKANG ,SINGAPORE 540234");
        dir.put("dob", "25/08/1980");
        dirs.add(dir);
        excel.put("directors", dirs);

        data.put("excelData", excel);
        req.setData(data);

        NomineeAppointmentDocumentData doc = service.createDocumentDataFromRequirement(req, "nominee director");

        assertNotNull(doc);
        assertNotNull(doc.getId());
        assertEquals("nominee_director", doc.getDocumentType());
        assertEquals("ABBEY HOLDINGS PTE. LTD.", doc.getCompanyName());
        assertEquals("201601260K", doc.getUen());
        assertEquals("TANGATURU SUBRAMANIAN ANNAPOORANA", doc.getNomineeName());
        assertEquals("S8061258C", doc.getNomineeIdNumber());
        assertEquals("INDIAN", doc.getNomineeNationality());
    }

    @Test
    public void testCreateDocumentDataFromRequirementDirector() {
        Requirement req = new Requirement();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> excel = new HashMap<>();
        excel.put("companyName", "3B TRADING PTE. LTD.");
        excel.put("uen", "202012345A");

        List<Map<String, Object>> dirs = new ArrayList<>();
        Map<String, Object> dir = new HashMap<>();
        dir.put("name", "MICHAEL CHEN");
        dir.put("type", "Director");
        dir.put("isNominee", false);
        dir.put("idNumber", "S9876543A");
        dir.put("nationality", "SINGAPOREAN");
        dirs.add(dir);
        excel.put("directors", dirs);

        data.put("excelData", excel);
        req.setData(data);

        NomineeAppointmentDocumentData doc = service.createDocumentDataFromRequirement(req, "director", "director");

        assertNotNull(doc);
        assertEquals("director", doc.getDocumentType());
        assertEquals("3B TRADING PTE. LTD.", doc.getCompanyName());
        assertEquals("202012345A", doc.getUen());
        assertEquals("MICHAEL CHEN", doc.getNomineeName());
    }

    @Test
    public void testUpdateDocumentData() {
        NomineeAppointmentDocumentData initial = service.createDocumentDataFromRequirement(null, "default");
        String docId = initial.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("companyName", "3B TRADING PTE. LTD.");
        updates.put("uen", "202012345A");
        updates.put("nomineeName", "JOHN DOE");
        updates.put("nomineeIdNumber", "T1234567A");

        NomineeAppointmentDocumentData updated = service.updateDocumentData(docId, updates);

        assertNotNull(updated);
        assertEquals("3B TRADING PTE. LTD.", updated.getCompanyName());
        assertEquals("202012345A", updated.getUen());
        assertEquals("JOHN DOE", updated.getNomineeName());
        assertEquals("T1234567A", updated.getNomineeIdNumber());

        NomineeAppointmentDocumentData fetched = service.getDocumentData(docId);
        assertEquals("3B TRADING PTE. LTD.", fetched.getCompanyName());
    }

    @Test
    public void testGenerateDocxBytesNomineeDirector() throws Exception {
        NomineeAppointmentDocumentData doc = new NomineeAppointmentDocumentData();
        doc.setDocumentType("nominee_director");
        doc.setCompanyName("GLOBAL INNOVATIONS PTE. LTD.");
        doc.setUen("202499887Z");
        doc.setNomineeName("ALEX TAN");
        doc.setNomineeIdNumber("S9988776A");

        byte[] docxBytes = service.generateDocxBytes(doc);
        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 0);

        // Verify that word/document.xml inside generated zip contains replaced tokens
        boolean foundDocXml = false;
        String xmlContent = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    foundDocXml = true;
                    xmlContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        assertTrue(foundDocXml, "word/document.xml must exist in generated docx");
        assertTrue(xmlContent.contains("GLOBAL INNOVATIONS"), "Should contain replaced company name");
        assertTrue(xmlContent.contains("202499887Z"), "Should contain replaced UEN");
        assertTrue(xmlContent.contains("ALEX TAN"), "Should contain replaced Nominee name");
        assertTrue(xmlContent.contains("S9988776A"), "Should contain replaced Nominee ID");
    }

    @Test
    public void testGenerateDocxBytesDirector() throws Exception {
        NomineeAppointmentDocumentData doc = new NomineeAppointmentDocumentData();
        doc.setDocumentType("director");
        doc.setCompanyName("APEX VENTURES PTE. LTD.");
        doc.setUen("202511223K");
        doc.setNomineeName("SARAH LEE");
        doc.setNomineeIdNumber("T1122334A");

        byte[] docxBytes = service.generateDocxBytes(doc);
        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 0);

        boolean foundDocXml = false;
        String xmlContent = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    foundDocXml = true;
                    xmlContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        assertTrue(foundDocXml, "word/document.xml must exist in generated docx");
        assertTrue(xmlContent.contains("APEX VENTURES"), "Should contain replaced company name");
        assertTrue(xmlContent.contains("202511223K"), "Should contain replaced UEN");
        assertTrue(xmlContent.contains("SARAH LEE"), "Should contain replaced Director name");
    }

    @Test
    public void testCreateDocumentDataFromRequirementChangeOfAddress() {
        Requirement req = new Requirement();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> excel = new HashMap<>();
        excel.put("companyName", "3B TRADING PTE. LTD.");
        excel.put("uen", "202012345A");
        excel.put("address", "1 RAFFLES PLACE, #20-00, SINGAPORE 048616");

        data.put("excelData", excel);
        req.setData(data);

        NomineeAppointmentDocumentData doc = service.createDocumentDataFromRequirement(req, "give me document of change of address");

        assertNotNull(doc);
        assertEquals("change_of_address", doc.getDocumentType());
        assertEquals("3B TRADING PTE. LTD.", doc.getCompanyName());
        assertEquals("202012345A", doc.getUen());
        assertEquals("1 RAFFLES PLACE, #20-00, SINGAPORE 048616", doc.getNewAddress());
    }

    @Test
    public void testGenerateDocxBytesChangeOfAddress() throws Exception {
        NomineeAppointmentDocumentData doc = new NomineeAppointmentDocumentData();
        doc.setDocumentType("change_of_address");
        doc.setCompanyName("3B TRADING PTE. LTD.");
        doc.setUen("202012345A");
        doc.setNewAddress("8 MARINA VIEW, #15-01 ASIA SQUARE TOWER 1, SINGAPORE 018960");
        doc.setActiveDirectorName("MICHAEL CHEN");
        doc.setSecondDirectorName("SARAH LEE");

        byte[] docxBytes = service.generateDocxBytes(doc);
        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 0);

        boolean foundDocXml = false;
        String xmlContent = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    foundDocXml = true;
                    xmlContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        assertTrue(foundDocXml, "word/document.xml must exist in generated docx");
        assertTrue(xmlContent.contains("CHANGE OF REGISTERED OFFICE ADDRESS"), "Should contain change of address heading");
        assertTrue(xmlContent.contains("8 MARINA VIEW"), "Should contain replaced new address");
        assertTrue(xmlContent.contains("MICHAEL CHEN"), "Should contain replaced director 1 name");
        assertTrue(xmlContent.contains("SARAH LEE"), "Should contain replaced director 2 name");
    }
}

