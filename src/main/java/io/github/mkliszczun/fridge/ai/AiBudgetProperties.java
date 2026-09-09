package io.github.mkliszczun.fridge.ai;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties("ai.budget")
@Validated
@Getter
@Setter
public class AiBudgetProperties {
    @NotBlank private String pricedModel = "gpt-5.6-luna";
    @NotNull @DecimalMin("0.000001") private BigDecimal inputPrice = new BigDecimal("0.20");
    @NotNull @DecimalMin("0") private BigDecimal cachedInputPrice = new BigDecimal("0.02");
    @NotNull @DecimalMin("0.000001") private BigDecimal cacheWritePrice = new BigDecimal("0.25");
    @NotNull @DecimalMin("0.000001") private BigDecimal outputPrice = new BigDecimal("1.20");
    @NotNull @DecimalMin("0.01") @DecimalMax("100") private BigDecimal dailyUsd = new BigDecimal("1.00");
}
