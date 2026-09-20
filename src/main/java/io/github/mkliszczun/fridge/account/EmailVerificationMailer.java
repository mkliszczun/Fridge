package io.github.mkliszczun.fridge.account;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationMailer {
    private final ObjectProvider<JavaMailSender> senders;
    private final AccountProperties properties;
    private final String smtpHost;

    public EmailVerificationMailer(ObjectProvider<JavaMailSender> senders, AccountProperties properties,
                                   @Value("${spring.mail.host:}") String smtpHost) {
        this.senders = senders;
        this.properties = properties;
        this.smtpHost = smtpHost;
    }

    public void send(String email, String code) {
        if (properties.getMailFrom().isBlank() || smtpHost.isBlank() || senders.getIfAvailable() == null) {
            throw new EmailDeliveryException();
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.getMailFrom());
        mail.setTo(email);
        mail.setSubject("Fridge — potwierdź adres e-mail");
        mail.setText("Twój kod potwierdzający adres e-mail: " + code
                + "\nKod jest ważny przez 10 minut. Wpisz go w aplikacji Fridge."
                + "\n\nJeśli nie prosisz o ten kod, zignoruj tę wiadomość. Nie udostępniaj kodu innym osobom.");
        senders.getObject().send(mail);
    }
}
