package io.github.mkliszczun.fridge.ai;

import io.github.mkliszczun.fridge.repository.UserRepository;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.security.abuse.GuardLocks;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AiBudgetService.class);
    public static final long MAX_INPUT_TOKENS = 128_000;
    public record Reservation(UUID userId, LocalDate date, long micros, int maxOutputTokens) {}
    public record Usage(LocalDate date, Instant resetsAt, BigDecimal limitUsd, BigDecimal estimatedCostUsd,
                        BigDecimal remainingUsd, long uses, Integer useLimit, long inputTokens, long cachedInputTokens,
                        long cacheWriteTokens, long outputTokens, long unsettledRequests) {}
    private final AiDailyUsageRepository usage;
    private final UserRepository users;
    private final AiBudgetProperties prices;
    private final Clock clock;
    private final GuardLocks locks;
    private final AiGlobalDailyUsageRepository globalUsage;
    private final EntityManager entityManager;

    public AiBudgetService(AiDailyUsageRepository usage, UserRepository users, AiBudgetProperties prices, Clock clock,
                          GuardLocks locks, AiGlobalDailyUsageRepository globalUsage, EntityManager entityManager) {
        this.usage = usage;
        this.users = users;
        this.prices = prices;
        this.clock = clock;
        this.locks = locks;
        this.globalUsage = globalUsage;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(UUID userId, long tokenVersion, int maxOutputTokens, boolean newUse) {
        if (!prices.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI is temporarily disabled");
        if (maxOutputTokens < 1 || maxOutputTokens > 8192) throw new IllegalArgumentException("Invalid output limit");
        var user = users.findLockedById(userId).orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        entityManager.refresh(user);
        new AccountStatusUserDetailsChecker().check(AppUserDetails.fromEntity(user));
        if (user.getTokenVersion() != tokenVersion) throw new BadCredentialsException("Session revoked");
        Instant now = clock.instant();
        LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        AiDailyUsage day = fresh(usage.findByUserIdAndUsageDate(userId, date)).orElseGet(() -> {
            AiDailyUsage fresh = new AiDailyUsage();
            fresh.setUserId(userId);
            fresh.setUsageDate(date);
            return fresh;
        });
        long reserve = prices.getInputPrice().max(prices.getCacheWritePrice()).multiply(BigDecimal.valueOf(MAX_INPUT_TOKENS))
                .add(prices.getOutputPrice().multiply(BigDecimal.valueOf(maxOutputTokens)))
                .setScale(0, RoundingMode.CEILING).longValueExact();
        boolean premium = isPremium(user.getPremiumUntil(), now);
        long limit = dailyLimit(premium).movePointRight(6).longValueExact();
        if ((newUse && !premium && day.getUseCount() >= prices.getFreeDailyUses())
                || day.getChargedMicros() + reserve > limit) throw limitExceeded(now, date);
        locks.lock("ai");
        AiGlobalDailyUsage global = fresh(globalUsage.findById(date)).orElseGet(() -> {
            AiGlobalDailyUsage fresh = new AiGlobalDailyUsage();
            fresh.setUsageDate(date);
            return fresh;
        });
        long globalLimit = prices.getGlobalDailyUsd().movePointRight(6).longValueExact();
        if (global.getChargedMicros() + reserve > globalLimit) throw limitExceeded(now, date);
        global.setChargedMicros(global.getChargedMicros() + reserve);
        if (!global.isWarningSent() && global.getChargedMicros() >= globalLimit * 8 / 10) {
            log.warn("AI_GLOBAL_BUDGET_WARNING date={} reservedAndChargedUsd={} limitUsd={}",
                    date, BigDecimal.valueOf(global.getChargedMicros(), 6), prices.getGlobalDailyUsd());
            global.setWarningSent(true);
        }
        globalUsage.save(global);
        day.setChargedMicros(day.getChargedMicros() + reserve);
        day.setUnsettledRequests(day.getUnsettledRequests() + 1);
        if (newUse) day.setUseCount(day.getUseCount() + 1);
        usage.save(day);
        return new Reservation(userId, date, reserve, maxOutputTokens);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(Reservation reservation, long input, long cached, long writes, long output) {
        if (input < 0 || cached < 0 || cached > input || writes < 0 || writes > input - cached || output < 0) return;
        boolean userExists = users.findLockedById(reservation.userId()).isPresent();
        locks.lock("ai");
        long cost = costMicros(input, cached, writes, output);
        fresh(globalUsage.findById(reservation.date())).ifPresent(day ->
                day.setChargedMicros(day.getChargedMicros() - reservation.micros() + cost));
        if (userExists) fresh(usage.findByUserIdAndUsageDate(reservation.userId(), reservation.date())).ifPresent(day -> {
            day.setChargedMicros(day.getChargedMicros() - reservation.micros() + costMicros(input, cached, writes, output));
            day.setInputTokens(day.getInputTokens() + input);
            day.setCachedInputTokens(day.getCachedInputTokens() + cached);
            day.setCacheWriteTokens(day.getCacheWriteTokens() + writes);
            day.setOutputTokens(day.getOutputTokens() + output);
            day.setUnsettledRequests(day.getUnsettledRequests() - 1);
        });
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID userId) {
        Instant now = clock.instant();
        var user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        boolean premium = isPremium(user.getPremiumUntil(), now);
        BigDecimal limit = dailyLimit(premium);
        LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        AiDailyUsage day = usage.findByUserIdAndUsageDate(userId, date).orElseGet(AiDailyUsage::new);
        BigDecimal charged = BigDecimal.valueOf(day.getChargedMicros(), 6);
        return new Usage(date, date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), limit, charged,
                limit.subtract(charged).max(BigDecimal.ZERO), day.getUseCount(),
                premium ? null : prices.getFreeDailyUses(), day.getInputTokens(), day.getCachedInputTokens(),
                day.getCacheWriteTokens(), day.getOutputTokens(), day.getUnsettledRequests());
    }

    private BigDecimal dailyLimit(boolean premium) {
        return premium ? prices.getPremiumDailyUsd() : prices.getFreeDailyUsd();
    }

    private <T> java.util.Optional<T> fresh(java.util.Optional<T> entity) {
        // OSIV can keep the same persistence context across reserve/settle/retry transactions.
        // A row lock alone does not refresh an entity already cached in that context.
        entity.ifPresent(entityManager::refresh);
        return entity;
    }

    private boolean isPremium(Instant premiumUntil, Instant now) {
        return premiumUntil != null && premiumUntil.isAfter(now);
    }

    private AiBudgetExceededException limitExceeded(Instant now, LocalDate date) {
        long seconds = Math.max(1, Duration.between(now,
                date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)).toSeconds());
        return new AiBudgetExceededException(seconds);
    }

    private long costMicros(long input, long cached, long writes, long output) {
        // USD/million tokens multiplied by token count yields micro-USD. Round upwards, never floating point.
        return prices.getInputPrice().multiply(BigDecimal.valueOf(input - cached - writes))
                .add(prices.getCachedInputPrice().multiply(BigDecimal.valueOf(cached)))
                .add(prices.getCacheWritePrice().multiply(BigDecimal.valueOf(writes)))
                .add(prices.getOutputPrice().multiply(BigDecimal.valueOf(output)))
                .setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
