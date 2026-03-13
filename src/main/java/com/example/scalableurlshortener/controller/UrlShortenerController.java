package com.example.scalableurlshortener.controller;

import com.example.scalableurlshortener.dto.ShortenRequest;
import com.example.scalableurlshortener.dto.ShortenResponse;
import com.example.scalableurlshortener.dto.UrlStatsResponse;
import com.example.scalableurlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class UrlShortenerController {

    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            @RequestHeader(name = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestParam(name = "apiKey", required = false) String apiKeyQuery,
            Authentication authentication
    ) {
        String apiKey = apiKeyHeader != null ? apiKeyHeader : apiKeyQuery;
        String code = service.shorten(request.getLongUrl(), request.getCustomAlias(), apiKey,
                authentication != null ? authentication.getName() : null);
        String shortUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/{code}")
                .buildAndExpand(code)
                .toUriString();
        return ResponseEntity.ok(new ShortenResponse(shortUrl));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return service.resolve(code)
                .map(longUrl -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setLocation(URI.create(longUrl));
                    return new ResponseEntity<Void>(headers, HttpStatus.FOUND); // 302
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/stats/{code}")
    public ResponseEntity<UrlStatsResponse> stats(@PathVariable String code) {
        return service.getStats(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
