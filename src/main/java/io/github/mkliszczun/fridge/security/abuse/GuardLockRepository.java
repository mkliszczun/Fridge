package io.github.mkliszczun.fridge.security.abuse;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface GuardLockRepository extends JpaRepository<GuardLock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GuardLock g where g.id = :id")
    Optional<GuardLock> lock(String id);
}
