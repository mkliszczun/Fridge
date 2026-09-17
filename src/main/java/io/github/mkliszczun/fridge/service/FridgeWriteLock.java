package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.FridgeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Serializes short inventory, reservation and shopping-list writes for one fridge across instances. */
@Service
public class FridgeWriteLock {
    private final FridgeRepository fridges;
    private final FridgeItemRepository items;
    private final EntityManager entityManager;

    public FridgeWriteLock(FridgeRepository fridges, FridgeItemRepository items, EntityManager entityManager) {
        this.fridges = fridges;
        this.items = items;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockFridge(UUID fridgeId) {
        fridges.findByIdForUpdate(fridgeId).orElseThrow(() -> new NotFoundException("Fridge not found"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockItem(UUID itemId) {
        // Scalar lookup does not cache a potentially stale inventory entity before acquiring the lock.
        lockFridge(items.findFridgeId(itemId).orElseThrow(() -> new NotFoundException("Item not found")));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshAfterAi(Object entity) {
        // Used only after acquiring the fridge lock, before making any local changes.
        try {
            entityManager.refresh(entity);
        } catch (EntityNotFoundException deletedWhileGenerating) {
            throw new ConflictException("Meal or inventory changed while generating reservations; retry");
        }
    }
}
