package com.example.scalableurlshortener.config;

import com.example.scalableurlshortener.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties props;

    public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties props) {
        this.rateLimitService = rateLimitService;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip rate limiting for static assets
        if (path.startsWith("/ui/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        String bucket = resolveBucket(method, path);
        int limit = resolveLimit(bucket);

        if (rateLimitService.isRateLimited(clientId, bucket, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return "user:" + principal.getName();
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private String resolveBucket(String method, String path) {
        if ("POST".equals(method) && "/shorten".equals(path)) {
            return "shorten";
        }
        if ("POST".equals(method) && "/auth/register".equals(path)) {
            return "register";
        }
        if ("POST".equals(method) && "/login".equals(path)) {
            return "login";
        }
        if ("GET".equals(method) && path.startsWith("/stats/")) {
            return "stats";
        }
        if ("GET".equals(method) && path.matches("^/[^/]+$")
                && !"/health".equals(path) && !"/logout".equals(path)) {
            return "redirect";
        }
        return "general";
    }

    private int resolveLimit(String bucket) {
        return switch (bucket) {
            case "shorten" -> props.getShortenPerMinute();
            case "register" -> props.getRegisterPerMinute();
            case "login" -> props.getLoginPerMinute();
            case "redirect" -> props.getRedirectPerMinute();
            case "stats" -> props.getStatsPerMinute();
            default -> props.getGeneralPerMinute();
        };
    }
}
