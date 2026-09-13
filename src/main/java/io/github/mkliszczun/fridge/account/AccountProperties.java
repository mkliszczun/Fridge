package io.github.mkliszczun.fridge.account;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("account")
@Getter
@Setter
public class AccountProperties {
    private String resetUrl = "";
    private String mailFrom = "";
}
