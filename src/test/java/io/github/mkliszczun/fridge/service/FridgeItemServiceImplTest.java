package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ForbiddenException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.fridge.Product;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.FridgeMemberRepository;
import io.github.mkliszczun.fridge.repository.FridgeRepository;
import io.github.mkliszczun.fridge.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FridgeItemServiceImplTest {

    @Mock
    FridgeItemRepository itemRepository;
    @Mock
    FridgeRepository fridgeRepository;
    @Mock
    FridgeMemberRepository memberRepository;
    @Mock
    ProductRepository productRepository;
    @Mock EffectiveExpirePolicy expirePolicy;

    FridgeItemService service;

    UUID userId = UUID.randomUUID();
    UUID fridgeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    Fridge fridge;
    Product product;
    FridgeItem persistedItem;

    @BeforeEach
    void setUp() throws Exception {
        service = new FridgeItemServiceImpl(
                itemRepository, fridgeRepository, memberRepository, productRepository, expirePolicy);

        fridge = new Fridge();
        setId(fridge, "id", fridgeId);
        fridge.setName("Dom");

        product = new Product();
        setId(product, "id", productId);
        product.setDefaultUnit(Unit.GRAM);

        persistedItem = new FridgeItem();
        setId(persistedItem, "id", itemId);
        persistedItem.setFridge(fridge);
    }

    // ---------- createItem ----------

    @Test
    void createItem_withProduct_setsDefaultUnit_andEffectiveDate_andOwner() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(fridge));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        LocalDate bestBefore = LocalDate.now().plusDays(7);
        LocalDate openDate = null;
        LocalDate computed = LocalDate.now().plusDays(5);
        when(expirePolicy.computeEffectiveExpireAt(bestBefore, openDate, product, null, null))
                .thenReturn(computed);

        when(itemRepository.save(any(FridgeItem.class))).thenAnswer(inv -> {
            FridgeItem i = inv.getArgument(0);
            i.setEffectiveExpireAt(computed);
            return i;
        });

        FridgeItem created = service.createItem(
                fridgeId, userId, productId, null,
                new BigDecimal("2.5"), null, bestBefore, openDate);

        assertThat(created.getFridge()).isEqualTo(fridge);
        assertThat(created.getProduct()).isEqualTo(product);
        assertThat(created.getUnit()).isEqualTo(Unit.GRAM); // default z Product
        assertThat(created.getEffectiveExpireAt()).isEqualTo(computed);
        assertThat(created.getOwnerUserId()).isEqualTo(userId);
        assertThat(created.getState()).isEqualTo(ItemState.SEALED);
        verify(expirePolicy).computeEffectiveExpireAt(bestBefore, openDate, product, null, null);
    }

    @Test
    void createItem_customName_withoutProduct_usesProvidedUnit_andOpenSetsStateOpen() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(fridge));

        LocalDate openDate = LocalDate.now();
        LocalDate computed = openDate.plusDays(3);
        when(expirePolicy.computeEffectiveExpireAt(null, openDate, null, null, null))
                .thenReturn(computed);

        when(itemRepository.save(any(FridgeItem.class))).thenAnswer(inv -> inv.getArgument(0));

        FridgeItem created = service.createItem(
                fridgeId, userId, null, "Domowy rosół",
                new BigDecimal("1"), Unit.MILLILITER, null, openDate);

        assertThat(created.getProduct()).isNull();
        assertThat(created.getCustomName()).isEqualTo("Domowy rosół");
        assertThat(created.getUnit()).isEqualTo(Unit.MILLILITER);
        assertThat(created.getState()).isEqualTo(ItemState.OPEN);
        assertThat(created.getEffectiveExpireAt()).isEqualTo(computed);
    }

    @Test
    void createItem_notMember_forbidden() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.createItem(
                fridgeId, userId, null, "X", BigDecimal.ONE, Unit.PIECE, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createItem_productNotFound_throws() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(fridge));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createItem(
                fridgeId, userId, productId, null, BigDecimal.ONE, null, null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void createItem_fridgeNotFound_throws() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createItem(
                fridgeId, userId, null, "x", BigDecimal.ONE, Unit.PIECE, null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Fridge not found");
    }

    // ---------- openItem ----------

    @Test
    void openItem_setsOpenState_andRecomputesEffectiveDate() {
        persistedItem.setState(ItemState.SEALED);
        persistedItem.setBestBeforeDate(LocalDate.now().plusDays(10));
        persistedItem.setProduct(product);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(persistedItem));
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);

        LocalDate openDate = LocalDate.now();
        LocalDate computed = openDate.plusDays(4);
        when(expirePolicy.computeEffectiveExpireAt(
                persistedItem.getBestBeforeDate(), openDate, product, null, null))
                .thenReturn(computed);

        when(itemRepository.save(any(FridgeItem.class))).thenAnswer(inv -> inv.getArgument(0));

        FridgeItem updated = service.openItem(itemId, userId, openDate);

        assertThat(updated.getState()).isEqualTo(ItemState.OPEN);
        assertThat(updated.getOpenDate()).isEqualTo(openDate);
        assertThat(updated.getEffectiveExpireAt()).isEqualTo(computed);
    }

    @Test
    void openItem_notFound_throws() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.openItem(itemId, userId, LocalDate.now()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Item not found");
    }

    @Test
    void openItem_notMember_forbidden() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(persistedItem));
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.openItem(itemId, userId, LocalDate.now()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- updateAmount ----------

    @Test
    void updateAmount_changesAmount_andPersists() {
        persistedItem.setAmount(new BigDecimal("1.0"));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(persistedItem));
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);
        when(itemRepository.save(any(FridgeItem.class))).thenAnswer(inv -> inv.getArgument(0));

        FridgeItem res = service.updateAmount(itemId, userId, new BigDecimal("0.5"));

        assertThat(res.getAmount()).isEqualTo("0.5");
    }

    // ---------- consume / discard ----------

    @Test
    void consume_setsConsumed_andArchives() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(persistedItem));
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);

        service.consume(itemId, userId);

        assertThat(persistedItem.getState()).isEqualTo(ItemState.CONSUMED);
        assertThat(persistedItem.getArchivedAt()).isNotNull();
        verify(itemRepository).save(persistedItem);
    }

    @Test
    void discard_setsDiscarded_andArchives() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(persistedItem));
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);

        service.discard(itemId, userId);

        assertThat(persistedItem.getState()).isEqualTo(ItemState.DISCARDED);
        assertThat(persistedItem.getArchivedAt()).isNotNull();
        verify(itemRepository).save(persistedItem);
    }

    // ---------- list (expiringSoon / all) ----------

    @Test
    void list_expiringSoon_true_callsRepoBetweenDates() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);
        when(itemRepository.findExpiringBetween(any(), any(), any()))
                .thenReturn(List.of(persistedItem));

        List<FridgeItem> res = service.list(fridgeId, userId, true);

        assertThat(res).hasSize(1);
        verify(itemRepository).findExpiringBetween(eq(fridgeId), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void list_allActive_whenExpiringSoonFalse() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);
        when(itemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of(persistedItem));

        List<FridgeItem> res = service.list(fridgeId, userId, false);

        assertThat(res).hasSize(1);
        verify(itemRepository).findActiveByFridge(fridgeId);
    }

    @Test
    void list_notMember_forbidden() {
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(false);
        assertThatThrownBy(() -> service.list(fridgeId, userId, null))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- helpers ----------

    private static void setId(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}