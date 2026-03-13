package com.example.scalableurlshortener.service;

import com.example.scalableurlshortener.config.UrlValidationProperties;
import com.example.scalableurlshortener.dto.UrlStatsResponse;
import com.example.scalableurlshortener.store.UrlEntity;
import com.example.scalableurlshortener.store.UrlRepository;
import com.example.scalableurlshortener.store.UrlStore;
import com.example.scalableurlshortener.store.UserEntity;
import com.example.scalableurlshortener.store.UserRepository;
import com.example.scalableurlshortener.util.Base62;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Service
public class UrlShortenerService {

    private final UrlStore store;
    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final UrlCacheService cacheService;
    private final UrlValidationProperties validationProperties;
    private final ApiKeyService apiKeyService;

    public UrlShortenerService(UrlStore store,
                               UrlRepository urlRepository,
                               UserRepository userRepository,
                               UrlCacheService cacheService,
                               UrlValidationProperties validationProperties,
                               ApiKeyService apiKeyService) {
        this.store = store;
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.cacheService = cacheService;
        this.validationProperties = validationProperties;
        this.apiKeyService = apiKeyService;
    }

    public String shorten(String longUrl, String customAlias, String apiKey, String sessionEmail) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("longUrl must not be blank");
        }

        UserEntity user;
        if (apiKey != null && !apiKey.isBlank()) {
            user = apiKeyService.findUserByRawKey(apiKey)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid or revoked API key"));
        } else if (sessionEmail != null && !sessionEmail.isBlank()) {
            user = userRepository.findByEmail(sessionEmail)
                    .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
        } else {
            throw new IllegalArgumentException("Authentication required — log in or provide an API key");
        }

        String normalized = longUrl.trim();
        validateUrl(normalized);

        if (customAlias != null && !customAlias.isBlank()) {
            String alias = validateAndNormalizeAlias(customAlias);

            if (store.containsCode(alias)) {
                throw new IllegalArgumentException("Alias already in use");
            }

            return store.getCodeByLongUrlAndUser(normalized, user)
                    .orElseGet(() -> store.getOrCreateCode(normalized, alias, user));
        }

        // Idempotency: if this user already shortened this URL, return their existing code
        return store.getCodeByLongUrlAndUser(normalized, user)
                .orElseGet(() -> createNewCode(normalized, user));
    }

    private String createNewCode(String normalizedLongUrl, UserEntity user) {
        while (true) {
            try {
                UrlEntity entity = new UrlEntity();
                entity.setLongUrl(normalizedLongUrl);
                entity.setUser(user);
                UrlEntity saved = urlRepository.saveAndFlush(entity);

                String code = Base62.encode(saved.getId());
                saved.setCode(code);

                UrlEntity withCode = urlRepository.saveAndFlush(saved);
                return withCode.getCode();
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                return urlRepository.findByLongUrlAndUser(normalizedLongUrl, user)
                        .map(UrlEntity::getCode)
                        .orElseThrow(() -> ex);
            }
        }
    }

    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();

            if (scheme == null ||
                    (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Only http/https URLs are allowed");
            }
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("URL must include a host");
            }

            var allowed = validationProperties.getAllowedDomains();
            if (allowed != null && !allowed.isEmpty()) {
                boolean match = allowed.stream().anyMatch(domain ->
                        host.equalsIgnoreCase(domain) || host.toLowerCase().endsWith("." + domain.toLowerCase())
                );
                if (!match) {
                    throw new IllegalArgumentException("Domain is not allowed");
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format");
        }
    }

    private String validateAndNormalizeAlias(String alias) {
        String trimmed = alias.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Alias must not be blank");
        }
        if (trimmed.length() > 16) {
            throw new IllegalArgumentException("Alias must be at most 16 characters long");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean isDigit = c >= '0' && c <= '9';
            boolean isLower = c >= 'a' && c <= 'z';
            boolean isUpper = c >= 'A' && c <= 'Z';
            if (!isDigit && !isLower && !isUpper) {
                throw new IllegalArgumentException("Alias may only contain letters and digits");
            }
        }
        return trimmed;
    }

    public Optional<String> resolve(String code) {
        // Always count the click in Redis (~0.1ms, no DB hit)
        Optional<String> cached = cacheService.getLongUrl(code);
        if (cached.isPresent()) {
            cacheService.incrementClickCount(code);
            return cached;
        }

        // Cache miss — fetch from DB, populate cache
        Optional<UrlEntity> entityOpt = urlRepository.findByCode(code);
        entityOpt.ifPresent(entity -> {
            cacheService.putLongUrl(entity.getCode(), entity.getLongUrl());
            cacheService.incrementClickCount(code);
        });
        return entityOpt.map(UrlEntity::getLongUrl);
    }

    public Optional<UrlStatsResponse> getStats(String code) {
        return urlRepository.findByCode(code)
                .map(entity -> new UrlStatsResponse(
                        entity.getCode(),
                        entity.getLongUrl(),
                        entity.getRedirectCount() + cacheService.getClickCount(code),
                        entity.getCreatedAt()
                ));
    }

}