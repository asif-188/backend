package com.globalisor.backend.controller;

import com.globalisor.backend.service.ComplianceBoxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/integrations/compliancebox")
public class ComplianceBoxController {

    @Autowired
    private ComplianceBoxService complianceBoxService;

    @PostMapping("/sync/{id}")
    public ResponseEntity<?> syncApplication(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = complianceBoxService.syncApplication(id, body);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/sync-custom")
    public ResponseEntity<?> syncCustom(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = complianceBoxService.syncCustom(payload);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
