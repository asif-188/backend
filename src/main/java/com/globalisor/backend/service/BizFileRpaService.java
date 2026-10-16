package com.globalisor.backend.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BizFile RPA Service - Human-in-the-Loop Browser Automation
 *
 * Flow:
 *   Step 1: Open bizfile.gov.sg browser window (headed / visible)
 *   Step 2: Click Login button (top nav)
 *   Step 3: Click Business User card → redirects to SingPass Corppass QR page
 *   Step 4: [HUMAN] User scans SingPass QR with phone — bot waits
 *   Step 5: Detect login success → navigate to Annual Filing (next steps)
 */
@Service
public class BizFileRpaService {

    private static final Logger logger = LoggerFactory.getLogger(BizFileRpaService.class);
    private static final String BIZFILE_URL = "https://bizfile.gov.sg";

    // Active RPA sessions keyed by sessionId
    private final ConcurrentHashMap<String, RpaSession> activeSessions = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────
    // Inner class: RPA Session state
    // ─────────────────────────────────────────────────────────────────
    public static class RpaSession {
        public String sessionId;
        public String applicationId;
        public String status;      // LAUNCHED | NAVIGATING | OPENED | LOGIN_MODAL | WAITING_QR_SCAN | LOGGED_IN | ERROR | CLOSED
        public String message;
        public String screenshotBase64;
        public String acknowledgmentNumber;
        public Playwright playwright;
        public Browser browser;
        public Page page;
        public long createdAt;
        public Map<String, Object> applicationData;

        public RpaSession(String sessionId, String applicationId) {
            this.sessionId = sessionId;
            this.applicationId = applicationId;
            this.status = "CREATED";
            this.message = "Session created, launching browser...";
            this.createdAt = System.currentTimeMillis();
        }

        public Map<String, Object> toStatusMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("sessionId", sessionId);
            map.put("applicationId", applicationId);
            map.put("status", status);
            map.put("message", message);
            if (screenshotBase64 != null) map.put("screenshot", screenshotBase64);
            if (acknowledgmentNumber != null) map.put("acknowledgmentNumber", acknowledgmentNumber);
            map.put("createdAt", createdAt);
            return map;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // PUBLIC API: Start Session (Steps 1-3 + waits for QR scan)
    // ─────────────────────────────────────────────────────────────────

    public Map<String, Object> startSession(String applicationId, Map<String, Object> applicationData) {
        String sessionId = "bizfile_" + System.currentTimeMillis();
        RpaSession session = new RpaSession(sessionId, applicationId);
        session.applicationData = applicationData != null ? applicationData : new HashMap<>();
        activeSessions.put(sessionId, session);

        // Run browser flow in background thread so REST endpoint returns immediately
        Thread thread = new Thread(() -> runBizFileFlow(session), "bizfile-rpa-" + sessionId);
        thread.setDaemon(true);
        thread.start();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("status", "CREATED");
        response.put("message", "BizFile browser session launching. Poll /status/{sessionId} for updates.");
        return response;
    }

    // ─────────────────────────────────────────────────────────────────
    // CORE FLOW: Steps 1 → 2 → 3 → wait QR
    // ─────────────────────────────────────────────────────────────────

    private void runBizFileFlow(RpaSession session) {
        try {

            // ── STEP 1: Launch visible Chromium browser ───────────────────────────
            logger.info("[BizFileRPA] [{}] Launching Chromium (headed)...", session.sessionId);
            session.status = "LAUNCHED";
            session.message = "Step 1/3: Opening browser...";

            session.playwright = Playwright.create();
            session.browser = session.playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setSlowMo(400)
                    .setArgs(List.of(
                        "--start-maximized",
                        "--disable-blink-features=AutomationControlled"
                    ))
            );

            BrowserContext context = session.browser.newContext(
                new Browser.NewContextOptions()
                    .setViewportSize(null)
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                                  "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                  "Chrome/120.0.0.0 Safari/537.36")
            );

            session.page = context.newPage();

            // ── STEP 2: Navigate to bizfile.gov.sg ───────────────────────────────
            logger.info("[BizFileRPA] [{}] Navigating to {}", session.sessionId, BIZFILE_URL);
            session.status = "NAVIGATING";
            session.message = "Step 2/3: Opening bizfile.gov.sg...";

            session.page.navigate(BIZFILE_URL);
            session.page.waitForLoadState(LoadState.NETWORKIDLE);

            // Dismiss announcement/maintenance modal if visible
            try {
                Locator closeModal = session.page.locator(
                    "button[aria-label='Close'], .modal button.close, [data-bs-dismiss='modal'], button:has-text('×')"
                ).first();
                if (closeModal.isVisible()) {
                    closeModal.click();
                    logger.info("[BizFileRPA] Dismissed announcement modal");
                    Thread.sleep(500);
                }
            } catch (Exception ignored) {}

            updateScreenshot(session);
            session.status = "OPENED";
            session.message = "Step 2/3: BizFile home page loaded. Clicking Login...";

            // ── STEP 2b: Click the Login button in the top navigation ─────────────
            logger.info("[BizFileRPA] [{}] Clicking Login button", session.sessionId);

            Locator loginBtn = session.page.locator(
                "a:has-text('Login'), button:has-text('Login'), [href*='login']"
            ).first();
            loginBtn.waitFor(new Locator.WaitForOptions().setTimeout(15000));
            loginBtn.click();

            // Wait for the login modal/dialog to appear
            session.page.waitForSelector(
                ".modal.show, [role='dialog'], [class*='modal-dialog']",
                new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000)
            );

