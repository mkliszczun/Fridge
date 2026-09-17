package io.github.mkliszczun.fridge.security.abuse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuthRateLimiter {
    private final GuardLocks locks;
    private final AuthRateBucketRepository buckets;
    private final AbuseProperties limits;
    private final Clock clock;

    public AuthRateLimiter(GuardLocks locks, AuthRateBucketRepository buckets, AbuseProperties limits, Clock clock) {
        this.locks = locks;
        this.buckets = buckets;
        this.limits = limits;
        this.clock = clock;
    }

    // Commits even failed authentication attempts; shared by every application instance.
    // Return seconds until retry, or zero when admitted. Never store the raw IP.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long check(String path, String client) {
        if (!limits.isEnabled()) return 0;
        int count;
        int seconds = 900;
        switch (path) {
            case "/auth/login" -> count = limits.getLoginPer15Minutes();
            case "/auth/register" -> { count = limits.getRegistrationsPerHour(); seconds = 3600; }
            case "/auth/password/forgot", "/auth/password/reset" -> count = limits.getPasswordRequestsPer15Minutes();
            case "/auth/refresh" -> count = limits.getRefreshPer15Minutes();
            default -> { return 0; }
        }
        locks.lock("auth");
        Instant now = clock.instant();
        buckets.deleteExpired(now);
        if (path.equals("/auth/register")) {
            var global = buckets.findById("register:global").orElse(null);
            if (global != null && global.getAttempts() >= limits.getGlobalRegistrationsPerDay()) {
                return Math.max(1, global.getExpiresAt().getEpochSecond() - now.getEpochSecond() + 1);
            }
        }
        long retry = consume(path + ":" + hash(client), count, seconds, now);
        if (retry == 0 && path.equals("/auth/register")) {
            retry = consume("register:global", limits.getGlobalRegistrationsPerDay(), 86400, now);
        }
        return retry;
    }

    private long consume(String key, int limit, int seconds, Instant now) {
        AuthRateBucket bucket = buckets.findById(key).orElseGet(() -> {
            AuthRateBucket fresh = new AuthRateBucket();
            fresh.setId(key);
            fresh.setExpiresAt(now.plusSeconds(seconds));
            return fresh;
        });
        if (bucket.getAttempts() >= limit) {
            return Math.max(1, bucket.getExpiresAt().getEpochSecond() - now.getEpochSecond() + 1);
        }
        bucket.setAttempts(bucket.getAttempts() + 1);
        buckets.save(bucket);
        return 0;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
