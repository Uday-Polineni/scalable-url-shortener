package com.example.scalableurlshortener.store;

import java.util.Optional;

public interface UrlStore {

    Optional<String> getLongUrl(String code);

    Optional<String> getCodeByLongUrlAndUser(String longUrl, UserEntity user);

    String getOrCreateCode(String longUrl, String candidateCode, UserEntity user);

    boolean containsCode(String code);
}

