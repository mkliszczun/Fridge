package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.ForbiddenException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.fridge.Product;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.FridgeMemberRepository;
import io.github.mkliszczun.fridge.repository.FridgeRepository;
import io.github.mkliszczun.fridge.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FridgeItemServiceImpl implements FridgeItemService{

    private final FridgeItemRepository itemRepository;
    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final EffectiveExpirePolicy expirePolicy;

    public FridgeItemServiceImpl(FridgeItemRepository itemRepository,
                             FridgeRepository fridgeRepository,
                             FridgeMemberRepository memberRepository,
                             ProductRepository productRepository,
                             EffectiveExpirePolicy expirePolicy) {
        this.itemRepository = itemRepository;
        this.fridgeRepository = fridgeRepository;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.expirePolicy = expirePolicy;
    }

    @Override
    public void assertMembership(UUID fridgeId, UUID userId) {
        if (!memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new ForbiddenException("Not a member of this fridge");
        }
    }

    @Override
    @Transactional
    public FridgeItem createItem(UUID fridgeId, UUID currentUserId, UUID productId, String customName, BigDecimal amount, Unit unit, LocalDate bestBeforeDate, LocalDate openDate) {

        assertMembership(fridgeId, currentUserId);
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new NotFoundException("Fridge not found"));

        Product product = null;
        if (productId != null) {
            product = productRepository.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found"));
            if (unit == null) {
                unit = product.getDefaultUnit();
            }
        }

        FridgeItem item = new FridgeItem();
        item.setFridge(fridge);
        item.setProduct(product);
        item.setCustomName(product == null ? customName : null);
        item.setAmount(amount);
        item.setUnit(unit);
        item.setBestBeforeDate(bestBeforeDate);
        item.setOpenDate(openDate);
        item.setState(openDate != null ? ItemState.OPEN : ItemState.SEALED);
        item.setOwnerUserId(currentUserId);

        // wylicz data ważności efektywna
        var effective = expirePolicy.computeEffectiveExpireAt(
                bestBeforeDate, openDate, product, null, null);
        item.setEffectiveExpireAt(effective);

        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public FridgeItem openItem(UUID itemId, UUID currentUserId, LocalDate openDate) {
        FridgeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        assertMembership(item.getFridge().getId(), currentUserId);

        assertActive(item, "opened");
        if (item.getState() == ItemState.OPEN) {
            return item;
        }

        openSealedItem(item, openDate != null ? openDate : LocalDate.now());

        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public FridgeItem useItem(UUID itemId, UUID currentUserId, BigDecimal amountUsed) {
        FridgeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        assertMembership(item.getFridge().getId(), currentUserId);
        assertActive(item, "used");

        if (amountUsed == null || amountUsed.signum() <= 0) {
            throw new ConflictException("Amount used must be greater than zero");
        }
        if (amountUsed.compareTo(item.getAmount()) > 0) {
            throw new ConflictException("Amount used exceeds available amount");
        }

        if (item.getState() == ItemState.SEALED) {
            openSealedItem(item, LocalDate.now());
        }

        BigDecimal remainingAmount = item.getAmount().subtract(amountUsed);
        item.setAmount(remainingAmount);
        if (remainingAmount.signum() == 0) {
            item.setState(ItemState.CONSUMED);
            item.setArchivedAt(OffsetDateTime.now());
        }

        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public FridgeItem updateAmount(UUID itemId, UUID currentUserId, BigDecimal newAmount) {
        FridgeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        assertMembership(item.getFridge().getId(), currentUserId);
        item.setAmount(newAmount);
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public void consume(UUID itemId, UUID currentUserId) {
        archiveWithState(itemId, currentUserId, ItemState.CONSUMED);
    }

    @Override
    @Transactional
    public void discard(UUID itemId, UUID currentUserId) {
        archiveWithState(itemId, currentUserId, ItemState.DISCARDED);
    }

    @Override
    public void archiveWithState(UUID itemId, UUID currentUserId, ItemState state) {
        FridgeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        assertMembership(item.getFridge().getId(), currentUserId);

        item.setState(state);
        item.setArchivedAt(OffsetDateTime.now());
        itemRepository.save(item);
    }

    @Override
    @Transactional
    public List<FridgeItem> list(UUID fridgeId, UUID currentUserId, Boolean expiringSoon) {
        assertMembership(fridgeId, currentUserId);

        if (Boolean.TRUE.equals(expiringSoon)) {
            LocalDate today = LocalDate.now();
            LocalDate to = today.plusDays(2);
            return itemRepository.findExpiringBetween(fridgeId, today, to);
        }
        return itemRepository.findActiveByFridge(fridgeId);
    }

    private void openSealedItem(FridgeItem item, LocalDate openDate) {
        LocalDate previousEffectiveExpireAt = item.getEffectiveExpireAt();
        item.setOpenDate(openDate);
        item.setState(ItemState.OPEN);

        LocalDate recalculatedExpireAt = expirePolicy.computeEffectiveExpireAt(
                item.getBestBeforeDate(), openDate, item.getProduct(), null, null);
        if (previousEffectiveExpireAt != null
                && (recalculatedExpireAt == null
                || previousEffectiveExpireAt.isBefore(recalculatedExpireAt))) {
            recalculatedExpireAt = previousEffectiveExpireAt;
        }
        item.setEffectiveExpireAt(recalculatedExpireAt);
    }

    private void assertActive(FridgeItem item, String action) {
        if (item.getState() == ItemState.CONSUMED || item.getState() == ItemState.DISCARDED) {
            throw new ConflictException("Archived item cannot be " + action);
        }
    }
}
