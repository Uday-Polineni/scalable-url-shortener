package com.example.scalableurlshortener.store;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryUrlStore {

    private final ConcurrentMap<String, String> codeToLongUrl = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> longUrlToCode = new ConcurrentHashMap<>();

    public Optional<String> getLongUrl(String code) {
        return Optional.ofNullable(codeToLongUrl.get(code));
    }

    public Optional<String> getCodeByLongUrl(String longUrl) {
        return Optional.ofNullable(longUrlToCode.get(longUrl));
    }

    /**
     * Atomically ensures idempotency:
     * - If longUrl already has a code, returns the existing code
     * - Else stores (longUrl->code) and (code->longUrl) and returns the new code
     */
    public String getOrCreateCode(String longUrl, String newCode) {
        String existing = longUrlToCode.putIfAbsent(longUrl, newCode);
        if (existing != null) {
            return existing;
        }
        codeToLongUrl.put(newCode, longUrl);
        return newCode;
    }

    public boolean containsCode(String code) {
        return codeToLongUrl.containsKey(code);
    }
}
