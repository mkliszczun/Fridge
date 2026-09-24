package io.github.mkliszczun.fridge.e2e;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${POSTGRES_TEST_URL}",
        "spring.datasource.username=fridge_test",
        "spring.datasource.password=fridge_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class PostgresFridgeInvitationTest extends FridgeInvitationFlowE2ETest {
    @Test void upgradeFromV16PreservesExistingAccountFridgeAndMembership() {
        String url = System.getenv("POSTGRES_TEST_URL");
        String schema = "invitation_migration_" + UUID.randomUUID().toString().replace("-", "");
        var sql = new JdbcTemplate(new DriverManagerDataSource(url, "fridge_test", "fridge_test"));
        try {
            Flyway.configure().dataSource(url, "fridge_test", "fridge_test").schemas(schema).target("16").load().migrate();
            UUID user = UUID.randomUUID();
            UUID fridge = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            sql.update("INSERT INTO " + schema + ".users (id, username, email, password, enabled, account_non_expired, account_non_locked, credentials_non_expired, email_verified_at, token_version) VALUES (?, 'existing@test.local', 'existing@test.local', 'existing-hash', true, true, true, true, now(), 5)", user);
            sql.update("INSERT INTO " + schema + ".fridge (id, name) VALUES (?, 'Existing fridge')", fridge);
            sql.update("INSERT INTO " + schema + ".fridge_member (id, fridge_id, user_id, role_in_fridge, is_default) VALUES (?, ?, ?, 'OWNER', true)", member, fridge, user);
            var account = sql.queryForMap("SELECT * FROM " + schema + ".users WHERE id = ?", user);
            var membership = sql.queryForMap("SELECT * FROM " + schema + ".fridge_member WHERE id = ?", member);
            Flyway.configure().dataSource(url, "fridge_test", "fridge_test").schemas(schema).load().migrate();
            assertThat(sql.queryForMap("SELECT * FROM " + schema + ".users WHERE id = ?", user)).isEqualTo(account);
            assertThat(sql.queryForMap("SELECT * FROM " + schema + ".fridge_member WHERE id = ?", member)).isEqualTo(membership);
            assertThat(sql.queryForObject("SELECT name FROM " + schema + ".fridge WHERE id = ?", String.class, fridge)).isEqualTo("Existing fridge");
            assertThat(sql.queryForObject("SELECT count(*) FROM " + schema + ".fridge_invitation", Integer.class)).isZero();
        } finally {
            // Only the schema created by this test, never public or a pre-existing schema.
            sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
