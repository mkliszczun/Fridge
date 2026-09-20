package io.github.mkliszczun.fridge.security.abuse;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties("abuse")
@Validated @Getter @Setter
public class AbuseProperties {
    private boolean enabled = true;
    // Enable only behind Fly's HTTP handler, with no untrusted direct access to the app port.
    private boolean trustFlyProxy = false;
    @Min(1) private int loginPer15Minutes = 20;
    @Min(1) private int registrationsPerHour = 3;
    @Min(1) private int globalRegistrationsPerDay = 100;
    @Min(1) private int passwordRequestsPer15Minutes = 5;
    @Min(1) private int refreshPer15Minutes = 60;
    @Min(1) private int emailSendsPer15Minutes = 10;
    @Min(1) private int emailVerificationsPer15Minutes = 20;
}
