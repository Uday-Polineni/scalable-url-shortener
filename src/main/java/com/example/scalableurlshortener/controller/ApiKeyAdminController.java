package com.example.scalableurlshortener.controller;

import com.example.scalableurlshortener.service.ApiKeyService;
import com.example.scalableurlshortener.store.UserEntity;
import com.example.scalableurlshortener.store.UserRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/api-keys")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;
    private final UserRepository userRepository;

    public ApiKeyAdminController(ApiKeyService apiKeyService, UserRepository userRepository) {
        this.apiKeyService = apiKeyService;
        this.userRepository = userRepository;
    }

    @PostMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> createApiKey(
            @PathVariable Long userId,
            @RequestParam(name = "label", required = false) String label
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String rawKey = apiKeyService.createKey(user, label);
        return ResponseEntity.ok(Map.of("apiKey", rawKey));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revokeApiKey(@RequestParam @NotBlank String apiKey) {
        apiKeyService.revokeKey(apiKey);
        return ResponseEntity.noContent().build();
    }
}

