package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

class AiInventoryPolicyTest {
    private final AiInventoryPolicy policy = new AiInventoryPolicy(
            Clock.fixed(Instant.parse("2026-09-16T23:30:00Z"), ZoneOffset.UTC));

    @Test void excludesExpiredAndArchivedButAllowsTodayAndUnknownDate() {
        FridgeItem item = new FridgeItem();
        item.setState(ItemState.SEALED);
        assertThat(policy.usable(item)).isTrue();
        item.setEffectiveExpireAt(LocalDate.of(2026, 9, 16));
        assertThat(policy.usable(item)).isTrue();
        item.setEffectiveExpireAt(LocalDate.of(2026, 9, 15));
        assertThat(policy.usable(item)).isFalse();
        item.setEffectiveExpireAt(LocalDate.of(2026, 9, 20));
        item.setArchivedAt(OffsetDateTime.parse("2026-09-15T12:00:00Z"));
        assertThat(policy.usable(item)).isFalse();
        item.setArchivedAt(null);
        item.setState(ItemState.CONSUMED);
        assertThat(policy.usable(item)).isFalse();
    }
}
