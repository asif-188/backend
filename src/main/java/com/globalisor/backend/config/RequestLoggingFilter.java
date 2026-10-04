package com.globalisor.backend.config;

import com.globalisor.backend.security.UserDetailsImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern BASE64_DATA_PATTERN = Pattern.compile("data:[a-zA-Z0-9/+-]+;base64,[a-zA-Z0-9+/=]+");
    private static final Pattern EMAIL_LOGIN_PATTERN = Pattern.compile("(?i)\"(?:email|username|login|id)\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();

        // Skip logging for GET, OPTIONS, and HEAD calls
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only cache body for non-GET requests to save memory
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 1024 * 1024);
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequestDetails(wrappedRequest, response, duration);
        }
    }

    private void logRequestDetails(ContentCachingRequestWrapper request, HttpServletResponse response, long duration) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            String fullPath = (query != null && !query.isEmpty()) ? (uri + "?" + query) : uri;

            String clientIp = getClientIp(request);
            String referer = request.getHeader("Referer");
            String refererDisplay = (referer != null && !referer.trim().isEmpty()) ? referer : "Direct/API";

            UserLogInfo userInfo = resolveUserInfo(request);
            String portal = detectPortal(request, userInfo.role);
            String contentType = request.getContentType() != null ? request.getContentType() : "N/A";
            int status = response.getStatus();

            String body = extractSanitizedBody(request);

            log.info("[HTTP-REQUEST] Method: {} | URI: {} | Status: {} | Duration: {}ms | Portal: {} | Role: {} | User: {} | IP: {} | Referer: {} | Content-Type: {} | Payload: {}",
                    method, fullPath, status, duration, portal, userInfo.role, userInfo.displayUser, clientIp, refererDisplay, contentType, body);
        } catch (Exception e) {
            log.warn("Failed to log request details: {}", e.getMessage());
        }
    }

    private String detectPortal(HttpServletRequest request, String role) {
        // 1. Explicit Header if frontend sets it
        String xPortal = request.getHeader("X-Portal");
        if (xPortal != null && !xPortal.trim().isEmpty()) {
            return xPortal.trim().toUpperCase();
        }

        // 2. Referer URL inspection
        String referer = request.getHeader("Referer");
        if (referer != null) {
            String lowerRef = referer.toLowerCase();
            if (lowerRef.contains("/admin/") || lowerRef.contains("/admin.html")) {
                return "ADMIN_PORTAL";
            }
            if (lowerRef.contains("/staff/") || lowerRef.contains("/staff.html")) {
                return "STAFF_PORTAL";
            }
            if (lowerRef.contains("/client/") || lowerRef.contains("/client.html")
                    || lowerRef.contains("/requirements") || lowerRef.contains("/onboarding")
                    || lowerRef.contains("/signin") || lowerRef.contains("/login")
                    || lowerRef.contains("/auth")) {
                return "CLIENT_PORTAL";
            }
        }

        // 3. API URI path inspection
        String uri = request.getRequestURI();
        if (uri != null) {
            String lowerUri = uri.toLowerCase();
            if (lowerUri.startsWith("/api/admin")) return "ADMIN_PORTAL";
            if (lowerUri.startsWith("/api/staff")) return "STAFF_PORTAL";
            if (lowerUri.startsWith("/api/client") || lowerUri.startsWith("/api/requirements")
                    || lowerUri.startsWith("/api/onboarding") || lowerUri.startsWith("/api/kyc")
                    || lowerUri.startsWith("/api/chat") || lowerUri.startsWith("/api/ocr")) {
                return "CLIENT_PORTAL";
            }
        }

        // 4. Fallback based on authenticated user's role
        if (role != null && !role.isEmpty() && !"UNAUTHENTICATED".equalsIgnoreCase(role)) {
            return role + "_PORTAL";
        }

        return "PUBLIC_API";
    }

    private UserLogInfo resolveUserInfo(ContentCachingRequestWrapper request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String role = "USER";
                if (auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
                    for (GrantedAuthority ga : auth.getAuthorities()) {
                        String a = ga.getAuthority();
                        if (a.startsWith("ROLE_")) {
                            role = a.substring(5);
                        } else {
                            role = a;
                        }
                    }
                }

                String display = auth.getName();
                if (auth.getPrincipal() instanceof UserDetailsImpl) {
                    UserDetailsImpl u = (UserDetailsImpl) auth.getPrincipal();
                    String name = (u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "");
                    name = name.trim();
                    String email = u.getEmail() != null ? u.getEmail() : "";
                    display = u.getId() + (email.isEmpty() ? "" : " (" + email + ")") + (name.isEmpty() ? "" : " [" + name + "]");
                }
                return new UserLogInfo(display, role);
            }
        } catch (Exception ignored) {}

        // If unauthenticated, check if it's a login attempt to display the target email
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/signin")) {
            byte[] buf = request.getContentAsByteArray();
            if (buf != null && buf.length > 0) {
                String rawBody = new String(buf, StandardCharsets.UTF_8);
                Matcher m = EMAIL_LOGIN_PATTERN.matcher(rawBody);
                if (m.find()) {
                    String attemptedLogin = m.group(1);
                    return new UserLogInfo("Anonymous (Login: " + attemptedLogin + ")", "LOGIN_ATTEMPT");
                }
            }
        }

        return new UserLogInfo("Anonymous/Unauthenticated", "UNAUTHENTICATED");
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.trim().isEmpty() && !"unknown".equalsIgnoreCase(xForwarded)) {
            return xForwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String extractSanitizedBody(ContentCachingRequestWrapper request) {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return "<none>";
        }

        byte[] buf = request.getContentAsByteArray();
        if (buf == null || buf.length == 0) {
            return "<empty>";
        }

        String rawBody = new String(buf, StandardCharsets.UTF_8);

        // Sanitize password / secret fields if present
        String sanitized = rawBody.replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"***\"");

        // Sanitize large base64 file payloads so they do not flood logs
        Matcher matcher = BASE64_DATA_PATTERN.matcher(sanitized);
        if (matcher.find()) {
            sanitized = matcher.replaceAll("[base64 binary data masked]");
        }

        // Truncate if exceedingly long
        if (sanitized.length() > 3000) {
            sanitized = sanitized.substring(0, 3000) + "... [truncated " + (sanitized.length() - 3000) + " chars]";
        }

        return sanitized;
    }

    private static class UserLogInfo {
        final String displayUser;
        final String role;

        UserLogInfo(String displayUser, String role) {
            this.displayUser = displayUser;
            this.role = role;
        }
    }
}

