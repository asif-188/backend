package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.repository.ClientDocumentRepository;
import com.globalisor.backend.repository.OnboardingRepository;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.service.DocumentGenerationService;
import com.globalisor.backend.service.DocumentGenerationService.NomineeAppointmentDocumentData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
public class BusinessIntelligenceControllerDocTest {

    @Mock
    private ClientDocumentRepository clientDocumentRepository;

    @Mock
    private OnboardingRepository onboardingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RequirementRepository requirementRepository;

    @Spy
    private DocumentGenerationService documentGenerationService = new DocumentGenerationService();

    @InjectMocks
    private BusinessIntelligenceController controller;

    @Test
    public void testQueryAppointmentDocument() {
        Requirement req = new Requirement();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> excel = new HashMap<>();
        excel.put("companyName", "ABBEY HOLDINGS PTE. LTD.");
        excel.put("uen", "201601260K");
        data.put("excelData", excel);
        req.setData(data);

        Mockito.when(requirementRepository.findAll()).thenReturn(List.of(req));
        Mockito.when(userRepository.findAll()).thenReturn(Collections.emptyList());
        Mockito.when(clientDocumentRepository.findAll()).thenReturn(Collections.emptyList());

        String query = "give me a document of appointment of nominee director for Abbey Holdings";
        ResponseEntity<Map<String, Object>> response = controller.queryBusinessIntelligence(query);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("nominee_director_appointment_document", body.get("type"));
        assertNotNull(body.get("docId"));
        assertNotNull(body.get("viewUrl"));
        assertNotNull(body.get("downloadUrl"));
        assertTrue(body.get("reply").toString().contains("Nominee Director Appointment Document (Form 45) Prepared"));
        assertTrue(body.get("reply").toString().contains("ABBEY HOLDINGS"));
    }

    @Test
    public void testDocumentEndpoints() {
        NomineeAppointmentDocumentData initialDoc = documentGenerationService.createDocumentDataFromRequirement(null, "test");
        String docId = initialDoc.getId();

        // 1. GET /data
        ResponseEntity<?> dataRes = controller.getDocumentData(docId);
        assertNotNull(dataRes);
        assertEquals(200, dataRes.getStatusCode().value());

        // 2. POST /update
        Map<String, Object> updates = new HashMap<>();
        updates.put("companyName", "NEW CORP PTE. LTD.");
        updates.put("uen", "2026112233");
        ResponseEntity<?> updateRes = controller.updateDocumentData(docId, updates);
        assertNotNull(updateRes);
        assertEquals(200, updateRes.getStatusCode().value());

        // 3. GET /download
        ResponseEntity<byte[]> downloadRes = controller.downloadDocument(docId);
        assertNotNull(downloadRes);
        assertEquals(200, downloadRes.getStatusCode().value());
        assertNotNull(downloadRes.getBody());
        assertTrue(downloadRes.getBody().length > 0);
        assertTrue(downloadRes.getHeaders().getContentDisposition().toString().contains("Nominee-Director-Appointment"));
    }
}
