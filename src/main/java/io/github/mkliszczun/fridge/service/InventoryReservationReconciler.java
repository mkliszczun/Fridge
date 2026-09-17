package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class InventoryReservationReconciler {
    private final PlannedMealReservationRepository reservations;

    public InventoryReservationReconciler(PlannedMealReservationRepository reservations) {
        this.reservations = reservations;
    }

    // Caller holds the fridge write lock. Earlier meals retain priority if stock was reduced manually.
    @Transactional(propagation = Propagation.MANDATORY)
    public void reconcile(FridgeItem item) {
        BigDecimal remaining = item.getArchivedAt() == null ? item.getAmount() : BigDecimal.ZERO;
        for (var reservation : reservations.findActiveForItem(item.getId())) {
            BigDecimal kept = remaining.min(reservation.getAmount());
            if (kept.signum() == 0) {
                reservation.getPlannedMealIngredient().removeReservation(reservation);
                reservations.delete(reservation);
            } else {
                reservation.setAmount(kept);
            }
            remaining = remaining.subtract(kept);
        }
    }
}
