package io.github.mkliszczun.fridge.entity;

import io.github.mkliszczun.fridge.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "default_expiration_days")
public class DefaultExpirationDays {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    @Enumerated(EnumType.STRING)
    private ProductType productType;

    private Integer defaultExpirationDays;

    private Integer expirationDaysAfterOpening;

}
