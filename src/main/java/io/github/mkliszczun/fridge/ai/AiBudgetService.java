package io.github.mkliszczun.fridge.ai;

import io.github.mkliszczun.fridge.repository.UserRepository;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.UUID;

@Service
public class AiBudgetService {
    public static final long MAX_INPUT_TOKENS = 128_000;
    public record Reservation(UUID userId, LocalDate date, long micros, int maxOutputTokens) {}
    public record Usage(LocalDate date, Instant resetsAt, BigDecimal limitUsd, BigDecimal estimatedCostUsd,
                        BigDecimal remainingUsd, long inputTokens, long cachedInputTokens, long outputTokens,
                        long unsettledRequests) {}
    private final AiDailyUsageRepository usage;
    private final UserRepository users;
    private final AiBudgetProperties prices;
    private final Clock clock;

    public AiBudgetService(AiDailyUsageRepository usage, UserRepository users, AiBudgetProperties prices, Clock clock) {
        this.usage = usage;
        this.users = users;
        this.prices = prices;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(UUID userId, long tokenVersion, int maxOutputTokens) {
        if (maxOutputTokens < 1 || maxOutputTokens > 8192) throw new IllegalArgumentException("Invalid output limit");
        var user = users.findLockedById(userId).orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        new AccountStatusUserDetailsChecker().check(AppUserDetails.fromEntity(user));
        if (user.getTokenVersion() != tokenVersion) throw new BadCredentialsException("Session revoked");
        LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        AiDailyUsage day = usage.findByUserIdAndUsageDate(userId, date).orElseGet(() -> {
            AiDailyUsage fresh = new AiDailyUsage();
            fresh.setUserId(userId);
            fresh.setUsageDate(date);
            return fresh;
        });
        long reserve = costMicros(MAX_INPUT_TOKENS, 0, maxOutputTokens);
        long limit = prices.getDailyUsd().movePointRight(6).longValueExact();
        if (day.getChargedMicros() + reserve > limit) {
            long seconds = Math.max(1, Duration.between(clock.instant(), date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)).toSeconds());
            throw new AiBudgetExceededException(seconds);
        }
        day.setChargedMicros(day.getChargedMicros() + reserve);
        day.setUnsettledRequests(day.getUnsettledRequests() + 1);
        usage.save(day);
        return new Reservation(userId, date, reserve, maxOutputTokens);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(Reservation reservation, long input, long cached, long output) {
        if (input < 0 || cached < 0 || cached > input || output < 0) return;
        if (users.findLockedById(reservation.userId()).isEmpty()) return; // Account deleted during provider call.
        usage.findByUserIdAndUsageDate(reservation.userId(), reservation.date()).ifPresent(day -> {
            day.setChargedMicros(day.getChargedMicros() - reservation.micros() + costMicros(input, cached, output));
            day.setInputTokens(day.getInputTokens() + input);
            day.setCachedInputTokens(day.getCachedInputTokens() + cached);
            day.setOutputTokens(day.getOutputTokens() + output);
            day.setUnsettledRequests(day.getUnsettledRequests() - 1);
        });
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID userId) {
        LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        AiDailyUsage day = usage.findByUserIdAndUsageDate(userId, date).orElseGet(AiDailyUsage::new);
        BigDecimal charged = BigDecimal.valueOf(day.getChargedMicros(), 6);
        return new Usage(date, date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), prices.getDailyUsd(), charged,
                prices.getDailyUsd().subtract(charged).max(BigDecimal.ZERO), day.getInputTokens(),
                day.getCachedInputTokens(), day.getOutputTokens(), day.getUnsettledRequests());
    }

    private long costMicros(long input, long cached, long output) {
        // USD/million tokens multiplied by token count yields micro-USD. Round upwards, never floating point.
        return prices.getInputPrice().multiply(BigDecimal.valueOf(input - cached))
                .add(prices.getCachedInputPrice().multiply(BigDecimal.valueOf(cached)))
                .add(prices.getOutputPrice().multiply(BigDecimal.valueOf(output)))
                .setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
