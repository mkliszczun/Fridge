package io.github.mkliszczun.fridge.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "ai_global_daily_usage")
@Getter @Setter
public class AiGlobalDailyUsage {
    @Id @Column(name = "usage_date") private LocalDate usageDate;
    @Column(name = "charged_micros", nullable = false) private long chargedMicros;
    @Column(name = "warning_sent", nullable = false) private boolean warningSent;
}
