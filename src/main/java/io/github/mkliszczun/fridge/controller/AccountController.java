package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.account.AccountService;
import io.github.mkliszczun.fridge.account.AccountDeletionService;
import io.github.mkliszczun.fridge.ai.AiBudgetService;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
public class AccountController {
    public record DeleteRequest(@NotBlank @Size(max = 72) String password) {}
    public record PremiumRequest(Instant premiumUntil) {}
    private final AccountService accounts;
    private final AccountDeletionService deletion;
    private final AiBudgetService budgets;

    public AccountController(AccountService accounts, AccountDeletionService deletion, AiBudgetService budgets) {
        this.accounts = accounts;
        this.deletion = deletion;
        this.budgets = budgets;
    }

    @GetMapping("/api/me")
    public AccountService.Profile me(@AuthenticationPrincipal AppUserDetails user) { return accounts.profile(user.getId()); }

    @GetMapping("/api/me/ai-usage")
    public AiBudgetService.Usage usage(@AuthenticationPrincipal AppUserDetails user) { return budgets.usage(user.getId()); }

    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody DeleteRequest request) {
        deletion.delete(user.getId(), request.password());
    }

    @PutMapping("/admin/users/{id}/premium")
    public AccountService.Profile premium(@PathVariable UUID id, @RequestBody PremiumRequest request) {
        return accounts.setPremium(id, request.premiumUntil());
    }
}
