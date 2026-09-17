package io.github.mkliszczun.fridge.e2e;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${POSTGRES_TEST_URL}",
        "spring.datasource.username=fridge_test",
        "spring.datasource.password=fridge_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "abuse.enabled=true", "abuse.login-per15-minutes=3",
        "abuse.registrations-per-hour=2", "abuse.global-registrations-per-day=3",
        "ai.budget.global-daily-usd=0.10"
})
class PostgresAbuseProtectionTest extends AbuseProtectionTest {}
