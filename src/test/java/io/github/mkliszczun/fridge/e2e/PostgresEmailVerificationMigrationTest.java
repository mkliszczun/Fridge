package io.github.mkliszczun.fridge.e2e;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresEmailVerificationMigrationTest {
    @Test void upgradeFromV14PreservesAccountsAndDataButRevokesSessions() {
        String url = System.getenv("POSTGRES_TEST_URL");
        String schema = "email_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "fridge_test", "fridge_test"));
        try {
            Flyway.configure().dataSource(url, "fridge_test", "fridge_test").schemas(schema).target("14").load().migrate();
            UUID user = UUID.randomUUID();
            UUID fridge = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".users (id, username, email, password, enabled, account_non_expired, account_non_locked, credentials_non_expired, token_version, premium_until) VALUES (?, 'old-login', 'invalid-legacy-email', 'old-hash', true, true, true, true, 7, TIMESTAMPTZ '2027-01-01T00:00:00Z')", user);
            jdbc.update("INSERT INTO " + schema + ".user_roles (user_id, role) VALUES (?, 'ADMIN')", user);
            jdbc.update("INSERT INTO " + schema + ".fridge (id, name) VALUES (?, 'Preserved fridge')", fridge);
            jdbc.update("INSERT INTO " + schema + ".refresh_token (token_hash, user_id, token_version, expires_at, used) VALUES ('old-hash', ?, 7, TIMESTAMPTZ '2027-01-01T00:00:00Z', false)", user);
            Flyway.configure().dataSource(url, "fridge_test", "fridge_test").schemas(schema).load().migrate();
            var account = jdbc.queryForMap("SELECT * FROM " + schema + ".users WHERE id = ?", user);
            assertThat(account.get("username")).isEqualTo("old-login");
            assertThat(account.get("email")).isEqualTo("invalid-legacy-email");
            assertThat(account.get("password")).isEqualTo("old-hash");
            assertThat(account.get("email_verified_at")).isNull();
            assertThat(account.get("premium_until")).isNotNull();
            assertThat(((Number) account.get("token_version")).longValue()).isEqualTo(8L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".user_roles WHERE user_id = ? AND role = 'ADMIN'", Integer.class, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT name FROM " + schema + ".fridge WHERE id = ?", String.class, fridge)).isEqualTo("Preserved fridge");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".refresh_token", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".email_verification", Integer.class)).isZero();
        } finally {
            // Only this test's randomly named schema, never public or any pre-existing schema.
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
