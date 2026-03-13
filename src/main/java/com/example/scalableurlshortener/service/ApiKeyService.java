package com.example.scalableurlshortener.service;

import com.example.scalableurlshortener.store.ApiKeyEntity;
import com.example.scalableurlshortener.store.ApiKeyRepository;
import com.example.scalableurlshortener.store.UserEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Creates a new API key for the given user, stores only the hash in the database,
     * and returns the raw key value to the caller. The raw key is shown only once.
     */
    public String createKey(UserEntity user, String label) {
        String rawKey = generateRawKey();
        String hash = hash(rawKey);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setUser(user);
        entity.setKeyHash(hash);
        entity.setLabel(label);
        apiKeyRepository.save(entity);

        return rawKey;
    }

    public Optional<UserEntity> findUserByRawKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String hash = hash(rawKey);
        return apiKeyRepository.findByKeyHashAndActiveTrue(hash)
                .map(ApiKeyEntity::getUser);
    }

    public void revokeKey(String rawKey) {
        String hash = hash(rawKey);
        apiKeyRepository.findByKeyHashAndActiveTrue(hash).ifPresent(key -> {
            key.setActive(false);
            key.setRevokedAt(OffsetDateTime.now());
            apiKeyRepository.save(key);
        });
    }

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

