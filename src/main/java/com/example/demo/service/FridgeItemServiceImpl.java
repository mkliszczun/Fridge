package com.example.demo.service;

import com.example.demo.enums.ItemState;
import com.example.demo.enums.Unit;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.fridge.Fridge;
import com.example.demo.fridge.FridgeItem;
import com.example.demo.fridge.Product;
import com.example.demo.repository.FridgeItemRepository;
import com.example.demo.repository.FridgeMemberRepository;
import com.example.demo.repository.FridgeRepository;
import com.example.demo.repository.ProductRepository;
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

        item.setOpenDate(openDate != null ? openDate : LocalDate.now());
        item.setState(ItemState.OPEN);
        var effective = expirePolicy.computeEffectiveExpireAt(
                item.getBestBeforeDate(), item.getOpenDate(), item.getProduct(), null, null);
        item.setEffectiveExpireAt(effective);

        return itemRepository.save(item);    }

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
}
