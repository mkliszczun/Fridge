package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.account.AccountService;
import io.github.mkliszczun.fridge.account.EmailVerificationService;
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
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    public record RefreshRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {}
    public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                               @NotBlank @Size(min = 8, max = 72) String password) {}
    public record EmailSendRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String verificationToken,
                                   @Email @Size(max = 254) String email) {}
    public record EmailVerifyRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String verificationToken,
                                     @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}

    private final AuthenticationManager authenticationManager;
    private final EmailVerificationService verification;
    private final AccountService accounts;

    public AuthController(AuthenticationManager authenticationManager, EmailVerificationService verification,
                          AccountService accounts) {
        this.authenticationManager = authenticationManager;
        this.verification = verification;
        this.accounts = accounts;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLogin(), request.getPassword()));
        AppUserDetails user = (AppUserDetails) auth.getPrincipal();
        return user.isEmailVerified() ? ResponseEntity.ok(accounts.issue(user))
                : ResponseEntity.accepted().body(verification.startExisting(user));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().body(verification.register(request));
    }

    @PostMapping("/email/send")
    public EmailVerificationService.Delivery sendEmail(@Valid @RequestBody EmailSendRequest request) {
        return verification.send(request.verificationToken(), request.email());
    }

    @PostMapping("/email/verify")
    public AccountService.Tokens verifyEmail(@Valid @RequestBody EmailVerifyRequest request) {
        return verification.verify(request.verificationToken(), request.code());
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
