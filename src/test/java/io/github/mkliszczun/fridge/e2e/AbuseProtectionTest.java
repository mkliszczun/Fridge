package io.github.mkliszczun.fridge.e2e;

import io.github.mkliszczun.fridge.ai.*;
import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.repository.UserRepository;
import io.github.mkliszczun.fridge.security.abuse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"abuse.enabled=true", "abuse.login-per15-minutes=3",
        "abuse.registrations-per-hour=2", "abuse.global-registrations-per-day=3",
        "ai.budget.global-daily-usd=0.10"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext
class AbuseProtectionTest {
    @Autowired AuthRateLimiter limiter;
    @Autowired AuthRateBucketRepository buckets;
    @Autowired AiBudgetService budget;
    @Autowired AiBudgetProperties prices;
    @Autowired AbuseProperties abuse;
    @Autowired AiGlobalDailyUsageRepository globalUsage;
    @Autowired AiDailyUsageRepository userUsage;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @MockitoBean Clock clock;
    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");

    @BeforeEach void prepare() {
        when(clock.instant()).thenReturn(now);
        buckets.deleteAll();
        globalUsage.deleteAll();
        prices.setEnabled(true);
        abuse.setTrustFlyProxy(false);
    }

    @Test void failedLoginsAreLimitedBeforeAuthenticationAndSpoofedHeadersDoNotResetCounter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113." + i)
                            .header("Fly-Client-IP", "203.0.113." + i)
                            .content("{\"login\":\"absent@test.local\",\"password\":\"WrongPassword123\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        when(clock.instant()).thenReturn(now.plusSeconds(901));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void flyHeadersAreOptInAndIpv6InterfaceRotationDoesNotBypassLimit() throws Exception {
        abuse.setTrustFlyProxy(true);
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        for (int i = 1; i <= 2; i++) {
            mvc.perform(post("/auth/register").header("Fly-Client-IP", "2001:db8:1:2::" + i)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/auth/register").header("Fly-Client-IP", "2001:db8:1:2::99")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test void parallelRequestsShareTheDatabaseLimit() throws Exception {
        assertThat(parallel(20, i -> limiter.check("/auth/login", "one-client") == 0)).isEqualTo(3);
        assertThat(parallel(20, i -> limiter.check("/auth/register", "client-" + i) == 0)).isEqualTo(3);
        assertThat(buckets.findAll()).allSatisfy(bucket -> assertThat(bucket.getAttempts()).isPositive());
    }

    @Test void globalBudgetCannotBeMultipliedByAccountsOrConcurrentRequests() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) ids.add(user());
        assertThat(parallel(ids.size(), i -> {
            try { budget.reserve(ids.get(i), 0, 1, true); return true; }
            catch (AiBudgetExceededException exhausted) { return false; }
        })).isEqualTo(3);
        assertThat(globalUsage.findById(LocalDate.of(2026, 9, 16)).orElseThrow().getChargedMicros())
                .isEqualTo(96_006);
        // Simulate account deletion clearing per-user accounting. Global accounting must remain.
        userUsage.deleteAll();
        assertThatThrownBy(() -> budget.reserve(user(), 0, 1, true)).isInstanceOf(AiBudgetExceededException.class);
    }

    @Test void lateSettlementUsesOriginalDayEvenAfterAccountDeletion() {
        UUID id = user();
        var reservation = budget.reserve(id, 0, 1, true);
        userUsage.deleteAll();
        users.deleteById(id);
        when(clock.instant()).thenReturn(now.plusSeconds(86400));
        budget.settle(reservation, 100, 0, 0, 1);
        assertThat(globalUsage.findById(reservation.date()).orElseThrow().getChargedMicros()).isEqualTo(22);
        assertThatCode(() -> budget.reserve(user(), 0, 1, true)).doesNotThrowAnyException();
    }

    @Test void killSwitchDoesNotChargeTheUser() {
        prices.setEnabled(false);
        UUID id = user();
        assertThatThrownBy(() -> budget.reserve(id, 0, 1, true))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        assertThat(budget.usage(id).uses()).isZero();
        assertThat(globalUsage.count()).isZero();
    }

    @Test void requestScopedEntityCacheCannotOverwriteAnotherRequestsCost() throws Exception {
        UUID first = user();
        UUID second = user();
        var em = entityManagerFactory.createEntityManager();
        var pool = Executors.newSingleThreadExecutor();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                entityManagerFactory, new org.springframework.orm.jpa.EntityManagerHolder(em));
        try {
            var reservation = budget.reserve(first, 0, 1, true);
            pool.submit(() -> budget.reserve(second, 0, 1, true)).get(10, TimeUnit.SECONDS);
            budget.settle(reservation, 100, 0, 0, 1);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
            pool.shutdownNow();
        }
        assertThat(globalUsage.findById(LocalDate.of(2026, 9, 16)).orElseThrow().getChargedMicros())
                .isEqualTo(32_024);
    }

    private UUID user() {
        UserEntity user = new UserEntity();
        user.setUsername(UUID.randomUUID() + "@test.local");
        user.setEmail(user.getUsername());
        user.setPassword("test-only-hash");
        return users.save(user).getId();
    }

    private long parallel(int count, java.util.function.IntPredicate action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) { int index = i; tasks.add(() -> action.test(index)); }
            long passed = 0;
            for (Future<Boolean> result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) if (result.get()) passed++;
            return passed;
        } finally { pool.shutdownNow(); }
    }
}
