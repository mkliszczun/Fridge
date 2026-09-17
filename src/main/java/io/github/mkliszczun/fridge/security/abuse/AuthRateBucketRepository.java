package io.github.mkliszczun.fridge.security.abuse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;

public interface AuthRateBucketRepository extends JpaRepository<AuthRateBucket, String> {
    @Modifying
    @Query("delete from AuthRateBucket b where b.expiresAt <= :now")
    void deleteExpired(Instant now);
}
