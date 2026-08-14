package com.globalisor.backend.controller;

import com.globalisor.backend.model.ChatThread;
import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.repository.ChatThreadRepository;
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

    @Mock
    private ChatThreadRepository chatThreadRepository;

    @Spy
    private DocumentGenerationService documentGenerationService = new DocumentGenerationService();

    @InjectMocks
    private BusinessIntelligenceController controller;

    @Test
    public void testThreadBasedMemoryAndContextRetention() {
        Requirement reqAbbey = new Requirement();
        Map<String, Object> dataAbbey = new HashMap<>();
        Map<String, Object> excelAbbey = new HashMap<>();
        excelAbbey.put("companyName", "ABBEY HOLDINGS PTE. LTD.");
        excelAbbey.put("uen", "201601260K");
        List<Map<String, Object>> abbeyDirs = List.of(
                Map.of("name", "TANGATURU SUBRAMANIAN", "type", "Director", "appointmentDate", "2016-01-26")
        );
        excelAbbey.put("directors", abbeyDirs);
        dataAbbey.put("excelData", excelAbbey);
        reqAbbey.setData(dataAbbey);

        Requirement req3B = new Requirement();
        Map<String, Object> data3B = new HashMap<>();
        Map<String, Object> excel3B = new HashMap<>();
        excel3B.put("companyName", "3B TRADING & CONSULTING PTE. LTD.");
        excel3B.put("uen", "202012345A");
        List<Map<String, Object>> tradingDirs = List.of(
                Map.of("name", "JOHN DOE", "type", "Director", "appointmentDate", "2020-05-15")
        );
        excel3B.put("directors", tradingDirs);
        data3B.put("excelData", excel3B);
        req3B.setData(data3B);

        Mockito.when(requirementRepository.findAll()).thenReturn(List.of(reqAbbey, req3B));
        Mockito.when(userRepository.findAll()).thenReturn(Collections.emptyList());
        Mockito.when(clientDocumentRepository.findAll()).thenReturn(Collections.emptyList());

        ChatThread mockThread = new ChatThread();
        mockThread.setId("th_test123");
        mockThread.setUserId("admin");
        mockThread.setTitle("Test Conversation");
        Mockito.when(chatThreadRepository.findById("th_test123")).thenReturn(Optional.of(mockThread));

        // 1. First question: Explicitly mentions "Abbey Holdings"
        ResponseEntity<Map<String, Object>> q1Res = controller.queryBusinessIntelligence(
                "Tell me about Abbey Holdings", null, "th_test123", "admin");

        assertNotNull(q1Res);
        Map<String, Object> q1Body = q1Res.getBody();
        assertNotNull(q1Body);
        assertEquals("ABBEY HOLDINGS PTE. LTD.", q1Body.get("activeCompany"));
        assertEquals("th_test123", q1Body.get("threadId"));

        // 2. Second question: User does NOT mention any company name ("Who is the director?")
        // Memory from thread must be used!
        ResponseEntity<Map<String, Object>> q2Res = controller.queryBusinessIntelligence(
                "Who is the director?", null, "th_test123", "admin");

        assertNotNull(q2Res);
        Map<String, Object> q2Body = q2Res.getBody();
        assertNotNull(q2Body);
        assertEquals("ABBEY HOLDINGS PTE. LTD.", q2Body.get("activeCompany"));
        assertEquals("director_summary", q2Body.get("type"));
        assertTrue(q2Body.get("reply").toString().contains("TANGATURU SUBRAMANIAN"));

        // 3. Third question: Request nominee director appointment document for the remembered company
        ResponseEntity<Map<String, Object>> q3Res = controller.queryBusinessIntelligence(
                "nominee director", null, "th_test123", "admin");

        assertNotNull(q3Res);
        Map<String, Object> q3Body = q3Res.getBody();
        assertNotNull(q3Body);
        assertEquals("ABBEY HOLDINGS PTE. LTD.", q3Body.get("activeCompany"));
        assertEquals("nominee_director_appointment_document", q3Body.get("type"));
        assertNotNull(q3Body.get("docId"));
        assertTrue(q3Body.get("viewUrl").toString().contains("/admin/document-viewer.html?docId="));

        // 4. Fourth question: Switch context explicitly to 3B Trading
        ResponseEntity<Map<String, Object>> q4Res = controller.queryBusinessIntelligence(
                "Tell me about 3B Trading", null, "th_test123", "admin");

        assertNotNull(q4Res);
        Map<String, Object> q4Body = q4Res.getBody();
        assertNotNull(q4Body);
        assertEquals("3B TRADING & CONSULTING PTE. LTD.", q4Body.get("activeCompany"));

        // 5. Fifth question: Ask follow-up question without company name -> must resolve to 3B Trading
        ResponseEntity<Map<String, Object>> q5Res = controller.queryBusinessIntelligence(
                "Who is the director?", null, "th_test123", "admin");

        assertNotNull(q5Res);
        Map<String, Object> q5Body = q5Res.getBody();
        assertNotNull(q5Body);
        assertEquals("3B TRADING & CONSULTING PTE. LTD.", q5Body.get("activeCompany"));
        assertTrue(q5Body.get("reply").toString().contains("JOHN DOE"));
    }

    @Test
    public void testQueryAppointmentClarification() {
        Requirement reqAbbey = new Requirement();
        Map<String, Object> dataAbbey = new HashMap<>();
        Map<String, Object> excelAbbey = new HashMap<>();
        excelAbbey.put("companyName", "ABBEY HOLDINGS PTE. LTD.");
        excelAbbey.put("uen", "201601260K");
        dataAbbey.put("excelData", excelAbbey);
        reqAbbey.setData(dataAbbey);

        Requirement req3B = new Requirement();
        Map<String, Object> data3B = new HashMap<>();
        Map<String, Object> excel3B = new HashMap<>();
        excel3B.put("companyName", "3B TRADING & CONSULTING PTE. LTD.");
        excel3B.put("uen", "202012345A");
        data3B.put("excelData", excel3B);
        req3B.setData(data3B);

        Mockito.when(requirementRepository.findAll()).thenReturn(List.of(reqAbbey, req3B));
        Mockito.when(userRepository.findAll()).thenReturn(Collections.emptyList());
        Mockito.when(clientDocumentRepository.findAll()).thenReturn(Collections.emptyList());

        // 1. Clarification with 3B Trading in query
        String query = "give me document of nominiee director 3B trading";
        ResponseEntity<Map<String, Object>> response = controller.queryBusinessIntelligence(query, null, null, "admin");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("appointment_clarification", body.get("type"));
        assertTrue(body.get("reply").toString().contains("Director or nominee director?"));
        assertTrue(body.get("reply").toString().contains("3B TRADING & CONSULTING"));
        assertEquals("3B TRADING & CONSULTING PTE. LTD.", body.get("companyName"));

        // 2. User subsequently selects "Director" with company context preserved
        ResponseEntity<Map<String, Object>> dirResponse = controller.queryBusinessIntelligence("director", "3B TRADING & CONSULTING PTE. LTD.", null, "admin");
        assertNotNull(dirResponse);
        Map<String, Object> dirBody = dirResponse.getBody();
        assertNotNull(dirBody);
        assertEquals("director_appointment_document", dirBody.get("type"));
        assertTrue(dirBody.get("reply").toString().contains("3B TRADING & CONSULTING"));
    }

    @Test
    public void testQueryDirectDirectorSelection() {
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

        String query = "director";
        ResponseEntity<Map<String, Object>> response = controller.queryBusinessIntelligence(query, null, null, "admin");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("director_appointment_document", body.get("type"));
        assertEquals("director", body.get("documentType"));
        assertEquals(2, body.get("docCount"));
        assertNotNull(body.get("docId"));
        assertTrue(body.get("viewUrl").toString().contains("type=director"));
        assertTrue(body.get("downloadUrl").toString().contains("type=director"));
        assertTrue(body.get("reply").toString().contains("Director Appointment Document Package Prepared (2 Documents)"));
    }

    @Test
    public void testQueryDirectNomineeSelection() {
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

        String query = "nominee director";
        ResponseEntity<Map<String, Object>> response = controller.queryBusinessIntelligence(query, null, null, "admin");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("nominee_director_appointment_document", body.get("type"));
        assertEquals("nominee_director", body.get("documentType"));
        assertEquals(3, body.get("docCount"));
        assertNotNull(body.get("docId"));
        assertTrue(body.get("viewUrl").toString().contains("type=nominee_director"));
        assertTrue(body.get("downloadUrl").toString().contains("type=nominee_director"));
        assertTrue(body.get("reply").toString().contains("Nominee Director Appointment Document Package Prepared (3 Documents)"));
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
        ResponseEntity<byte[]> downloadRes = controller.downloadDocument(docId, "nominee_director");
        assertNotNull(downloadRes);
        assertEquals(200, downloadRes.getStatusCode().value());
        assertNotNull(downloadRes.getBody());
        assertTrue(downloadRes.getBody().length > 0);
        assertTrue(downloadRes.getHeaders().getContentDisposition().toString().contains("Nominee-Director-Appointment"));
    }

    @Test
    public void testQueryChangeOfAddressDocument() {
        Requirement req = new Requirement();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> excel = new HashMap<>();
        excel.put("companyName", "3B TRADING & CONSULTING PTE. LTD.");
        excel.put("uen", "202012345A");
        excel.put("address", "1 RAFFLES PLACE, #20-00, SINGAPORE 048616");
        data.put("excelData", excel);
        req.setData(data);

        Mockito.when(requirementRepository.findAll()).thenReturn(List.of(req));
        Mockito.when(userRepository.findAll()).thenReturn(Collections.emptyList());
        Mockito.when(clientDocumentRepository.findAll()).thenReturn(Collections.emptyList());

        String query = "give me document of change of address for 3B Trading";
        ResponseEntity<Map<String, Object>> response = controller.queryBusinessIntelligence(query, null, null, "admin");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("change_of_address_document", body.get("type"));
        assertEquals("change_of_address", body.get("documentType"));
        assertEquals(1, body.get("docCount"));
        assertNotNull(body.get("docId"));
        assertTrue(body.get("viewUrl").toString().contains("type=change_of_address"));
        assertTrue(body.get("downloadUrl").toString().contains("type=change_of_address"));
        assertTrue(body.get("reply").toString().contains("Change of Registered Office Address Resolution (DRIW) Prepared"));
        assertTrue(body.get("reply").toString().contains("3B TRADING & CONSULTING"));

        // Test downloading the generated change of address document
        String docId = body.get("docId").toString();
        ResponseEntity<byte[]> downloadRes = controller.downloadDocument(docId, "change_of_address");
        assertNotNull(downloadRes);
        assertEquals(200, downloadRes.getStatusCode().value());
        assertNotNull(downloadRes.getBody());
        assertTrue(downloadRes.getBody().length > 0);
        assertTrue(downloadRes.getHeaders().getContentDisposition().toString().contains("Change-of-Address-DRIW-"));
    }
}
