package io.github.mkliszczun.fridge.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface AiGlobalDailyUsageRepository extends JpaRepository<AiGlobalDailyUsage, LocalDate> {}
