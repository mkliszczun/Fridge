package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.account.AccountService;
import io.github.mkliszczun.fridge.dto.RegisterRequest;
import io.github.mkliszczun.fridge.entity.LoginRequest;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    public record RefreshRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {}
    public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                               @NotBlank @Size(min = 8, max = 72) String password) {}

    private final AuthenticationManager authenticationManager;
    private final UserDetailsManager users;
    private final PasswordEncoder passwords;
    private final AccountService accounts;

    public AuthController(AuthenticationManager authenticationManager, UserDetailsManager users,
                          PasswordEncoder passwords, AccountService accounts) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.passwords = passwords;
        this.accounts = accounts;
    }

    @PostMapping("/login")
    public AccountService.Tokens login(@Valid @RequestBody LoginRequest request) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLogin(), request.getPassword()));
        return accounts.issue((AppUserDetails) auth.getPrincipal());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        AccountService.validatePassword(request.password());
        if (users.userExists(request.login())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "User already exists"));
        }
        users.createUser(User.withUsername(request.login()).password(passwords.encode(request.password()))
                .roles("USER").build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accounts.issue((AppUserDetails) users.loadUserByUsername(request.login())));
    }

    @PostMapping("/refresh")
    public AccountService.Tokens refresh(@Valid @RequestBody RefreshRequest request) {
        return accounts.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AppUserDetails user) { accounts.logoutAll(user.getId()); }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest request) {
        accounts.forgotPassword(request.email());
        return Map.of("message", "If this account exists, a password reset link will be sent.");
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request) { accounts.resetPassword(request.token(), request.password()); }
}
