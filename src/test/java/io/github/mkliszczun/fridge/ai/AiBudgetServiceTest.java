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

    @Test
    void settlesActualCachedInputAndOutputAndKeepsCostAfterOuterRollback() {
        UUID id = user();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var reserved = budget.reserve(id, 0, 4000);
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
        assertThat(budget.usage(user()).estimatedCostUsd()).isZero();
    }

    @Test
    void parallelRequestsCannotOverspendAndFailuresKeepTheirReservations() throws Exception {
        UUID id = user();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 40; i++) tasks.add(() -> {
                try { budget.reserve(id, 0, 8000); return true; }
                catch (AiBudgetExceededException ex) {
                    assertThat(ex.getHeaders().getFirst("Retry-After")).isEqualTo("43200");
                    return false;
                }
            });
            int successful = 0;
            for (Future<Boolean> result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                if (result.get()) successful++;
            }
            assertThat(successful).isEqualTo(24); // 24 * $0.0416; the 25th reservation exceeds $1.
            assertThat(budget.usage(id).estimatedCostUsd()).isEqualByComparingTo("0.998400");
            assertThat(budget.usage(id).unsettledRequests()).isEqualTo(24);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void midnightStartsNewDayAndLateSettlementStaysOnOriginalDay() {
        UUID id = user();
        var reservation = budget.reserve(id, 0, 4000);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-10T00:00:00Z"));
        assertThat(budget.usage(id).estimatedCostUsd()).isZero();
        budget.settle(reservation, 1000, 0, 0, 1000);
        assertThat(budget.usage(id).estimatedCostUsd()).isZero();
        assertThat(usage.findByUserIdAndUsageDate(id, reservation.date()).orElseThrow().getChargedMicros()).isEqualTo(1400);
        assertThat(budget.reserve(id, 0, 4000).date()).isEqualTo(reservation.date().plusDays(1));
    }
}
