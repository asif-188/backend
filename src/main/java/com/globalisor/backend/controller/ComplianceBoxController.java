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

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken() {
        Map<String, Object> result = complianceBoxService.refreshBrowserToken();
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Object> result = complianceBoxService.refreshBrowserToken();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/token")
    public ResponseEntity<?> updateToken(@RequestBody Map<String, Object> body) {
        String token = body != null && body.containsKey("token") ? String.valueOf(body.get("token")).trim() : "";
        if (token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Token is required"));
        }
        complianceBoxService.saveToken(token);
        return ResponseEntity.ok(Map.of("success", true, "message", "ComplianceBox token updated successfully in database", "token", token));
    }

    @GetMapping("/token")
    public ResponseEntity<?> getToken() {
        String token = complianceBoxService.resolveToken();
        return ResponseEntity.ok(Map.of("success", true, "token", token));
    }
}
