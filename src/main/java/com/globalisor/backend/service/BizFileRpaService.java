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

    @org.springframework.beans.factory.annotation.Value("${bizfile.rpa.headless:true}")
    private boolean headlessConfig;

    private boolean isHeadlessMode() {
        String envVal = System.getenv("BIZFILE_RPA_HEADLESS");
        if (envVal != null && !envVal.isBlank()) {
            return Boolean.parseBoolean(envVal);
        }
        return headlessConfig;
    }

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

            // ── STEP 1: Launch Chromium browser ──────────────────────────────────
            boolean isHeadless = isHeadlessMode();
            logger.info("[BizFileRPA] [{}] Launching Chromium (headless={})...", session.sessionId, isHeadless);
            session.status = "LAUNCHED";
            session.message = "Step 1/3: Opening browser...";

            List<String> browserArgs = new java.util.ArrayList<>(List.of(
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-blink-features=AutomationControlled"
            ));
            if (!isHeadless) {
                browserArgs.add("--start-maximized");
            }

            session.playwright = Playwright.create();
            session.browser = session.playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                    .setHeadless(isHeadless)
                    .setSlowMo(isHeadless ? 100 : 400)
                    .setArgs(browserArgs)
            );

            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                              "AppleWebKit/537.36 (KHTML, like Gecko) " +
                              "Chrome/120.0.0.0 Safari/537.36");

            if (isHeadless) {
                contextOptions.setViewportSize(1280, 800);
            } else {
                contextOptions.setViewportSize(null);
            }

            BrowserContext context = session.browser.newContext(contextOptions);
            session.page = context.newPage();

            // ── STEP 2: Navigate to bizfile.gov.sg ───────────────────────────────
            logger.info("[BizFileRPA] [{}] Navigating to {}", session.sessionId, BIZFILE_URL);
            session.status = "NAVIGATING";
            session.message = "Step 2/3: Opening bizfile.gov.sg...";

            session.page.navigate(BIZFILE_URL);
            session.page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            try { Thread.sleep(2500); } catch (Exception ignored) {}

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
            logger.info("[BizFileRPA] [{}] Clicking Login button (#global-header-login)", session.sessionId);

            Locator loginBtn = session.page.locator("#global-header-login");
            loginBtn.waitFor(new Locator.WaitForOptions().setTimeout(15000));
            loginBtn.click();

            // Give modal 1.5s to open
            try { Thread.sleep(1500); } catch (Exception ignored) {}
            if (!session.page.locator("#login-modal").isVisible()) {
                // Fallback direct JS trigger if modal animation didn't start
                session.page.evaluate("() => document.getElementById('global-header-login')?.click()");
                try { Thread.sleep(1500); } catch (Exception ignored) {}
            }

            updateScreenshot(session);
            session.status = "LOGIN_MODAL";
            session.message = "Step 2/3: Login modal opened. Selecting Business User...";

            // ── STEP 3: Click Business User ───────────────────────────────────────
            logger.info("[BizFileRPA] [{}] Clicking Business User (#business-login)", session.sessionId);

            Locator businessUserCard = session.page.locator("#business-login");
            businessUserCard.waitFor(new Locator.WaitForOptions().setTimeout(15000));
            businessUserCard.click();

            // ── STEP 3b: Wait for SingPass login page / QR code ───────────────────
            logger.info("[BizFileRPA] [{}] Waiting for SingPass page...", session.sessionId);
            long startWait = System.currentTimeMillis();
            while (System.currentTimeMillis() - startWait < 35000) {
                try {
                    String curUrl = session.page.url();
                    if (curUrl != null && (curUrl.contains("singpass.gov.sg") || curUrl.contains("corppass"))) {
                        logger.info("[BizFileRPA] [{}] SingPass URL reached: {}", session.sessionId, curUrl);
                        break;
                    }
                } catch (Exception ignored) {
                    // Mid-navigation, continue polling
                }
                try { Thread.sleep(1000); } catch (Exception ignored) {}
            }

            // Wait for SingPass page to settle
            try {
                session.page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
            } catch (Exception ignored) {}

            // Wait 2.5s for dynamic SingPass QR code canvas/SVG to fully paint
            try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
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

                // Still on SingPass — refresh screenshot every 5s
                if (elapsed % 5000 == 0) {
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
                byte[] png = null;
                String currentUrl = "";
                try {
                    currentUrl = session.page.url();
                } catch (Exception ignored) {}

                // When on SingPass page or waiting for QR scan, intelligently crop directly to the QR card/element
                if ("WAITING_QR_SCAN".equals(session.status) || (currentUrl != null && currentUrl.contains("singpass"))) {
                    try {
                        Object clipObj = session.page.evaluate(
                            "() => {\n" +
                            "  try {\n" +
                            "    // 1. Direct Singpass QR canvas / svg / img\n" +
                            "    const qr = document.querySelector('canvas, svg, img[alt*=\"QR\" i], [id*=\"qr\" i], [class*=\"qr\" i], [data-testid*=\"qr\" i]');\n" +
                            "    if (qr) {\n" +
                            "      const r = qr.getBoundingClientRect();\n" +
                            "      if (r.width >= 100 && r.height >= 100 && r.width <= 600) {\n" +
                            "        const pad = 24;\n" +
                            "        const x = Math.max(0, r.x - pad);\n" +
                            "        const y = Math.max(0, r.y - pad);\n" +
                            "        return { x: x, y: y, width: r.width + pad * 2, height: r.height + pad * 2 };\n" +
                            "      }\n" +
                            "    }\n" +
                            "    // 2. Singpass login card container\n" +
                            "    const card = document.querySelector('.card, [class*=\"card\" i], [class*=\"login-box\" i], [class*=\"container\" i], main');\n" +
                            "    if (card) {\n" +
                            "      const r = card.getBoundingClientRect();\n" +
                            "      if (r.width >= 250 && r.height >= 250 && r.width <= 800) {\n" +
                            "        return { x: Math.max(0, r.x), y: Math.max(0, r.y), width: r.width, height: r.height };\n" +
                            "      }\n" +
                            "    }\n" +
                            "    // 3. Fallback center box for standard SingPass desktop layout\n" +
                            "    return { x: 360, y: 120, width: 560, height: 560 };\n" +
                            "  } catch (e) {\n" +
                            "    return null;\n" +
                            "  }\n" +
                            "}"
                        );

                        if (clipObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) clipObj;
                            Number x = (Number) map.get("x");
                            Number y = (Number) map.get("y");
                            Number width = (Number) map.get("width");
                            Number height = (Number) map.get("height");
                            if (x != null && y != null && width != null && height != null && width.doubleValue() > 50) {
                                png = session.page.screenshot(new Page.ScreenshotOptions()
                                    .setClip(x.doubleValue(), y.doubleValue(), width.doubleValue(), height.doubleValue()));
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("[BizFileRPA] QR clip detection failed, falling back: {}", e.getMessage());
                    }
                }

                if (png == null) {
                    png = session.page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                }

                session.screenshotBase64 = java.util.Base64.getEncoder().encodeToString(png);
            }
        } catch (Exception e) {
            logger.debug("[BizFileRPA] Screenshot failed: {}", e.getMessage());
        }
    }
}
