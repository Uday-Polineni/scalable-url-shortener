package com.example.scalableurlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urlshortener.rate-limit")
public class RateLimitProperties {

    private int shortenPerMinute = 20;
    private int registerPerMinute = 5;
    private int loginPerMinute = 10;
    private int redirectPerMinute = 100;
    private int statsPerMinute = 30;
    private int generalPerMinute = 60;

    public int getShortenPerMinute() {
        return shortenPerMinute;
    }

    public void setShortenPerMinute(int shortenPerMinute) {
        this.shortenPerMinute = shortenPerMinute;
    }

    public int getRegisterPerMinute() {
        return registerPerMinute;
    }

    public void setRegisterPerMinute(int registerPerMinute) {
        this.registerPerMinute = registerPerMinute;
    }

    public int getLoginPerMinute() {
        return loginPerMinute;
    }

    public void setLoginPerMinute(int loginPerMinute) {
        this.loginPerMinute = loginPerMinute;
    }

    public int getRedirectPerMinute() {
        return redirectPerMinute;
    }

    public void setRedirectPerMinute(int redirectPerMinute) {
        this.redirectPerMinute = redirectPerMinute;
    }

    public int getStatsPerMinute() {
        return statsPerMinute;
    }

    public void setStatsPerMinute(int statsPerMinute) {
        this.statsPerMinute = statsPerMinute;
    }

    public int getGeneralPerMinute() {
        return generalPerMinute;
    }

    public void setGeneralPerMinute(int generalPerMinute) {
        this.generalPerMinute = generalPerMinute;
    }
}