            updateScreenshot(session);
            session.status = "LOGIN_MODAL";
            session.message = "Step 2/3: Login modal opened. Selecting Business User...";

            // ── STEP 3: Click Business User ───────────────────────────────────────
            logger.info("[BizFileRPA] [{}] Clicking Business User", session.sessionId);

            Locator businessUserCard = session.page.locator(
                "[class*='card']:has-text('Business User'), " +
                "h3:has-text('Business User'), " +
                "h2:has-text('Business User'), " +
                "button:has-text('Business User'), " +
                "a:has-text('Business User'), " +
                "div:has-text('Business User') >> nth=0"
            ).first();

            businessUserCard.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            businessUserCard.click();

            // Wait for redirect to SingPass / Corppass login page
            logger.info("[BizFileRPA] [{}] Waiting for SingPass redirect...", session.sessionId);
            session.page.waitForURL(
                "**/singpass.gov.sg/**",
                new Page.WaitForURLOptions().setTimeout(30000)
            );

            session.page.waitForLoadState(LoadState.NETWORKIDLE);
            updateScreenshot(session);

            session.status = "WAITING_QR_SCAN";
            session.message = "✅ Step 3/3: SingPass QR code page is open. " +
                              "Please scan the QR code with your SingPass mobile app to continue.";

            logger.info("[BizFileRPA] [{}] Reached SingPass QR page. Waiting for user to scan...", session.sessionId);

            // ── STEP 4 (HUMAN): Wait for user to scan QR ─────────────────────────
            waitForSingPassLogin(session);

        } catch (Exception e) {
            logger.error("[BizFileRPA] [{}] Error: {}", session.sessionId, e.getMessage(), e);
            session.status = "ERROR";
            session.message = "Error during automation: " + e.getMessage();
            updateScreenshot(session);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // STEP 4: Poll until SingPass QR is scanned and user is redirected back
    // ─────────────────────────────────────────────────────────────────

    private void waitForSingPassLogin(RpaSession session) {
        int maxWaitMs = 5 * 60 * 1000; // 5 minutes
        int elapsed = 0;
        int pollInterval = 2000;

        logger.info("[BizFileRPA] [{}] Polling for SingPass login completion...", session.sessionId);

        while (elapsed < maxWaitMs) {
            try {
                Thread.sleep(pollInterval);
                elapsed += pollInterval;

                String currentUrl = session.page.url();

                // Redirected back to BizFile = login success
                if (currentUrl.contains("bizfile.gov.sg") && !currentUrl.contains("login")) {
                    updateScreenshot(session);
                    session.status = "LOGGED_IN";
                    session.message = "✅ Login successful! You are now logged in to BizFile as a Business User.";
                    logger.info("[BizFileRPA] [{}] Login complete. URL: {}", session.sessionId, currentUrl);
                    return;
                }

                // Still on SingPass — refresh screenshot every 10s
                if (elapsed % 10000 == 0) {
                    updateScreenshot(session);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("[BizFileRPA] [{}] Poll error: {}", session.sessionId, e.getMessage());
            }
        }

        session.status = "TIMEOUT";
        session.message = "⏰ QR code was not scanned within 5 minutes. Please close this session and try again.";
        updateScreenshot(session);
    }

    // ─────────────────────────────────────────────────────────────────
    // Session Management APIs
    // ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getSessionStatus(String sessionId) {
        RpaSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Map.of("success", false, "error", "Session not found: " + sessionId);
        }
        Map<String, Object> result = new HashMap<>(session.toStatusMap());
        result.put("success", true);
        return result;
    }

    public Map<String, Object> closeSession(String sessionId) {
        RpaSession session = activeSessions.remove(sessionId);
        if (session == null) {
            return Map.of("success", false, "error", "Session not found: " + sessionId);
        }
        try {
            if (session.page != null) { try { session.page.close(); } catch (Exception ignored) {} }
            if (session.browser != null) { try { session.browser.close(); } catch (Exception ignored) {} }
            if (session.playwright != null) { try { session.playwright.close(); } catch (Exception ignored) {} }
        } catch (Exception e) {
            logger.warn("[BizFileRPA] Error closing session {}: {}", sessionId, e.getMessage());
        }
        logger.info("[BizFileRPA] Session {} closed", sessionId);
        return Map.of("success", true, "message", "Session closed successfully", "sessionId", sessionId);
    }

    public Map<String, Object> listSessions() {
        return Map.of(
            "success", true,
            "count", activeSessions.size(),
            "sessions", activeSessions.values().stream().map(RpaSession::toStatusMap).toList()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Utility: Capture base64 screenshot
    // ─────────────────────────────────────────────────────────────────

    private void updateScreenshot(RpaSession session) {
        try {
            if (session.page != null) {
                byte[] png = session.page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                session.screenshotBase64 = java.util.Base64.getEncoder().encodeToString(png);
            }
        } catch (Exception e) {
            logger.debug("[BizFileRPA] Screenshot failed: {}", e.getMessage());
        }
    }
}
