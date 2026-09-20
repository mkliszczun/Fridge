package io.github.mkliszczun.fridge.account;

import io.github.mkliszczun.fridge.dto.RegisterRequest;
import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.enums.Role;
import io.github.mkliszczun.fridge.repository.UserRepository;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.security.abuse.AuthRateBucket;
import io.github.mkliszczun.fridge.security.abuse.AuthRateBucketRepository;
import io.github.mkliszczun.fridge.security.abuse.GuardLocks;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class EmailVerificationService {
    public record Challenge(String verificationToken, Instant expiresAt, String email, boolean emailRequired) {}
    public record Delivery(Instant codeExpiresAt, Instant resendAvailableAt) {}
    private record Address(@NotBlank @Email @Size(max = 254) String value) {}
    private record Context(EmailVerification flow, UserEntity user) {}
    private record Result(AccountService.Tokens tokens, ResponseStatusException error) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final EmailVerificationRepository flows;
    private final UserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwords;
    private final EmailVerificationMailer mailer;
    private final GuardLocks locks;
    private final AuthRateBucketRepository buckets;
    private final EntityManager em;
    private final Clock clock;
    private final Validator validator;
    private final TransactionTemplate tx;

    public EmailVerificationService(EmailVerificationRepository flows, UserRepository users, AccountService accounts,
                                    PasswordEncoder passwords, EmailVerificationMailer mailer, GuardLocks locks,
                                    AuthRateBucketRepository buckets, EntityManager em, Clock clock,
                                    Validator validator, PlatformTransactionManager manager) {
        this.flows = flows; this.users = users; this.accounts = accounts; this.passwords = passwords;
        this.mailer = mailer; this.locks = locks; this.buckets = buckets; this.em = em;
        this.clock = clock; this.validator = validator; this.tx = new TransactionTemplate(manager);
    }

    public Challenge register(RegisterRequest request) {
        AccountService.validatePassword(request.password());
        String email = normalize(request.login());
        requireEmail(email);
        String passwordHash = passwords.encode(request.password());
        return tx.execute(status -> {
            guard();
            requireAvailable(email, null);
            EmailVerification flow = new EmailVerification();
            flow.setPendingUsername(email);
            flow.setPendingPasswordHash(passwordHash);
            flow.setEmail(email);
            return create(flow);
        });
    }

    public Challenge startExisting(AppUserDetails authenticated) {
        return tx.execute(status -> {
            guard();
            UserEntity user = lockedUser(authenticated.getId());
            if (user.isEmailVerified() || user.getTokenVersion() != authenticated.getTokenVersion()
                    || !user.getPassword().equals(authenticated.getPassword())) {
                throw new BadCredentialsException("Authentication changed; sign in again");
            }
            EmailVerification flow = new EmailVerification();
            flow.setUserId(user.getId());
            flow.setTokenVersion(user.getTokenVersion());
            String email = normalize(user.getEmail());
            if (validEmail(email)) flow.setEmail(email);
            return create(flow);
        });
    }

    private Challenge create(EmailVerification flow) {
        String secret = SecretTokens.generate();
        flow.setTokenHash(SecretTokens.hash(secret));
        flow.setExpiresAt(clock.instant().plusSeconds(1800));
        flows.save(flow);
        return new Challenge(secret, flow.getExpiresAt(), flow.getEmail(), flow.getEmail() == null);
    }

    public Delivery send(String secret, String requestedEmail) {
        try {
            return tx.execute(status -> {
                guard();
                Context context = context(secret);
                EmailVerification flow = context.flow();
                String email = requestedEmail == null ? flow.getEmail() : normalize(requestedEmail);
                requireEmail(email);
                // A pending registration cannot claim someone else's login by verifying a different address.
                if (flow.getUserId() == null && !email.equals(flow.getPendingUsername())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start a new registration to change the address");
                }
                requireAvailable(email, flow.getUserId());
                Instant now = clock.instant();
                Set<String> scopes = scopes(flow, email);
                requireAttemptsAvailable(scopes, now);
                for (String scope : scopes) {
                    List<AuthRateBucket> sends = events("send", scope, now);
                    if (sends.size() >= 5) throw limited(earliestExpiry(sends), now);
                    Instant resend = sends.stream().map(b -> b.getExpiresAt().minusSeconds(3540))
                            .max(Instant::compareTo).orElse(now);
                    if (resend.isAfter(now)) throw limited(resend, now);
                }
                String code;
                do {
                    code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
                } while (flow.getCodeHash() != null && passwords.matches(code, flow.getCodeHash()));
                flow.setEmail(email);
                flow.setCodeHash(passwords.encode(code));
                flow.setCodeExpiresAt(now.plusSeconds(600).isBefore(flow.getExpiresAt())
                        ? now.plusSeconds(600) : flow.getExpiresAt());
                recordEvents("send", scopes, now.plusSeconds(3600));
                // SMTP has bounded timeouts. Keep code/counters atomic: failed delivery rolls them back.
                // The shared email guard also serializes competing registrations/address claims across machines.
                mailer.send(email, code);
                return new Delivery(flow.getCodeExpiresAt(), now.plusSeconds(60));
            });
        } catch (MailException ex) {
            // Do not return/log SMTP diagnostics: they may contain credentials or message contents.
            throw new EmailDeliveryException();
        }
    }

    public AccountService.Tokens verify(String secret, String code) {
        Result result = tx.execute(status -> {
            guard();
            Context context = context(secret);
            EmailVerification flow = context.flow();
            Instant now = clock.instant();
            Set<String> scopes = scopes(flow, flow.getEmail());
            requireAttemptsAvailable(scopes, now);
            if (flow.getCodeHash() == null || !flow.getCodeExpiresAt().isAfter(now)
                    || !passwords.matches(code, flow.getCodeHash())) {
                recordEvents("failure", scopes, now.plusSeconds(900));
                // Return the error and throw AFTER commit, otherwise failed-attempt counters would roll back.
                return new Result(null, new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code"));
            }
            requireAvailable(flow.getEmail(), flow.getUserId());
            UserEntity user = context.user();
            if (user == null) {
                user = new UserEntity();
                user.setUsername(flow.getPendingUsername());
                user.setPassword(flow.getPendingPasswordHash());
                user.setRoles(new HashSet<>(Set.of(Role.USER)));
            } else {
                user.revokeSessions(); // Invalidate all sibling verification processes and prior sessions.
                user.setPasswordResetHash(null); // A link sent to the old address cannot reset this account.
                user.setPasswordResetExpiresAt(null);
            }
            user.setEmail(flow.getEmail());
            user.setEmailVerifiedAt(now);
            users.saveAndFlush(user);
            flow.setConsumedAt(now);
            flow.setCodeHash(null);
            em.flush();
            return new Result(accounts.issue(AppUserDetails.fromEntity(user)), null);
        });
        if (result.error() != null) throw result.error();
        return result.tokens();
    }

    private void guard() {
        locks.lock("email");
        buckets.deleteExpiredEmailEvents(clock.instant());
    }

    private Context context(String secret) {
        EmailVerification flow = flows.findById(SecretTokens.hash(secret)).orElseThrow(EmailVerificationService::invalidProcess);
        // Lock order: email guard -> user -> process writes. Account deletion never takes the email guard.
        UserEntity user = flow.getUserId() == null ? null : lockedUser(flow.getUserId());
        if (flow.getConsumedAt() != null || !flow.getExpiresAt().isAfter(clock.instant())
                || (user != null && (user.isEmailVerified() || user.getTokenVersion() != flow.getTokenVersion()))) {
            throw invalidProcess();
        }
        return new Context(flow, user);
    }

    private UserEntity lockedUser(UUID id) {
        UserEntity user = users.findLockedById(id).orElseThrow(EmailVerificationService::invalidProcess);
        em.refresh(user);
        new AccountStatusUserDetailsChecker().check(AppUserDetails.fromEntity(user));
        return user;
    }

    private void requireAvailable(String email, UUID owner) {
        if (users.findAddressOwners(email).stream().anyMatch(u -> !u.getId().equals(owner))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Address unavailable");
        }
    }

    private Set<String> scopes(EmailVerification flow, String email) {
        Set<String> result = new HashSet<>();
        if (flow.getUserId() != null) result.add("user:" + flow.getUserId());
        if (email != null) result.add("address:" + email);
        return result;
    }

    private List<AuthRateBucket> events(String kind, String scope, Instant now) {
        return buckets.findByIdStartingWithAndExpiresAtAfter("email:" + kind + ":" + SecretTokens.hash(scope) + ":", now);
    }

    private void recordEvents(String kind, Set<String> scopes, Instant expiry) {
        for (String scope : scopes) {
            AuthRateBucket event = new AuthRateBucket();
            event.setId("email:" + kind + ":" + SecretTokens.hash(scope) + ":" + UUID.randomUUID());
            event.setAttempts(1);
            event.setExpiresAt(expiry);
            buckets.save(event);
        }
    }

    private void requireAttemptsAvailable(Set<String> scopes, Instant now) {
        Instant retry = null;
        for (String scope : scopes) {
            List<AuthRateBucket> failures = events("failure", scope, now);
            if (failures.size() >= 5) {
                Instant expiry = earliestExpiry(failures);
                if (retry == null || expiry.isAfter(retry)) retry = expiry;
            }
        }
        if (retry != null) throw limited(retry, now);
    }

    private static Instant earliestExpiry(List<AuthRateBucket> events) {
        return events.stream().map(AuthRateBucket::getExpiresAt).min(Instant::compareTo).orElseThrow();
    }

    private static ResponseStatusException limited(Instant until, Instant now) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Verification limit reached; try again later") {
            @Override public org.springframework.http.HttpHeaders getHeaders() {
                var headers = new org.springframework.http.HttpHeaders();
                headers.set("Retry-After", Long.toString(Math.max(1, until.getEpochSecond() - now.getEpochSecond() + 1)));
                return headers;
            }
        };
    }

    private void requireEmail(String email) {
        if (!validEmail(email)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
    }

    private boolean validEmail(String email) { return validator.validate(new Address(email)).isEmpty(); }
    private static String normalize(String email) { return email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    private static ResponseStatusException invalidProcess() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification process; sign in or register again");
    }
}
