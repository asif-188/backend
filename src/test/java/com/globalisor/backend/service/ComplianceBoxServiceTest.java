package com.globalisor.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ComplianceBoxServiceTest {

    @Test
    public void testSyncCustomDirectHttp() {
        ComplianceBoxService service = new ComplianceBoxService();
        ReflectionTestUtils.setField(service, "complianceBoxApiUrl", "https://api.compliancebox.co/api/clients");
        ReflectionTestUtils.setField(service, "complianceBoxToken", "1efb62b2-4f40-4f7b-b08b-4fc641932eda");
        ReflectionTestUtils.setField(service, "complianceBoxCompanyId", "629dbca7374a1ac76b559ee7");

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Automated Java Test Client " + System.currentTimeMillis());
        payload.put("contactPerson", "Java Tester");
        payload.put("email", "javatest@globalisor.com");
        payload.put("phone", "+65 9123 4567");
        payload.put("country", "Singapore");

        Map<String, Object> result = service.syncCustom(payload);
        assertNotNull(result);
        System.out.println("Test Sync Result: " + result);
        
        // Ensure success is true and no "Cannot run program node" error occurs
        assertTrue((Boolean) result.get("success"), "Expected success to be true but got: " + result.get("error"));
        assertEquals(200, result.get("statusCode"));
        assertNotNull(result.get("complianceBoxId"));
    }
}
