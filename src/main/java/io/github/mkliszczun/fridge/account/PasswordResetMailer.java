package io.github.mkliszczun.fridge.account;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;

@Service
public class PasswordResetMailer {
    private final ObjectProvider<JavaMailSender> senders;
    private final AccountProperties properties;

    public PasswordResetMailer(ObjectProvider<JavaMailSender> senders, AccountProperties properties) {
        this.senders = senders;
        this.properties = properties;
    }

    public void requireConfigured() {
        try {
            URI uri = URI.create(properties.getResetUrl());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getFragment() != null || uri.getUserInfo() != null
                    || properties.getMailFrom().isBlank() || senders.getIfAvailable() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery unavailable");
        }
    }

    public void send(String email, String token) {
        requireConfigured();
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.getMailFrom());
        mail.setTo(email);
        mail.setSubject("Fridge — zmiana hasła");
        mail.setText("Aby ustawić nowe hasło, otwórz link w ciągu 30 minut:\n"
                + properties.getResetUrl() + "#token=" + token
                + "\n\nJeśli nie prosisz o zmianę hasła, zignoruj tę wiadomość.");
        senders.getObject().send(mail);
    }
}
