package io.github.mkliszczun.fridge.logging;

import jakarta.mail.MessagingException;
import org.springframework.mail.MailSendException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.client.RestClientResponseException;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Diagnostic locations and machine-readable codes, never exception messages or payloads. */
public final class SafeDiagnostics {
    private SafeDiagnostics() {}

    public static String describe(Throwable failure) {
        if (failure == null) return "none";
        StringBuilder result = new StringBuilder();
        var pending = new ArrayDeque<Throwable>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty() && seen.size() < 8) {
            Throwable error = pending.removeFirst();
            if (!seen.add(error)) continue;
            if (!result.isEmpty()) result.append(" | ");
            result.append(error.getClass().getName());
            if (error instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().matches("[A-Z0-9]{5}")) {
                result.append(" sqlState=").append(sql.getSQLState());
            }
            if (error instanceof WebClientResponseException http) {
                result.append(" upstreamStatus=").append(http.getStatusCode().value());
            }
            if (error instanceof RestClientResponseException http) {
                result.append(" upstreamStatus=").append(http.getStatusCode().value());
            }
            StackTraceElement[] stack = error.getStackTrace();
            for (int i = 0; i < Math.min(stack.length, 24); i++) {
                result.append(" <- ").append(stack[i]);
            }
            if (stack.length > 24) result.append(" <- ...");
            if (error.getCause() != null) pending.add(error.getCause());
            if (error instanceof SQLException sql && sql.getNextException() != null) pending.add(sql.getNextException());
            if (error instanceof MessagingException mail && mail.getNextException() != null) pending.add(mail.getNextException());
            if (error instanceof MailSendException mail) Collections.addAll(pending, mail.getMessageExceptions());
        }
        if (!pending.isEmpty()) result.append(" | ...");
        return result.toString();
    }
}
