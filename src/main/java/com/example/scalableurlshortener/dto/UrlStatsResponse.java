package com.example.scalableurlshortener.dto;

import java.time.OffsetDateTime;

public class UrlStatsResponse {

    private final String code;
    private final String longUrl;
    private final long redirectCount;
    private final OffsetDateTime createdAt;

    public UrlStatsResponse(String code, String longUrl, long redirectCount, OffsetDateTime createdAt) {
        this.code = code;
        this.longUrl = longUrl;
        this.redirectCount = redirectCount;
        this.createdAt = createdAt;
    }

    public String getCode() {
        return code;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public long getRedirectCount() {
        return redirectCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

