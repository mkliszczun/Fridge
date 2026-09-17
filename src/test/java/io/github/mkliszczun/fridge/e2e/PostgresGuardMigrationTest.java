package io.github.mkliszczun.fridge.e2e;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresGuardMigrationTest {
    @Test void upgradeFromV12PreservesExistingCostsAndManualShoppingEntries() {
        String url = System.getenv("POSTGRES_TEST_URL");
        String schema = "guard_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "fridge_test", "fridge_test"));
        try {
            Flyway.configure().dataSource(url, "fridge_test", "fridge_test").schemas(schema).target("12").load().migrate();
            UUID user = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".users (id, username, email, password, enabled, account_non_expired, account_non_locked, credentials_non_expired) VALUES (?, 'migration', 'migration@test.local', 'test-only', true, true, true, true)", user);
            jdbc.update("INSERT INTO " + schema + ".ai_daily_usage (id, user_id, usage_date, charged_micros) VALUES (?, ?, DATE '2026-09-16', 123)", UUID.randomUUID(), user);
            UUID fridge = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".fridge (id, name) VALUES (?, 'Migration test')", fridge);
            UUID manual = UUID.randomUUID();
            UUID generated = UUID.randomUUID();
            UUID unquantified = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".shopping_list_item (id, fridge_id, name, manual_amount, is_quantified) VALUES (?, ?, 'Manual', 25, true)", manual, fridge);
            jdbc.update("INSERT INTO " + schema + ".shopping_list_item (id, fridge_id, name, is_quantified) VALUES (?, ?, 'Generated', true)", generated, fridge);
            jdbc.update("INSERT INTO " + schema + ".shopping_list_item (id, fridge_id, name, is_quantified) VALUES (?, ?, 'Uncertain manual', false)", unquantified, fridge);
            for (UUID item : new UUID[]{manual, generated, unquantified}) {
                jdbc.update("INSERT INTO " + schema + ".shopping_list_item_source (id, shopping_list_item_id, planned_meal_ingredient_id) VALUES (?, ?, ?)", UUID.randomUUID(), item, UUID.randomUUID());
            }
            Flyway.configure().dataSource(url, "fridge_test", "fridge_test").schemas(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT charged_micros FROM " + schema + ".ai_global_daily_usage", Long.class)).isEqualTo(123L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".shopping_list_item", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".shopping_list_item_source", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT has_manual_entry FROM " + schema + ".shopping_list_item WHERE id = ?", Boolean.class, manual)).isTrue();
            assertThat(jdbc.queryForObject("SELECT has_manual_entry FROM " + schema + ".shopping_list_item WHERE id = ?", Boolean.class, generated)).isFalse();
            assertThat(jdbc.queryForObject("SELECT has_manual_entry FROM " + schema + ".shopping_list_item WHERE id = ?", Boolean.class, unquantified)).isTrue();
        } finally {
            // Only the isolated schema created by this test; never public or an existing schema.
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
