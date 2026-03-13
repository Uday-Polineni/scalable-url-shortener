package com.example.scalableurlshortener.store;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class PostgresUrlStore implements UrlStore {

    private final UrlRepository repository;

    public PostgresUrlStore(UrlRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> getLongUrl(String code) {
        return repository.findByCode(code).map(UrlEntity::getLongUrl);
    }

    @Override
    public Optional<String> getCodeByLongUrlAndUser(String longUrl, UserEntity user) {
        return repository.findByLongUrlAndUser(longUrl, user).map(UrlEntity::getCode);
    }

    @Override
    public boolean containsCode(String code) {
        return repository.findByCode(code).isPresent();
    }

    @Override
    @Transactional
    public String getOrCreateCode(String longUrl, String candidateCode, UserEntity user) {
        return repository.findByLongUrlAndUser(longUrl, user)
                .map(UrlEntity::getCode)
                .orElseGet(() -> createOrGetExisting(longUrl, candidateCode, user));
    }

    private String createOrGetExisting(String longUrl, String candidateCode, UserEntity user) {
        try {
            UrlEntity entity = new UrlEntity();
            entity.setCode(candidateCode);
            entity.setLongUrl(longUrl);
            entity.setUser(user);
            return repository.saveAndFlush(entity).getCode();
        } catch (DataIntegrityViolationException ex) {
            return repository.findByLongUrlAndUser(longUrl, user)
                    .orElseThrow(() -> ex)
                    .getCode();
        }
    }
}

