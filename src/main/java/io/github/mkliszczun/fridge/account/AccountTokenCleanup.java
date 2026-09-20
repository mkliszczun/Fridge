package io.github.mkliszczun.fridge.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Clock;

@Component
public class AccountTokenCleanup {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountTokenCleanup(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void removeExpiredSecrets() {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("delete from refresh_token where expires_at <= ?", now);
        jdbc.update("delete from email_verification where expires_at <= ?", now);
        jdbc.update("update users set password_reset_hash = null, password_reset_expires_at = null where password_reset_expires_at <= ?", now);
    }
}
