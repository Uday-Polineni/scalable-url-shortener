package com.example.scalableurlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "urlshortener")
public class UrlValidationProperties {

    /**
     * Optional list of allowed hostnames for long URLs.
     * If empty or not set, all domains are allowed.
     */
    private List<String> allowedDomains;

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }
}

