package io.github.mkliszczun.fridge.account;

import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.repository.UserRepository;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.util.JwtUtil;
import io.github.mkliszczun.fridge.util.JwtProperties;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {
    public record Tokens(String token, String refreshToken, long expiresIn) {}
    public record Profile(UUID id, String email, String plan, Instant premiumUntil, boolean adsEnabled) {}

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtUtil jwt;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwords;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final EntityManager entityManager;

    public AccountService(UserRepository users, RefreshTokenRepository refreshTokens, JwtUtil jwt,
                          JwtProperties jwtProperties, PasswordEncoder passwords, PasswordResetMailer mailer,
                          Clock clock, PlatformTransactionManager transactions, EntityManager entityManager) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.jwtProperties = jwtProperties;
        this.passwords = passwords;
        this.mailer = mailer;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.entityManager = entityManager;
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters and at most 72 UTF-8 bytes");
        }
    }

    public Tokens issue(AppUserDetails authenticated) {
        return tx.execute(status -> {
            UserEntity user = locked(authenticated.getId());
            if (user.getTokenVersion() != authenticated.getTokenVersion()
                    || !user.getPassword().equals(authenticated.getPassword())) {
                throw new BadCredentialsException("Authentication changed; sign in again");
            }
            return newTokens(user, clock.instant().plus(Duration.ofDays(30)));
        });
    }

    private Tokens newTokens(UserEntity user, Instant expiry) {
        new AccountStatusUserDetailsChecker().check(AppUserDetails.fromEntity(user));
        String secret = SecretTokens.generate();
        RefreshToken refresh = new RefreshToken();
        refresh.setTokenHash(SecretTokens.hash(secret));
        refresh.setUserId(user.getId());
        refresh.setTokenVersion(user.getTokenVersion());
        refresh.setExpiresAt(expiry);
        refreshTokens.save(refresh);
        String access = jwt.generateToken(user.getUsername(), user.getId(),
                user.getRoles().stream().map(Enum::name).toList(), user.getTokenVersion());
        return new Tokens(access, secret, jwtProperties.getExpiration() / 1000L);
    }

    public Tokens refresh(String secret) {
        // Commit revocation on replay BEFORE returning 401. Throwing inside the transaction would undo it.
        Tokens result = tx.execute(status -> {
            RefreshToken token = refreshTokens.findById(SecretTokens.hash(secret)).orElse(null);
            if (token == null) return null;
            UserEntity user = users.findLockedById(token.getUserId()).orElse(null);
            if (user == null) return null;
            entityManager.refresh(token); // Another request may have rotated it while waiting for the user lock.
            if (!token.getExpiresAt().isAfter(clock.instant()) || token.getTokenVersion() != user.getTokenVersion()) return null;
            if (token.isUsed()) {
                user.revokeSessions();
                return null;
            }
            token.setUsed(true);
            return newTokens(user, token.getExpiresAt()); // absolute 30-day session lifetime
        });
        if (result == null) throw new BadCredentialsException("Invalid refresh token");
        return result;
    }

    public void logoutAll(UUID userId) {
        tx.executeWithoutResult(status -> locked(userId).revokeSessions());
    }

    public void forgotPassword(String email) {
        mailer.requireConfigured(); // Same response for existing/unknown addresses when mail is unavailable.
        tx.executeWithoutResult(status -> users.findByEmail(email).ifPresent(candidate -> {
            UserEntity user = locked(candidate.getId());
            Instant now = clock.instant();
            if (!user.isEnabled() || (user.getPasswordResetRequestedAt() != null
                    && user.getPasswordResetRequestedAt().plus(Duration.ofMinutes(5)).isAfter(now))) return;
            String secret = SecretTokens.generate();
            user.setPasswordResetHash(SecretTokens.hash(secret));
            user.setPasswordResetExpiresAt(now.plus(Duration.ofMinutes(30)));
            user.setPasswordResetRequestedAt(now);
            mailer.send(user.getEmail(), secret);
        }));
    }

    public void resetPassword(String secret, String password) {
        validatePassword(password);
        tx.executeWithoutResult(status -> {
            String hash = SecretTokens.hash(secret);
            UserEntity candidate = users.findByPasswordResetHash(hash).orElseThrow(AccountService::invalidReset);
            UserEntity user = locked(candidate.getId());
            if (!hash.equals(user.getPasswordResetHash()) || user.getPasswordResetExpiresAt() == null
                    || !user.getPasswordResetExpiresAt().isAfter(clock.instant()) || !user.isEnabled()) {
                throw invalidReset();
            }
            user.setPassword(passwords.encode(password));
            user.setPasswordResetHash(null);
            user.setPasswordResetExpiresAt(null);
            user.revokeSessions();
        });
    }

    public Profile profile(UUID userId) {
        return toProfile(users.findById(userId).orElseThrow(() -> new BadCredentialsException("Account unavailable")));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Profile setPremium(UUID userId, Instant until) {
        if (until != null && !until.isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Premium expiry must be in the future");
        }
        return tx.execute(status -> {
            UserEntity user = locked(userId);
            user.setPremiumUntil(until);
            return toProfile(user);
        });
    }

    private Profile toProfile(UserEntity user) {
        boolean premium = user.getPremiumUntil() != null && user.getPremiumUntil().isAfter(clock.instant());
        return new Profile(user.getId(), user.getEmail(), premium ? "PREMIUM" : "FREE", user.getPremiumUntil(), !premium);
    }

    private UserEntity locked(UUID id) {
        UserEntity user = users.findLockedById(id).orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        entityManager.refresh(user); // Avoid stale state from an earlier lookup in this persistence context.
        return user;
    }

    private static ResponseStatusException invalidReset() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
    }
}
