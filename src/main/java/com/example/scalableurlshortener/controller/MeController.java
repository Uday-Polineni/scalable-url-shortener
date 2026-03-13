package com.example.scalableurlshortener.controller;

import com.example.scalableurlshortener.service.UrlCacheService;
import com.example.scalableurlshortener.store.UrlRepository;
import com.example.scalableurlshortener.store.UserEntity;
import com.example.scalableurlshortener.store.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/me")
public class MeController {

    private final UserRepository userRepository;
    private final UrlRepository urlRepository;
    private final UrlCacheService cacheService;

    public MeController(UserRepository userRepository,
                        UrlRepository urlRepository,
                        UrlCacheService cacheService) {
        this.userRepository = userRepository;
        this.urlRepository = urlRepository;
        this.cacheService = cacheService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(Authentication authentication) {
        String email = authentication.getName();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        long totalLinks = urlRepository.countByUser(user);
        long dbClicks = urlRepository.sumRedirectCountByUserId(user.getId());

        // Add pending Redis counts not yet flushed to DB
        long pendingClicks = urlRepository.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .mapToLong(u -> cacheService.getClickCount(u.getCode()))
                .sum();

        return Map.of(
                "email", user.getEmail(),
                "totalLinks", totalLinks,
                "totalClicks", dbClicks + pendingClicks
        );
    }

    @GetMapping("/links")
    public List<Map<String, Object>> links(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        String email = authentication.getName();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return urlRepository.findTop20ByUserOrderByCreatedAtDesc(user).stream().map(u -> {
            long dbCount = u.getRedirectCount();
            long pendingCount = cacheService.getClickCount(u.getCode());

            Map<String, Object> row = new java.util.HashMap<>();
            row.put("code", u.getCode());
            row.put("longUrl", u.getLongUrl());
            row.put("redirectCount", dbCount + pendingCount);
            row.put("createdAt", u.getCreatedAt());
            return row;
        }).toList();
    }
}
