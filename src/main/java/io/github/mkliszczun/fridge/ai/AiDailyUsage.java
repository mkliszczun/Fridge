package io.github.mkliszczun.fridge.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ai_daily_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "usage_date"}))
@Getter
@Setter
public class AiDailyUsage {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private LocalDate usageDate;
    @Column(nullable = false) private long chargedMicros;
    @Column(nullable = false) private long inputTokens;
    @Column(nullable = false) private long cachedInputTokens;
    @Column(nullable = false) private long cacheWriteTokens;
    @Column(nullable = false) private long outputTokens;
    @Column(nullable = false) private long unsettledRequests;
}
