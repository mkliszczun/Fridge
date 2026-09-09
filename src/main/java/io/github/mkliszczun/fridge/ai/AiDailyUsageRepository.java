package io.github.mkliszczun.fridge.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiDailyUsageRepository extends JpaRepository<AiDailyUsage, UUID> {
    Optional<AiDailyUsage> findByUserIdAndUsageDate(UUID userId, LocalDate date);
}
