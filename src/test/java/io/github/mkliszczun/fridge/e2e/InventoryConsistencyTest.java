package io.github.mkliszczun.fridge.e2e;

import io.github.mkliszczun.fridge.dto.*;
import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.repository.*;
import io.github.mkliszczun.fridge.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class InventoryConsistencyTest {
    @Autowired FridgeService fridges;
    @Autowired FridgeItemService items;
    @Autowired PlannedMealService meals;
    @Autowired RecipeService recipes;
    @Autowired PlannedMealCompletionService completion;
    @Autowired PlannedMealAutoReservationService autoReservations;
    @Autowired ShoppingListServiceImpl shopping;
    @Autowired UserRepository users;
    @Autowired FridgeItemRepository itemRepository;
    @Autowired PlannedMealReservationRepository reservations;
    @Autowired AiShoppingListService shoppingAi;
    @MockitoBean OpenAiShoppingListClient ai;

    @Test void concurrentUsesDoNotLoseSubtractions() throws Exception {
        Fixture f = fixture();
        List<Runnable> operations = new ArrayList<>();
        for (int i = 0; i < 10; i++) operations.add(() -> items.useItem(f.item(), f.user(), amount("100")));
        concurrently(operations);
        var result = itemRepository.findById(f.item()).orElseThrow();
        assertThat(result.getAmount()).isEqualByComparingTo("0");
        assertThat(result.getArchivedAt()).isNotNull();
    }

    @Test void completionAndManualUseCannotOverwriteEachOther() throws Exception {
        Fixture f = fixture();
        reserve(f, "600");
        concurrently(List.of(() -> items.useItem(f.item(), f.user(), amount("100")),
                () -> completion.complete(f.fridge(), f.meal(), f.user())));
        assertThat(itemRepository.findById(f.item()).orElseThrow().getAmount()).isEqualByComparingTo("300");
        assertThat(reservations.sumReservedAmount(f.item())).isZero();
    }

    @Test void reducingStockTrimsReservationsAndArchivingReleasesThem() {
        Fixture f = fixture();
        reserve(f, "600");
        items.updateAmount(f.item(), f.user(), amount("250"));
        assertThat(reservations.sumReservedAmount(f.item())).isEqualByComparingTo("250");
        items.discard(f.item(), f.user());
        assertThat(reservations.sumReservedAmount(f.item())).isZero();
        assertThatThrownBy(() -> items.updateAmount(f.item(), f.user(), amount("100")))
                .isInstanceOf(ConflictException.class);
    }

    @Test void portionsInvalidateReservationsAndOnlyTheirShoppingContributions() {
        Fixture f = fixture();
        reserve(f, "600");
        var oldProposal = new ShoppingListImportRequest(List.of(new ShoppingListImportRequest.Item(
                "Mleko", "ml", List.of(new ShoppingListImportRequest.Source(f.ingredient(), amount("200"))))));
        shopping.importProposal(f.fridge(), f.user(), oldProposal);
        shopping.addItem(f.fridge(), f.user(), "Mleko", amount("50"), "ml");
        PlannedMeal changed = meals.update(f.fridge(), f.meal(), f.user(),
                new PlannedMealUpdateRequest(null, LocalDate.now(), 1));
        assertThat(reservations.sumReservedAmount(f.item())).isZero();
        assertThat(changed.getIngredients().get(0).getId()).isNotEqualTo(f.ingredient());
        var list = shopping.list(f.fridge(), f.user());
        assertThat(list).singleElement().satisfies(item -> {
            assertThat(item.getManualAmount()).isEqualByComparingTo("50");
            assertThat(item.getSources()).isEmpty();
        });
        assertThatThrownBy(() -> shopping.importProposal(f.fridge(), f.user(), oldProposal))
                .isInstanceOf(NotFoundException.class);
    }

    @Test void changingOnlyDateKeepsReservations() {
        Fixture f = fixture();
        reserve(f, "600");
        meals.update(f.fridge(), f.meal(), f.user(),
                new PlannedMealUpdateRequest(null, LocalDate.now().plusDays(1), 2));
        assertThat(reservations.sumReservedAmount(f.item())).isEqualByComparingTo("600");
    }

    @Test void parallelShoppingAdditionsAreSummedIntoOneItem() throws Exception {
        Fixture f = fixture();
        List<Runnable> operations = new ArrayList<>();
        for (int i = 0; i < 8; i++) operations.add(() -> shopping.addItem(f.fridge(), f.user(), "Ryż", amount("100"), "g"));
        concurrently(operations);
        assertThat(shopping.list(f.fridge(), f.user())).singleElement().satisfies(item ->
                assertThat(item.getManualAmount()).isEqualByComparingTo("800"));
    }

    @Test void concurrentAiReservationsRecheckNeedsAfterWaitingForTheLock() throws Exception {
        Fixture f = fixture();
        CyclicBarrier bothGenerated = new CyclicBarrier(2);
        when(ai.match(anyList(), anyList())).thenAnswer(call -> {
            bothGenerated.await(10, TimeUnit.SECONDS);
            return matches(f);
        });
        Runnable reserve = () -> autoReservations.reserve(f.fridge(), f.user(), new PlannedMealsReserveRequest(List.of(f.meal())));
        concurrently(List.of(reserve, reserve));
        assertThat(reservations.sumReservedAmount(f.item())).isEqualByComparingTo("600");
        assertThat(meals.get(f.fridge(), f.meal(), f.user()).getIngredients().get(0).getReservations()).hasSize(1);
    }

    @Test void completingWhileAiWorksDoesNotBlockOrResurrectReservations() throws Exception {
        Fixture f = fixture();
        CountDownLatch aiStarted = new CountDownLatch(1);
        CountDownLatch answer = new CountDownLatch(1);
        when(ai.match(anyList(), anyList())).thenAnswer(call -> {
            aiStarted.countDown();
            assertThat(answer.await(10, TimeUnit.SECONDS)).isTrue();
            return matches(f);
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> result = pool.submit(() -> autoReservations.reserve(f.fridge(), f.user(),
                    new PlannedMealsReserveRequest(List.of(f.meal()))));
            assertThat(aiStarted.await(5, TimeUnit.SECONDS)).isTrue();
            completion.complete(f.fridge(), f.meal(), f.user());
            answer.countDown();
            assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ConflictException.class);
            assertThat(reservations.sumReservedAmount(f.item())).isZero();
        } finally { answer.countDown(); pool.shutdownNow(); }
    }

    @Test void expiredReservedFoodDoesNotCoverShoppingNeedsOrGoToAi() {
        Fixture f = fixture();
        reserve(f, "600");
        items.updateBestBeforeDate(f.item(), f.user(), LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1));
        var list = shoppingAi.generate(f.fridge(), f.user(), new AiShoppingListGenerateRequest(List.of(f.meal())));
        assertThat(list.items()).singleElement().satisfies(item -> assertThat(item.amount()).isEqualByComparingTo("600"));
        autoReservations.reserve(f.fridge(), f.user(), new PlannedMealsReserveRequest(List.of(f.meal())));
        verifyNoInteractions(ai);
    }

    private void reserve(Fixture f, String amount) {
        meals.createReservation(f.fridge(), f.meal(), f.user(),
                new PlannedMealReservationRequest(f.ingredient(), f.item(), amount(amount)));
    }

    private List<ShoppingListIngredientMatch> matches(Fixture f) {
        return List.of(new ShoppingListIngredientMatch(f.ingredient(), List.of(f.item())));
    }

    private Fixture fixture() {
        UserEntity user = new UserEntity();
        user.setUsername(UUID.randomUUID() + "@test.local");
        user.setEmail(user.getUsername());
        user.setPassword("test-only-hash");
        UUID userId = users.save(user).getId();
        UUID fridge = fridges.createFridge("Test", userId).getId();
        UUID recipe = recipes.create(userId, new RecipeRequest("Owsianka", null, "Wymieszaj", 2,
                List.of(new RecipeIngredientRequest("Mleko", amount("600"), "ml", false, null)))).getId();
        PlannedMeal meal = meals.create(fridge, userId, new PlannedMealCreateRequest(recipe, LocalDate.now(), 2));
        UUID item = items.createItem(fridge, userId, null, "Mleko", amount("1000"), Unit.MILLILITER,
                LocalDate.now().plusDays(5), null).getId();
        return new Fixture(userId, fridge, meal.getId(), meal.getIngredients().get(0).getId(), item);
    }

    private void concurrently(List<Runnable> operations) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (Runnable operation : operations) results.add(pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                operation.run();
            }));
            start.countDown();
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    private BigDecimal amount(String value) { return new BigDecimal(value); }
    private record Fixture(UUID user, UUID fridge, UUID meal, UUID ingredient, UUID item) {}
}
