package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.account.PasswordResetMailer;
import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.enums.Role;
import io.github.mkliszczun.fridge.repository.UserRepository;
import io.github.mkliszczun.fridge.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityE2ETest extends VerifiedAccountTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @MockitoBean PasswordResetMailer mailer;
    static final String PASSWORD = "Secret123!";

    @Test
    void healthIsPublicButApplicationDataStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    record Account(String email, UUID id, String token, String refresh) {}

    Account register() throws Exception {
        String email = UUID.randomUUID() + "@test.local";
        var result = registerVerified(email, PASSWORD);
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new Account(email, users.findByUsername(email).orElseThrow().getId(),
                body.path("token").asText(), body.path("refreshToken").asText());
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Account account) {
        return request.header("Authorization", "Bearer " + account.token());
    }

    @Test
    void catalogCanBeAddedByUsersButOnlyAdminsCanEditAndDelete() throws Exception {
        Account user = register();
        var result = mvc.perform(json(as(post("/api/products"), user), Map.of(
                "name", "Mleko", "productType", "DAIRY", "defaultUnit", "MILLILITER")))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(json(as(patch("/api/products/{id}/shelf-life-after-opening", id), user),
                Map.of("shelfLifeAfterOpeningDays", 5))).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/products/{id}", id), user)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/products"), user)).andExpect(status().isOk());

        Account admin = register();
        UserEntity adminEntity = users.findById(admin.id()).orElseThrow();
        adminEntity.setRoles(Set.of(Role.USER, Role.ADMIN));
        users.save(adminEntity);
        mvc.perform(json(as(patch("/api/products/{id}/shelf-life-after-opening", id), admin),
                Map.of("shelfLifeAfterOpeningDays", 5))).andExpect(status().isOk())
                .andExpect(jsonPath("$.shelfLifeAfterOpeningDays").value(5));
        mvc.perform(as(delete("/api/products/{id}", id), admin)).andExpect(status().isNoContent());
    }

    @Test
    void refreshRotationDetectsReplayAndLogoutRevokesAllSessions() throws Exception {
        Account user = register();
        var rotated = mvc.perform(json(post("/auth/refresh"), Map.of("refreshToken", user.refresh())))
                .andExpect(status().isOk()).andReturn();
        JsonNode tokens = mapper.readTree(rotated.getResponse().getContentAsString());
        assertThat(tokens.path("refreshToken").asText()).isNotEqualTo(user.refresh());
        mvc.perform(json(post("/auth/refresh"), Map.of("refreshToken", user.refresh())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + tokens.path("token").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/refresh"), Map.of("refreshToken", tokens.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        var login = mvc.perform(json(post("/auth/login"), Map.of("login", user.email(), "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        String access = mapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + access)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/login"), Map.of("login", user.email(), "password", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledLockedDeletedAndMalformedIdentitiesCannotUseJwt() throws Exception {
        Account user = register();
        UserEntity entity = users.findById(user.id()).orElseThrow();
        entity.setEnabled(false);
        users.save(entity);
        mvc.perform(as(get("/api/me"), user)).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/refresh"), Map.of("refreshToken", user.refresh())))
                .andExpect(status().isUnauthorized());
        entity.setEnabled(true);
        entity.setAccountNonLocked(false);
        users.save(entity);
        mvc.perform(as(get("/api/me"), user)).andExpect(status().isUnauthorized());
        for (String token : List.of("garbage", jwt.generateToken("nobody", UUID.randomUUID(), List.of("ADMIN")),
                jwt.generateToken(user.email(), List.of("ADMIN")))) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void resetIsPrivateOneTimeExpiringAndRevokesOldCredentials() throws Exception {
        Account user = register();
        var known = mvc.perform(json(post("/auth/password/forgot"), Map.of("email", user.email())))
                .andExpect(status().isAccepted()).andReturn();
        var unknown = mvc.perform(json(post("/auth/password/forgot"), Map.of("email", "missing-" + user.email())))
                .andExpect(status().isAccepted()).andReturn();
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        mvc.perform(json(post("/auth/password/forgot"), Map.of("email", user.email()))).andExpect(status().isAccepted());
        var token = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(1)).send(eq(user.email()), token.capture());
        assertThat(users.findById(user.id()).orElseThrow().getPasswordResetHash()).isNotEqualTo(token.getValue());
        assertThat(known.getResponse().getContentAsString()).doesNotContain(token.getValue());
        String newPassword = "NewSecret456!";
        mvc.perform(json(post("/auth/password/reset"), Map.of("token", token.getValue(), "password", newPassword)))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/auth/password/reset"), Map.of("token", token.getValue(), "password", PASSWORD)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/me"), user)).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/refresh"), Map.of("refreshToken", user.refresh()))).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/login"), Map.of("login", user.email(), "password", PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/login"), Map.of("login", user.email(), "password", newPassword))).andExpect(status().isOk());

        Account expired = register();
        mvc.perform(json(post("/auth/password/forgot"), Map.of("email", expired.email()))).andExpect(status().isAccepted());
        verify(mailer).send(eq(expired.email()), token.capture());
        UserEntity entity = users.findById(expired.id()).orElseThrow();
        entity.setPasswordResetExpiresAt(Instant.now().minusSeconds(1));
        users.save(entity);
        mvc.perform(json(post("/auth/password/reset"), Map.of("token", token.getValue(), "password", newPassword)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/reset-password.html")).andExpect(status().isOk());
        mvc.perform(get("/reset-password.js")).andExpect(status().isOk());
    }

    @Test
    void premiumIsServerControlledAndExpiresWithoutNewJwt() throws Exception {
        Account user = register();
        Account admin = register();
        UserEntity entity = users.findById(admin.id()).orElseThrow();
        entity.setRoles(Set.of(Role.ADMIN, Role.USER));
        users.save(entity);
        mvc.perform(as(get("/api/me"), user)).andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.adsEnabled").value(true));
        String until = Instant.now().plusSeconds(3600).toString();
        mvc.perform(json(as(put("/admin/users/{id}/premium", user.id()), user), Map.of("premiumUntil", until)))
                .andExpect(status().isForbidden());
        mvc.perform(json(as(put("/admin/users/{id}/premium", user.id()), admin), Map.of("premiumUntil", until)))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/me"), user)).andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.adsEnabled").value(false));
        entity = users.findById(user.id()).orElseThrow();
        entity.setPremiumUntil(Instant.now().minusSeconds(1));
        users.save(entity);
        mvc.perform(as(get("/api/me"), user)).andExpect(jsonPath("$.plan").value("FREE"));
        mvc.perform(json(as(put("/admin/users/{id}/premium", user.id()), admin), Map.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.adsEnabled").value(true));
    }

    @Test
    void deletionRemovesPrivateDataAndTransfersSharedFridgeWithoutOrphans() throws Exception {
        Account owner = register();
        Account member = register();
        UUID shared = createFridge(owner, "Shared");
        UUID personal = createFridge(owner, "Personal");
        jdbc.update("insert into fridge_member(id,fridge_id,user_id,role_in_fridge,is_default) values(?,?,?,'MEMBER',false)",
                UUID.randomUUID(), shared, member.id());
        UUID item = UUID.randomUUID();
        jdbc.update("insert into fridge_item(id,fridge_id,owner_user_id,amount,unit,state) values(?,?,?,1,'PIECE','SEALED')", item, shared, owner.id());
        jdbc.update("insert into fridge_item(id,fridge_id,owner_user_id,amount,unit,state) values(?,?,?,1,'PIECE','SEALED')", UUID.randomUUID(), personal, owner.id());
        UUID recipe = UUID.randomUUID();
        jdbc.update("insert into recipe(id,owner_user_id,name,instructions,servings) values(?,?,'Soup','Cook',1)", recipe, owner.id());
        UUID meal = UUID.randomUUID();
        jdbc.update("insert into planned_meal(id,fridge_id,created_by_user_id,source_recipe_id,recipe_name,recipe_instructions,recipe_servings,planned_date,servings) values(?,?,?,?,'Soup','Cook',1,CURRENT_DATE,1)",
                meal, shared, owner.id(), recipe);
        UUID ingredient = UUID.randomUUID();
        jdbc.update("insert into planned_meal_ingredient(id,planned_meal_id,name,is_optional,display_order) values(?,?,'Water',false,0)", ingredient, meal);
        jdbc.update("insert into planned_meal_reservation(id,planned_meal_ingredient_id,fridge_item_id,amount) values(?,?,?,1)", UUID.randomUUID(), ingredient, item);
        UUID shopping = UUID.randomUUID();
        jdbc.update("insert into shopping_list_item(id,fridge_id,name,is_quantified,is_checked) values(?,?,'Water',false,false)", shopping, shared);
        jdbc.update("insert into shopping_list_item_source(id,shopping_list_item_id,planned_meal_ingredient_id) values(?,?,?)", UUID.randomUUID(), shopping, ingredient);
        mvc.perform(json(as(delete("/api/me"), owner), Map.of("password", "wrong"))).andExpect(status().isUnauthorized());
        assertThat(users.existsById(owner.id())).isTrue();
        mvc.perform(json(as(delete("/api/me"), owner), Map.of("password", PASSWORD))).andExpect(status().isNoContent());
        assertThat(users.existsById(owner.id())).isFalse();
        assertThat(users.existsById(member.id())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from fridge where id = ?", Long.class, personal)).isZero();
        assertThat(jdbc.queryForObject("select role_in_fridge from fridge_member where fridge_id = ? and user_id = ?", String.class, shared, member.id())).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("select owner_user_id from fridge_item where id = ?", UUID.class, item)).isNull();
        for (String table : List.of("recipe", "planned_meal", "planned_meal_ingredient")) {
            UUID id = table.equals("recipe") ? recipe : table.equals("planned_meal") ? meal : ingredient;
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where id = ?", Long.class, id)).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from shopping_list_item_source where planned_meal_ingredient_id = ?", Long.class, ingredient)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ?", Long.class, owner.id())).isZero();
        mvc.perform(as(get("/api/me"), owner)).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/fridges"), member)).andExpect(status().isOk());
    }

    UUID createFridge(Account user, String name) throws Exception {
        var result = mvc.perform(json(as(post("/api/fridges"), user), Map.of("name", name)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    @Test
    void simultaneousRefreshCannotProduceTwoValidSessions() throws Exception {
        Account user = register();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> refresh = () -> {
                start.await();
                return mvc.perform(json(post("/auth/refresh"), Map.of("refreshToken", user.refresh()))).andReturn();
            };
            var first = pool.submit(refresh);
            var second = pool.submit(refresh);
            start.countDown();
            var a = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).containsExactlyInAnyOrder(200, 401);
            var success = a.getResponse().getStatus() == 200 ? a : b;
            String access = mapper.readTree(success.getResponse().getContentAsString()).path("token").asText();
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void expiredAccessAndRefreshTokensAreRejected() throws Exception {
        Account user = register();
        jdbc.update("update refresh_token set expires_at = ? where user_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user.id());
        mvc.perform(json(post("/auth/refresh"), Map.of("refreshToken", user.refresh()))).andExpect(status().isUnauthorized());
        var properties = new io.github.mkliszczun.fridge.util.JwtProperties();
        properties.setSecret("MySuperStrongTestSecretKeyWithAtLeast32Chars123");
        properties.setExpiration(-1000);
        String expired = new JwtUtil(properties).generateToken(user.email(), user.id(), List.of("USER"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void mailFailureDoesNotRevealAccountOrConsumeResetCooldown() throws Exception {
        Account user = register();
        doThrow(new org.springframework.mail.MailSendException("SMTP failed"))
                .when(mailer).send(eq(user.email()), anyString());
        mvc.perform(json(post("/auth/password/forgot"), Map.of("email", user.email()))).andExpect(status().isAccepted());
        var entity = users.findById(user.id()).orElseThrow();
        assertThat(entity.getPasswordResetHash()).isNull();
        assertThat(entity.getPasswordResetRequestedAt()).isNull();
    }
}
