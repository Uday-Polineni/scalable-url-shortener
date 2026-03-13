package com.example.scalableurlshortener.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlEntity, Long> {

    Optional<UrlEntity> findByCode(String code);

    Optional<UrlEntity> findByLongUrlAndUser(String longUrl, UserEntity user);

    long countByUser(UserEntity user);

    List<UrlEntity> findTop20ByUserOrderByCreatedAtDesc(UserEntity user);

    @Query("select coalesce(sum(u.redirectCount), 0) from UrlEntity u where u.user.id = :userId")
    long sumRedirectCountByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update UrlEntity u set u.redirectCount = u.redirectCount + :delta where u.code = :code")
    void incrementRedirectCount(@Param("code") String code, @Param("delta") long delta);
}

