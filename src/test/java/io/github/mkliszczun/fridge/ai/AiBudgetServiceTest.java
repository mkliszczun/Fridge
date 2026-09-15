package io.github.mkliszczun.fridge.ai;

import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AiBudgetServiceTest {
    @Autowired AiBudgetService budget;
    @Autowired AiDailyUsageRepository usage;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;

    @BeforeEach void time() { when(clock.instant()).thenReturn(Instant.parse("2026-09-09T12:00:00Z")); }

    UUID user() {
        UserEntity user = new UserEntity();
        user.setUsername(UUID.randomUUID() + "@test.local");
        user.setEmail(user.getUsername());
        user.setPassword("unused-test-hash");
        return users.save(user).getId();
    }

    UUID premiumUser() {
        UUID id = user();
        UserEntity user = users.findById(id).orElseThrow();
        user.setPremiumUntil(Instant.parse("2026-10-01T00:00:00Z"));
        return users.save(user).getId();
    }

    @Test
    void settlesActualCachedInputAndOutputAndKeepsCostAfterOuterRollback() {
        UUID id = user();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var reserved = budget.reserve(id, 0, 4000, true);
            budget.settle(reserved, 1000, 500, 200, 100);
            throw new IllegalStateException("AI proposal rejected by domain validation");
        })).isInstanceOf(IllegalStateException.class);
        var result = budget.usage(id);
        assertThat(result.estimatedCostUsd()).isEqualByComparingTo("0.000240");
        assertThat(result.cacheWriteTokens()).isEqualTo(200);
        assertThat(result.inputTokens()).isEqualTo(1000);
        assertThat(result.cachedInputTokens()).isEqualTo(500);
        assertThat(result.outputTokens()).isEqualTo(100);
        assertThat(result.unsettledRequests()).isZero();
        assertThat(result.uses()).isEqualTo(1);
        assertThat(result.useLimit()).isEqualTo(3);
        assertThat(budget.usage(user()).estimatedCostUsd()).isZero();
    }

    @Test
    void parallelFreeRequestsCannotExceedThreeUses() throws Exception {
        UUID id = user();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 40; i++) tasks.add(() -> {
                try { budget.reserve(id, 0, 1, true); return true; }
                catch (AiBudgetExceededException ex) {
                    assertThat(ex.getHeaders().getFirst("Retry-After")).isEqualTo("43200");
                    return false;
                }
            });
            int successful = 0;
            for (Future<Boolean> result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                if (result.get()) successful++;
            }
            assertThat(successful).isEqualTo(3);
            assertThat(budget.usage(id).estimatedCostUsd()).isEqualByComparingTo("0.096006");
            assertThat(budget.usage(id).uses()).isEqualTo(3);
            assertThat(budget.usage(id).unsettledRequests()).isEqualTo(3);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void freeCostLimitCanBlockBeforeThirdUse() {
        UUID id = user();
        budget.reserve(id, 0, 8000, true);
        budget.reserve(id, 0, 8000, true);

        assertThatThrownBy(() -> budget.reserve(id, 0, 8000, true))
                .isInstanceOf(AiBudgetExceededException.class);
        assertThat(budget.usage(id).estimatedCostUsd()).isEqualByComparingTo("0.083200");
        assertThat(budget.usage(id).uses()).isEqualTo(2);
    }

    @Test
    void retriesAccumulateCostButNotUses() {
        UUID id = user();
        for (int attempt = 0; attempt < 5; attempt++) {
            var reservation = budget.reserve(id, 0, 1, attempt == 0);
            budget.settle(reservation, 0, 0, 0, 0);
        }

        assertThat(budget.usage(id).uses()).isEqualTo(1);
        assertThat(budget.usage(id).estimatedCostUsd()).isZero();
    }

    @Test
    void premiumHasNoUseLimitButCannotExceedFiftyCents() {
        UUID id = premiumUser();
        for (int request = 0; request < 12; request++) budget.reserve(id, 0, 8000, true);

        assertThatThrownBy(() -> budget.reserve(id, 0, 8000, true))
                .isInstanceOf(AiBudgetExceededException.class);
        var result = budget.usage(id);
        assertThat(result.limitUsd()).isEqualByComparingTo("0.50");
        assertThat(result.estimatedCostUsd()).isEqualByComparingTo("0.499200");
        assertThat(result.uses()).isEqualTo(12);
        assertThat(result.useLimit()).isNull();
    }

    @Test
    void expiredPremiumImmediatelyUsesFreeLimits() {
        UUID id = premiumUser();
        assertThat(budget.usage(id).limitUsd()).isEqualByComparingTo("0.50");

        when(clock.instant()).thenReturn(Instant.parse("2026-10-01T00:00:00Z"));

        assertThat(budget.usage(id).limitUsd()).isEqualByComparingTo("0.10");
        assertThat(budget.usage(id).useLimit()).isEqualTo(3);
    }

    @Test
    void midnightStartsNewDayAndLateSettlementStaysOnOriginalDay() {
        UUID id = user();
        var reservation = budget.reserve(id, 0, 4000, true);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-10T00:00:00Z"));
        assertThat(budget.usage(id).estimatedCostUsd()).isZero();
        assertThat(budget.usage(id).uses()).isZero();
        budget.settle(reservation, 1000, 0, 0, 1000);
        assertThat(budget.usage(id).estimatedCostUsd()).isZero();
        assertThat(usage.findByUserIdAndUsageDate(id, reservation.date()).orElseThrow().getChargedMicros()).isEqualTo(1400);
        assertThat(budget.reserve(id, 0, 4000, true).date()).isEqualTo(reservation.date().plusDays(1));
    }
}
