package io.github.mkliszczun.fridge.security.abuse;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "auth_rate_bucket", indexes = @Index(name = "idx_auth_bucket_expiry", columnList = "expires_at"))
@Getter @Setter
public class AuthRateBucket {
    @Id private String id;
    @Column(nullable = false) private long attempts;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
}
