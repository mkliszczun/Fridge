package io.github.mkliszczun.fridge.account;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationMailerTest {
    @Test void usesSharedSmtpWithoutRequiringPasswordResetUrl() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        AccountProperties properties = new AccountProperties();
        EmailVerificationMailer mailer = new EmailVerificationMailer(provider, properties, "smtp.example.com");
        assertThatThrownBy(() -> mailer.send("user@example.com", "012345")).isInstanceOf(EmailDeliveryException.class);
        verifyNoInteractions(sender);
        properties.setMailFrom("fridge@example.com");
        mailer.send("user@example.com", "012345");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("fridge@example.com");
        assertThat(message.getValue().getTo()).containsExactly("user@example.com");
        assertThat(message.getValue().getText()).contains("012345", "10 minut");
        assertThat(message.getValue().getSubject()).doesNotContain("012345");
        assertThatThrownBy(() -> new EmailVerificationMailer(provider, properties, "").send("user@example.com", "012345"))
                .isInstanceOf(EmailDeliveryException.class);
    }
}
