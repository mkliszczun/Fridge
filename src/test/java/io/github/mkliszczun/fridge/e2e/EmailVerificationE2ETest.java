package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.account.*;
import io.github.mkliszczun.fridge.ai.AiBudgetService;
import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.enums.Role;
import io.github.mkliszczun.fridge.repository.UserRepository;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.FridgeService;
import io.github.mkliszczun.fridge.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationE2ETest extends VerifiedAccountTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired EmailVerificationRepository flows;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountService accounts;
    @Autowired AiBudgetService budget;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired FridgeService fridges;
    @MockitoBean Clock clock;
    @MockitoBean PasswordResetMailer resetMailer;
    final AtomicReference<Instant> now = new AtomicReference<>();
    static final String PASSWORD = "Secret123!";

    @BeforeEach void time() {
        now.set(Instant.parse("2026-09-17T12:00:00Z"));
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test void registrationCreatesNoAccountUntilVerificationThenIssuesFreeUserTokens() throws Exception {
        String email = email();
        JsonNode pending = body(request("/auth/register", Map.of("login", email, "password", PASSWORD,
                "roles", List.of("ADMIN"), "premium", true)).andExpect(status().isAccepted()));
        String token = pending.path("verificationToken").asText();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(pending.has("token")).isFalse();
        assertThat(users.findByUsername(email)).isEmpty();
        assertThat(Instant.parse(pending.path("expiresAt").asText())).isEqualTo(now.get().plusSeconds(1800));
        var flow = flows.findById(hash(token)).orElseThrow();
        assertThat(flow.getPendingPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, flow.getPendingPasswordHash())).isTrue();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        request("/auth/refresh", Map.of("refreshToken", token)).andExpect(status().isUnauthorized());
        String code = send(token, null, email);
        assertThat(code).matches("[0-9]{6}");
        assertThat(flows.findById(hash(token)).orElseThrow().getCodeHash()).isNotEqualTo(code);
        assertThat(passwords.matches(code, flows.findById(hash(token)).orElseThrow().getCodeHash())).isTrue();
        JsonNode tokens = body(verifyCode(token, code).andExpect(status().isOk()));
        UserEntity user = users.findByUsername(email).orElseThrow();
        assertThat(user.getRoles()).containsExactly(Role.USER);
        assertThat(user.getPremiumUntil()).isNull();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(now.get());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + tokens.path("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("FREE"));
        request("/auth/refresh", Map.of("refreshToken", tokens.path("refreshToken").asText())).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isOk());
        verifyCode(token, code).andExpect(status().isBadRequest());
        request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isBadRequest());
    }

    @Test void existingAccountKeepsLoginRolesPremiumAndFridgeWhenAddressIsCorrected() throws Exception {
        UserEntity user = legacy(true);
        UUID fridge = fridges.createFridge("Existing data", user.getId()).getId();
        user.setRoles(new HashSet<>(Set.of(Role.USER, Role.ADMIN)));
        user.setPremiumUntil(now.get().plusSeconds(86400));
        users.save(user);
        String token = challenge(user);
        String corrected = email();
        String code = send(token, corrected, corrected);
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
        String access = body(verifyCode(token, code).andExpect(status().isOk())).path("token").asText();
        UserEntity saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getUsername()).isEqualTo(user.getUsername());
        assertThat(saved.getEmail()).isEqualTo(corrected);
        assertThat(saved.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(saved.getPremiumUntil()).isEqualTo(user.getPremiumUntil());
        mvc.perform(get("/api/fridges").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(fridge.toString()));
        login(user.getUsername(), PASSWORD).andExpect(status().isOk());
        login(corrected, PASSWORD).andExpect(status().isOk());
    }

    @Test void invalidLegacyAddressRequiresInputAndDefaultValidAddressWorks() throws Exception {
        UserEntity user = legacy(false);
        JsonNode pending = body(login(user.getUsername(), PASSWORD).andExpect(status().isAccepted()));
        assertThat(pending.path("emailRequired").asBoolean()).isTrue();
        String token = pending.path("verificationToken").asText();
        request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isBadRequest());
        String corrected = email();
        verifyCode(token, send(token, corrected, corrected)).andExpect(status().isOk());
        UserEntity other = legacy(true);
        String otherToken = challenge(other);
        verifyCode(otherToken, send(otherToken, null, other.getEmail())).andExpect(status().isOk());
    }

    @Test void resendInvalidatesOldCodeAndChangingTargetDoesNotChangeEmailBeforeSuccess() throws Exception {
        UserEntity user = legacy(true);
        String token = challenge(user);
        String original = send(token, null, user.getEmail());
        advance(60);
        String nextEmail = email();
        String replacement = send(token, nextEmail, nextEmail);
        verifyCode(token, original).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
        verifyCode(token, replacement).andExpect(status().isOk());
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(nextEmail);
    }

    @Test void codeExpiresAfterTenMinutesAndProcessAfterThirtyMinutes() throws Exception {
        String email = email();
        String token = register(email);
        String code = send(token, null, email);
        advance(600);
        verifyCode(token, code).andExpect(status().isBadRequest());
        advance(1190);
        JsonNode delivery = body(request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isOk()));
        assertThat(Instant.parse(delivery.path("codeExpiresAt").asText())).isEqualTo(now.get().plusSeconds(10));
        advance(10);
        verifyCode(token, lastCode(email)).andExpect(status().isBadRequest());
        request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isBadRequest());
        assertThat(users.findByUsername(email)).isEmpty();
    }

    @Test void cooldownAndSendQuotaSurviveNewTokensForSameAccountAndAddress() throws Exception {
        UserEntity user = legacy(true);
        for (int i = 0; i < 5; i++) {
            String token = challenge(user);
            send(token, null, user.getEmail());
            String sibling = challenge(user);
            request("/auth/email/send", Map.of("verificationToken", sibling, "email", email()))
                    .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
            advance(60);
        }
        request("/auth/email/send", Map.of("verificationToken", challenge(user)))
                .andExpect(status().isTooManyRequests());
        // A separate pending registration still shares the address quota.
        String shared = email();
        send(register(shared), null, shared);
        request("/auth/email/send", Map.of("verificationToken", register(shared))).andExpect(status().isTooManyRequests());
        advance(3600);
        send(challenge(user), null, user.getEmail());
    }

    @Test void wrongAttemptQuotaSurvivesResendAndTokenRotationAndRecoversAfterFifteenMinutes() throws Exception {
        UserEntity user = legacy(true);
        String token = challenge(user);
        String code = send(token, null, user.getEmail());
        for (int i = 0; i < 4; i++) verifyCode(token, wrong(code)).andExpect(status().isBadRequest());
        advance(60);
        code = send(token, null, user.getEmail());
        verifyCode(token, wrong(code)).andExpect(status().isBadRequest());
        verifyCode(token, code).andExpect(status().isTooManyRequests());
        String sibling = challenge(user);
        verifyCode(sibling, "000000").andExpect(status().isTooManyRequests());
        request("/auth/email/send", Map.of("verificationToken", sibling, "email", email()))
                .andExpect(status().isTooManyRequests());
        advance(901);
        verifyCode(sibling, send(sibling, null, user.getEmail())).andExpect(status().isOk());
    }

    @Test void failedAttemptsAreAlsoSharedByAddressAcrossPendingRegistrations() throws Exception {
        String email = email();
        String token = register(email);
        String code = send(token, null, email);
        for (int i = 0; i < 5; i++) verifyCode(token, wrong(code)).andExpect(status().isBadRequest());
        String sibling = register(email);
        verifyCode(sibling, code).andExpect(status().isTooManyRequests());
        request("/auth/email/send", Map.of("verificationToken", sibling)).andExpect(status().isTooManyRequests());
    }

    @Test void foreignCodesTokensAndAddressClaimsDoNotGrantAccess() throws Exception {
        String email = email();
        String first = register(email);
        String code = send(first, null, email);
        String other = register(email());
        verifyCode(other, code).andExpect(status().isBadRequest());
        verifyCode("A".repeat(43), code).andExpect(status().isBadRequest());
        request("/auth/email/send", Map.of("verificationToken", first, "email", email())).andExpect(status().isBadRequest());
        UserEntity owner = legacy(true);
        owner.setUsername(email()); // Reserved by another account's LOGIN, not email.
        users.save(owner);
        String pending = challenge(legacy(false));
        request("/auth/email/send", Map.of("verificationToken", pending, "email", owner.getUsername().toUpperCase(Locale.ROOT)))
                .andExpect(status().isConflict());
        request("/auth/email/send", Map.of("verificationToken", pending, "email", owner.getEmail()))
                .andExpect(status().isConflict());
        request("/auth/register", Map.of("login", owner.getEmail(), "password", PASSWORD)).andExpect(status().isConflict());
        assertThat(users.findByUsername(email)).isEmpty();
    }

    @Test void smtpFailureRollsBackNewCodeAndAllowsRetryWithSameProof() throws Exception {
        String email = email();
        String token = register(email);
        doThrow(new MailSendException("Private SMTP diagnostic")).when(verificationMailer).send(eq(email), anyString());
        request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Email delivery unavailable; retry with the same verification token"));
        assertThat(flows.findById(hash(token)).orElseThrow().getCodeHash()).isNull();
        assertThat(users.findByUsername(email)).isEmpty();
        doNothing().when(verificationMailer).send(eq(email), anyString());
        verifyCode(token, send(token, null, email)).andExpect(status().isOk());
    }

    @Test void failedResendPreservesThePreviouslyDeliveredCode() throws Exception {
        String email = email();
        String token = register(email);
        String original = send(token, null, email);
        advance(60);
        doThrow(new MailSendException("delivery failed")).when(verificationMailer).send(eq(email), anyString());
        request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isServiceUnavailable());
        verifyCode(token, original).andExpect(status().isOk());
    }

    @Test void successfulVerificationInvalidatesSiblingProcessesAndOldAddressResetLink() throws Exception {
        UserEntity user = legacy(true);
        String first = challenge(user);
        String sibling = challenge(user);
        request("/auth/password/forgot", Map.of("email", user.getEmail())).andExpect(status().isAccepted());
        var reset = ArgumentCaptor.forClass(String.class);
        verify(resetMailer).send(eq(user.getEmail()), reset.capture());
        String corrected = email();
        verifyCode(first, send(first, corrected, corrected)).andExpect(status().isOk());
        request("/auth/email/send", Map.of("verificationToken", sibling)).andExpect(status().isBadRequest());
        verifyCode(sibling, "000000").andExpect(status().isBadRequest());
        request("/auth/password/reset", Map.of("token", reset.getValue(), "password", "NewPassword123!"))
                .andExpect(status().isBadRequest());
        login(user.getUsername(), PASSWORD).andExpect(status().isOk());
    }

    @Test void disabledAccountCannotCompleteAnExistingProcess() throws Exception {
        UserEntity user = legacy(true);
        String token = challenge(user);
        String code = send(token, null, user.getEmail());
        user.setEnabled(false);
        users.save(user);
        verifyCode(token, code).andExpect(status().isUnauthorized());
        request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isUnauthorized());
        login(user.getUsername(), PASSWORD).andExpect(status().isUnauthorized());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test void normalizedEmailSupportsLoginAndPasswordResetWithoutChangingLegacyUsernameRules() throws Exception {
        String email = email();
        String token = register(email.toUpperCase(Locale.ROOT));
        verifyCode(token, send(token, null, email)).andExpect(status().isOk());
        login(email.toUpperCase(Locale.ROOT), PASSWORD).andExpect(status().isOk());
        request("/auth/password/forgot", Map.of("email", email.toUpperCase(Locale.ROOT))).andExpect(status().isAccepted());
        verify(resetMailer).send(eq(email), anyString());
    }

    @Test void passwordResetInvalidatesProcessWithoutVerifyingTheAccount() throws Exception {
        UserEntity user = legacy(true);
        String token = challenge(user);
        String code = send(token, null, user.getEmail());
        request("/auth/password/forgot", Map.of("email", user.getEmail())).andExpect(status().isAccepted());
        var reset = ArgumentCaptor.forClass(String.class);
        verify(resetMailer).send(eq(user.getEmail()), reset.capture());
        request("/auth/password/reset", Map.of("token", reset.getValue(), "password", "NewPassword123!"))
                .andExpect(status().isNoContent());
        verifyCode(token, code).andExpect(status().isBadRequest());
        request("/auth/email/send", Map.of("verificationToken", token)).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
        login(user.getUsername(), PASSWORD).andExpect(status().isUnauthorized());
        login(user.getUsername(), "NewPassword123!").andExpect(status().isAccepted()).andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test void oldJwtRefreshAndDirectAiCannotBypassVerificationEvenWithCurrentVersion() throws Exception {
        UserEntity user = legacy(true);
        String access = jwt.generateToken(user.getUsername(), user.getId(), List.of("USER"), user.getTokenVersion());
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbc.update("insert into refresh_token(token_hash, user_id, token_version, expires_at, used) values (?, ?, ?, ?, false)",
                hash(refresh), user.getId(), user.getTokenVersion(), java.sql.Timestamp.from(now.get().plusSeconds(86400)));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        request("/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> accounts.issue(AppUserDetails.fromEntity(user))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> budget.reserve(user.getId(), user.getTokenVersion(), 100, true)).isInstanceOf(BadCredentialsException.class);
        String token = challenge(user);
        verifyCode(token, send(token, null, user.getEmail())).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        request("/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
    }

    @Test void concurrentConfirmationUsesSameCodeOnlyOnce() throws Exception {
        String email = email();
        String token = register(email);
        String code = send(token, null, email);
        assertThat(concurrent(() -> verifyCode(token, code).andReturn().getResponse().getStatus(),
                () -> verifyCode(token, code).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200, 400);
        assertThat(users.findAddressOwners(email)).hasSize(1);
        UUID id = users.findByUsername(email).orElseThrow().getId();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test void concurrentSeparateProcessesCannotCreateDuplicateAccounts() throws Exception {
        String email = email();
        String first = register(email);
        String firstCode = send(first, null, email);
        advance(60);
        String second = register(email);
        String secondCode = send(second, null, email);
        assertThat(concurrent(() -> verifyCode(first, firstCode).andReturn().getResponse().getStatus(),
                () -> verifyCode(second, secondCode).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
        assertThat(users.findAddressOwners(email)).hasSize(1);
    }

    @Test void concurrentWrongAttemptsCannotExceedFive() throws Exception {
        String email = email();
        String token = register(email);
        String bad = wrong(send(token, null, email));
        List<Callable<Integer>> actions = new ArrayList<>();
        for (int i = 0; i < 8; i++) actions.add(() -> verifyCode(token, bad).andReturn().getResponse().getStatus());
        List<Integer> statuses = concurrent(actions);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(5);
        assertThat(statuses.stream().filter(s -> s == 429).count()).isEqualTo(3);
    }

    private List<Integer> concurrent(Callable<Integer> first, Callable<Integer> second) throws Exception {
        return concurrent(List.of(first, second));
    }
    private List<Integer> concurrent(List<Callable<Integer>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CyclicBarrier barrier = new CyclicBarrier(actions.size());
        try {
            List<Callable<Integer>> gated = actions.stream().<Callable<Integer>>map(action -> () -> {
                barrier.await(10, TimeUnit.SECONDS); return action.call();
            }).toList();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(gated, 30, TimeUnit.SECONDS)) results.add(result.get());
            return results;
        } finally { pool.shutdownNow(); }
    }

    private UserEntity legacy(boolean validEmail) {
        UserEntity user = new UserEntity();
        user.setUsername("legacy-" + UUID.randomUUID());
        user.setEmail(validEmail ? email() : user.getUsername());
        user.setPassword(passwords.encode(PASSWORD));
        user.setRoles(new HashSet<>(Set.of(Role.USER)));
        return users.save(user);
    }
    private String challenge(UserEntity user) throws Exception {
        return body(login(user.getUsername(), PASSWORD).andExpect(status().isAccepted())).path("verificationToken").asText();
    }
    private ResultActions login(String login, String password) throws Exception {
        return request("/auth/login", Map.of("login", login, "password", password));
    }
    private String register(String email) throws Exception {
        return body(request("/auth/register", Map.of("login", email, "password", PASSWORD)).andExpect(status().isAccepted()))
                .path("verificationToken").asText();
    }
    private String send(String token, String target, String recipient) throws Exception {
        request("/auth/email/send", target == null ? Map.of("verificationToken", token)
                : Map.of("verificationToken", token, "email", target)).andExpect(status().isOk());
        return lastCode(recipient);
    }
    private String lastCode(String email) {
        var code = ArgumentCaptor.forClass(String.class);
        verify(verificationMailer, atLeastOnce()).send(eq(email), code.capture());
        return code.getValue();
    }
    private ResultActions verifyCode(String token, String code) throws Exception {
        return request("/auth/email/verify", Map.of("verificationToken", token, "code", code));
    }
    private ResultActions request(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private JsonNode body(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }
    private void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
    private static String email() { return UUID.randomUUID() + "@test.local"; }
    private static String wrong(String code) { return code.equals("000000") ? "000001" : "000000"; }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
