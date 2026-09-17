package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class AiInventoryPolicy {
    private final Clock clock;

    public AiInventoryPolicy(Clock clock) { this.clock = clock; }

    public boolean usable(FridgeItem item) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return item.getArchivedAt() == null
                && item.getState() != ItemState.CONSUMED && item.getState() != ItemState.DISCARDED
                && (item.getEffectiveExpireAt() == null || !item.getEffectiveExpireAt().isBefore(today));
    }
}
