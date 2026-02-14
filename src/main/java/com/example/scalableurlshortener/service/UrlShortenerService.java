package com.example.scalableurlshortener.service;

import com.example.scalableurlshortener.store.InMemoryUrlStore;
import com.example.scalableurlshortener.util.Base62;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import java.net.URI;
import java.net.URISyntaxException;

@Service
public class UrlShortenerService {

    private final InMemoryUrlStore store;
    private final AtomicLong idCounter = new AtomicLong(0);

    public UrlShortenerService(InMemoryUrlStore store) {
        this.store = store;
    }

    public String shorten(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("longUrl must not be blank");
        }

        String normalized = longUrl.trim();
        validateUrl(normalized);

        // ✅ Idempotency: if already shortened, return existing code
        return store.getCodeByLongUrl(normalized)
                .orElseGet(() -> createNewCode(normalized));
    }

    private String createNewCode(String normalizedLongUrl) {
        while (true) {
            long id = idCounter.incrementAndGet();
            String code = Base62.encode(id);

            // If collision ever happens, loop (super unlikely with atomic id)
            if (store.containsCode(code)) continue;

            // Atomically "claim" this longUrl; if someone else already did, return theirs.
            String finalCode = store.getOrCreateCode(normalizedLongUrl, code);

            // If we lost the race and got an existing code back, just return it.
            // If we won, finalCode == code and mapping is saved.
            return finalCode;
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
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format");
        }
    }


    public Optional<String> resolve(String code) {
        return store.getLongUrl(code);
    }
}