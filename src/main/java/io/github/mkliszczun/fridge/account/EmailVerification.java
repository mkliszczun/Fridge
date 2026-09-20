package io.github.mkliszczun.fridge.account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification")
@Getter @Setter
public class EmailVerification {
    @Id @Column(length = 64) private String tokenHash;
    private UUID userId;
    @Column(nullable = false) private long tokenVersion;
    @Column(length = 64) private String pendingUsername;
    private String pendingPasswordHash;
    @Column(length = 254) private String email;
    @Column(length = 60) private String codeHash;
    private Instant codeExpiresAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
}
