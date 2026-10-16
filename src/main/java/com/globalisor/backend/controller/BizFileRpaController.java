package com.globalisor.backend.controller;

import com.globalisor.backend.service.BizFileRpaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * BizFile RPA REST Controller
 *
 * Endpoints:
 *   POST   /api/bizfile/session/start              - Start browser session (Steps 1-3)
 *   GET    /api/bizfile/session/{sessionId}/status  - Poll session status + screenshot
 *   DELETE /api/bizfile/session/{sessionId}         - Close/cleanup session
 *   GET    /api/bizfile/sessions                    - List all active sessions
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/bizfile")
public class BizFileRpaController {

    @Autowired
    private BizFileRpaService bizFileRpaService;

    /**
     * Step 1-3: Launch browser → Navigate to BizFile → Login as Business User
     * Returns sessionId immediately; browser runs in background.
     *
     * Body (optional):
     * {
     *   "applicationId": "abc123",
     *   "applicationData": { ... }
     * }
     */
    @PostMapping("/session/start")
    public ResponseEntity<?> startSession(@RequestBody(required = false) Map<String, Object> body) {
        String applicationId = body != null && body.containsKey("applicationId")
            ? String.valueOf(body.get("applicationId")) : "manual";

        @SuppressWarnings("unchecked")
        Map<String, Object> applicationData = body != null && body.containsKey("applicationData")
            ? (Map<String, Object>) body.get("applicationData") : null;

        Map<String, Object> result = bizFileRpaService.startSession(applicationId, applicationData);
        return ResponseEntity.ok(result);
    }

    /**
     * Poll session status — call every 2-3 seconds from admin UI.
     * Response includes:
     *   - status: CREATED | LAUNCHED | NAVIGATING | OPENED | LOGIN_MODAL |
     *             WAITING_QR_SCAN | LOGGED_IN | ERROR | TIMEOUT | CLOSED
     *   - message: Human readable description of current step
     *   - screenshot: base64 PNG of current browser state (optional, may be null)
     */
    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<?> getSessionStatus(@PathVariable String sessionId) {
        Map<String, Object> result = bizFileRpaService.getSessionStatus(sessionId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Close and cleanup browser session
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> closeSession(@PathVariable String sessionId) {
        Map<String, Object> result = bizFileRpaService.closeSession(sessionId);
        return ResponseEntity.ok(result);
    }

    /**
     * List all active RPA sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions() {
        return ResponseEntity.ok(bizFileRpaService.listSessions());
    }
}
