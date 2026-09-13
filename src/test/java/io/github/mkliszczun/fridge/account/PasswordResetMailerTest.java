package io.github.mkliszczun.fridge.account;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetMailerTest {
    @Test void requiresHttpsAndSendsOneTimeSecretOnlyInFragment() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        AccountProperties properties = new AccountProperties();
        PasswordResetMailer mailer = new PasswordResetMailer(provider, properties, "smtp.example.com");
        assertThatThrownBy(mailer::requireConfigured).isInstanceOf(ResponseStatusException.class);
        properties.setResetUrl("http://example.com/reset-password.html");
        properties.setMailFrom("fridge@example.com");
        assertThatThrownBy(mailer::requireConfigured).isInstanceOf(ResponseStatusException.class);
        properties.setResetUrl("https://example.com/reset-password.html");
        String token = SecretTokens.generate();
        mailer.send("user@example.com", token);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getText()).contains("https://example.com/reset-password.html#token=" + token);
        assertThat(message.getValue().getTo()).containsExactly("user@example.com");
        assertThat(SecretTokens.hash(token)).hasSize(64).isNotEqualTo(token);
    }
}
