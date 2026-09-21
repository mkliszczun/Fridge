package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.logging.SafeDiagnostics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

@RestController
@lombok.extern.slf4j.Slf4j
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(Map.of("status", "UP"));
            log.error("event=health_database_unavailable reason=invalid_connection");
        } catch (SQLException | RuntimeException ex) {
            log.error("event=health_database_unavailable diagnostics={}", SafeDiagnostics.describe(ex));
        }
        return ResponseEntity.status(503).header("Cache-Control", "no-store").body(Map.of("status", "DOWN"));
    }
}
