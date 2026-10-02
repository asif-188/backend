package com.globalisor.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();

        // Skip logging for GET, OPTIONS, and HEAD calls
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 1024 * 1024);
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logNonGetRequest(wrappedRequest, response, duration);
        }
    }

    private void logNonGetRequest(ContentCachingRequestWrapper request, HttpServletResponse response, long duration) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            String fullPath = (query != null && !query.isEmpty()) ? (uri + "?" + query) : uri;

            String clientIp = getClientIp(request);
            String user = getAuthenticatedUser();
            String contentType = request.getContentType() != null ? request.getContentType() : "N/A";
            int status = response.getStatus();

            String body = extractSanitizedBody(request);

            log.info("[HTTP-REQUEST] Method: {} | URI: {} | Status: {} | Duration: {}ms | IP: {} | User: {} | Content-Type: {} | Payload: {}",
                    method, fullPath, status, duration, clientIp, user, contentType, body);
        } catch (Exception e) {
            log.warn("Failed to log request details: {}", e.getMessage());
        }
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

    private String getAuthenticatedUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "Anonymous/Unauthenticated";
    }

    private String extractSanitizedBody(ContentCachingRequestWrapper request) {
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
}
