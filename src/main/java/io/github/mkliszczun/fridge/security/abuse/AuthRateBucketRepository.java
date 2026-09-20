package io.github.mkliszczun.fridge.security.abuse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;

public interface AuthRateBucketRepository extends JpaRepository<AuthRateBucket, String> {
    @Modifying
    @Query("delete from AuthRateBucket b where b.expiresAt <= :now and b.id not like 'email:%'")
    void deleteExpired(Instant now);

    @Modifying
    @Query("delete from AuthRateBucket b where b.expiresAt <= :now and b.id like 'email:%'")
    void deleteExpiredEmailEvents(Instant now);

    java.util.List<AuthRateBucket> findByIdStartingWithAndExpiresAtAfter(String prefix, Instant now);
}
