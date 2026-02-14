package com.example.scalableurlshortener.controller;

import com.example.scalableurlshortener.dto.ShortenRequest;
import com.example.scalableurlshortener.dto.ShortenResponse;
import com.example.scalableurlshortener.service.UrlShortenerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
        String code = service.shorten(request.getLongUrl());
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
}
