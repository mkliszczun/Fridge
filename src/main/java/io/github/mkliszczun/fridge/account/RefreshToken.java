package io.github.mkliszczun.fridge.account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
@Getter
@Setter
public class RefreshToken {
    @Id
    @Column(length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private long tokenVersion;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean used;
}
