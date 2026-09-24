package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.account.AccountDeletionService;
import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.enums.FridgeRole;
import io.github.mkliszczun.fridge.enums.Role;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.FridgeMember;
import io.github.mkliszczun.fridge.repository.*;
import io.github.mkliszczun.fridge.service.FridgeInvitationService;
import io.github.mkliszczun.fridge.service.FridgeService;
import io.github.mkliszczun.fridge.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class FridgeInvitationFlowE2ETest extends VerifiedAccountTestSupport {
    private static final String PASSWORD = "Secret123!";
    private static final String INBOX = "/api/fridge-invitations";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired FridgeRepository fridgeRepository;
    @Autowired FridgeMemberRepository members;
    @Autowired FridgeInvitationRepository invitations;
    @Autowired FridgeInvitationService service;
    @Autowired FridgeService fridges;
    @Autowired AccountDeletionService deletion;
    @Autowired JwtUtil jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @Test void invitationGivesAccessOnlyAfterRecipientAcceptsWithoutSendingEmail() throws Exception {
        var f = fixture();
        Instant before = Instant.now();
        invite(f, "  " + f.recipient().getEmail().toUpperCase(Locale.ROOT) + "  ").andExpect(status().isAccepted());
        var inbox = inbox(f.recipient());
        assertThat(inbox.size()).isEqualTo(1);
        var entry = inbox.get(0);
        assertThat(entry.path("fridgeName").asText()).isEqualTo("Shared fridge");
        assertThat(entry.path("invitedByUserId").asText()).isEqualTo(f.owner().getId().toString());
        assertThat(entry.path("invitedByEmail").asText()).isEqualTo(f.owner().getEmail());
        assertThat(entry.hasNonNull("createdAt")).isTrue();
        assertThat(Instant.parse(entry.path("expiresAt").asText()))
                .isBetween(before.plus(Duration.ofDays(7)), Instant.now().plus(Duration.ofDays(7)));
        assertThat(inbox(f.owner()).size()).isZero();
        mvc.perform(as(get("/api/fridges"), f.recipient())).andExpect(jsonPath("$").isEmpty());
        mvc.perform(as(get("/api/fridges/{id}/shopping-list", f.fridge()), f.recipient()))
                .andExpect(status().isForbidden());

        String id = entry.path("id").asText();
        accept(id, f.recipient()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(f.fridge().toString()));
        var membership = members.findByFridgeIdAndUserId(f.fridge(), f.recipient().getId()).orElseThrow();
        assertThat(membership.getRoleInFridge()).isEqualTo(FridgeRole.MEMBER);
        assertThat(membership.getIsDefault()).isTrue();
        assertThat(inbox(f.recipient()).size()).isZero();
        mvc.perform(as(get("/api/fridges"), f.recipient()))
                .andExpect(jsonPath("$[0].id").value(f.fridge().toString()));
        mvc.perform(as(get("/api/fridges/{id}/shopping-list", f.fridge()), f.recipient()))
                .andExpect(status().isOk());
        accept(id, f.recipient()).andExpect(status().isNotFound());
        assertThat(members.countByFridgeId(f.fridge())).isEqualTo(2);
        verifyNoInteractions(verificationMailer);
    }

    @Test void declinedInvitationDoesNotGrantAccessAndCanBeSentAgain() throws Exception {
        var f = fixture();
        String id = send(f);
        decline(id, f.recipient()).andExpect(status().isNoContent());
        accept(id, f.recipient()).andExpect(status().isNotFound());
        decline(id, f.recipient()).andExpect(status().isNotFound());
        assertThat(members.existsByFridgeIdAndUserId(f.fridge(), f.recipient().getId())).isFalse();
        assertThat(send(f)).isNotEqualTo(id);
    }

    @Test void duplicatesDoNotExtendExpiryAndExpiredInvitationsCanBeReplaced() throws Exception {
        var f = fixture();
        String id = send(f);
        String expiry = inbox(f.recipient()).get(0).path("expiresAt").asText();
        assertThat(send(f)).isEqualTo(id);
        assertThat(inbox(f.recipient()).get(0).path("expiresAt").asText()).isEqualTo(expiry);
        jdbc.update("update fridge_invitation set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), UUID.fromString(id));
        assertThat(inbox(f.recipient()).size()).isZero();
        accept(id, f.recipient()).andExpect(status().isNotFound());
        decline(id, f.recipient()).andExpect(status().isNotFound());
        assertThat(members.existsByFridgeIdAndUserId(f.fridge(), f.recipient().getId())).isFalse();
        assertThat(send(f)).isNotEqualTo(id);
    }

    @Test void onlyOwnerCanInviteEvenIfCallerIsAGlobalAdmin() throws Exception {
        var f = fixture();
        String id = send(f);
        accept(id, f.recipient()).andExpect(status().isOk());
        var outsider = user();
        for (UserEntity caller : List.of(f.recipient(), outsider)) {
            invite(f.fridge(), caller, outsider.getEmail()).andExpect(status().isForbidden());
        }
        outsider.setRoles(Set.of(Role.ADMIN));
        users.saveAndFlush(outsider);
        invite(f.fridge(), outsider, user().getEmail()).andExpect(status().isForbidden());
        invite(UUID.randomUUID(), f.owner(), outsider.getEmail()).andExpect(status().isNotFound());
    }

    @Test void onlyRecipientCanListAcceptOrDeclineAnInvitation() throws Exception {
        var f = fixture();
        String id = send(f);
        for (UserEntity caller : List.of(f.owner(), user())) {
            assertThat(inbox(caller).size()).isZero();
            accept(id, caller).andExpect(status().isNotFound());
            decline(id, caller).andExpect(status().isNotFound());
        }
        assertThat(inbox(f.recipient()).size()).isEqualTo(1);
        accept(UUID.randomUUID().toString(), f.recipient()).andExpect(status().isNotFound());
    }

    @Test void authenticationAndValidEmailAreRequired() throws Exception {
        var f = fixture();
        String id = send(f);
        mvc.perform(post("/api/fridges/{id}/invitations", f.fridge())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"any@test.local\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(INBOX)).andExpect(status().isUnauthorized());
        mvc.perform(post(INBOX + "/{id}/accept", id)).andExpect(status().isUnauthorized());
        mvc.perform(post(INBOX + "/{id}/decline", id)).andExpect(status().isUnauthorized());
        for (String input : List.of("{}", "{\"email\":null}", "{\"email\":\" \"}",
                "{\"email\":\"not-an-email\"}", json.writeValueAsString(Map.of("email", "a".repeat(255) + "@test.local")))) {
            mvc.perform(as(post("/api/fridges/{id}/invitations", f.fridge()), f.owner())
                    .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
        }
        f.recipient().setEmailVerifiedAt(null);
        users.saveAndFlush(f.recipient());
        mvc.perform(as(get(INBOX), f.recipient())).andExpect(status().isUnauthorized());
        accept(id, f.recipient()).andExpect(status().isUnauthorized());
    }

    @Test void responsesDoNotRevealUnknownUnverifiedInactiveOrAlreadyJoinedAccounts() throws Exception {
        var f = fixture();
        var unverified = user();
        unverified.setEmailVerifiedAt(null);
        users.saveAndFlush(unverified);
        var disabled = user();
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);
        String expected = invite(f, f.recipient().getEmail()).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        for (String email : List.of(UUID.randomUUID() + "@test.local", unverified.getEmail(), disabled.getEmail(), f.owner().getEmail())) {
            invite(f, email).andExpect(status().isAccepted()).andExpect(content().string(expected));
        }
        assertThat(service.list(unverified.getId())).isEmpty();
        assertThat(service.list(disabled.getId())).isEmpty();
        String id = inbox(f.recipient()).get(0).path("id").asText();
        accept(id, f.recipient()).andExpect(status().isOk());
        invite(f, f.recipient().getEmail()).andExpect(status().isAccepted()).andExpect(content().string(expected));
        assertThat(inbox(f.recipient()).size()).isZero();
    }

    @Test void existingDefaultFridgeIsPreserved() throws Exception {
        var f = fixture();
        UUID own = fridges.createFridge("Own", f.recipient().getId()).getId();
        accept(send(f), f.recipient()).andExpect(status().isOk());
        assertThat(members.findByFridgeIdAndUserId(own, f.recipient().getId()).orElseThrow().getIsDefault()).isTrue();
        assertThat(members.findByFridgeIdAndUserId(f.fridge(), f.recipient().getId()).orElseThrow().getIsDefault()).isFalse();
    }

    @Test void invitationStaysWithUserIdWhenEmailChanges() throws Exception {
        var f = fixture();
        String id = send(f);
        String oldEmail = f.recipient().getEmail();
        f.recipient().setEmail("changed-" + UUID.randomUUID() + "@test.local");
        users.saveAndFlush(f.recipient());
        var other = user();
        other.setEmail(oldEmail);
        users.saveAndFlush(other);
        assertThat(inbox(other).size()).isZero();
        accept(id, other).andExpect(status().isNotFound());
        accept(id, f.recipient()).andExpect(status().isOk());
    }

    @Test void usernameIsNotUsedInsteadOfVerifiedEmail() throws Exception {
        var f = fixture();
        String username = f.recipient().getUsername();
        f.recipient().setEmail("mail-" + UUID.randomUUID() + "@test.local");
        users.saveAndFlush(f.recipient());
        invite(f, username).andExpect(status().isAccepted());
        assertThat(inbox(f.recipient()).size()).isZero();
        invite(f, f.recipient().getEmail()).andExpect(status().isAccepted());
        assertThat(inbox(f.recipient()).size()).isEqualTo(1);
    }

    @Test void existingMemberDoesNotGetDuplicateMembershipOrLoseOwnerRole() throws Exception {
        var f = fixture();
        String id = send(f);
        addMember(f.fridge(), f.recipient(), FridgeRole.OWNER);
        accept(id, f.recipient()).andExpect(status().isOk());
        assertThat(members.countByFridgeId(f.fridge())).isEqualTo(2);
        assertThat(members.findByFridgeIdAndUserId(f.fridge(), f.recipient().getId()).orElseThrow().getRoleInFridge())
                .isEqualTo(FridgeRole.OWNER);
    }

    @Test void invitationCannotBeAcceptedAfterSenderLosesOwnerRole() throws Exception {
        var f = fixture();
        String id = send(f);
        var owner = members.findByFridgeIdAndUserId(f.fridge(), f.owner().getId()).orElseThrow();
        owner.setRoleInFridge(FridgeRole.MEMBER);
        members.saveAndFlush(owner);
        accept(id, f.recipient()).andExpect(status().isConflict());
        assertThat(members.existsByFridgeIdAndUserId(f.fridge(), f.recipient().getId())).isFalse();
    }

    @Test void deletingFridgeOrEitherAccountRemovesInvitations() throws Exception {
        var removedFridge = fixture();
        String first = send(removedFridge);
        fridges.deleteFridge(removedFridge.fridge(), removedFridge.owner().getId(), true);
        assertThat(invitations.existsById(UUID.fromString(first))).isFalse();
        accept(first, removedFridge.recipient()).andExpect(status().isNotFound());

        var removedRecipient = fixture();
        String second = send(removedRecipient);
        deletion.delete(removedRecipient.recipient().getId(), PASSWORD);
        assertThat(invitations.existsById(UUID.fromString(second))).isFalse();
        assertThat(fridgeRepository.existsById(removedRecipient.fridge())).isTrue();

        var removedOwner = fixture();
        addMember(removedOwner.fridge(), user(), FridgeRole.MEMBER);
        String third = send(removedOwner);
        deletion.delete(removedOwner.owner().getId(), PASSWORD);
        assertThat(invitations.existsById(UUID.fromString(third))).isFalse();
        assertThat(fridgeRepository.existsById(removedOwner.fridge())).isTrue();
        accept(third, removedOwner.recipient()).andExpect(status().isNotFound());

        var lastOwner = fixture();
        String fourth = send(lastOwner);
        deletion.delete(lastOwner.owner().getId(), PASSWORD);
        assertThat(invitations.existsById(UUID.fromString(fourth))).isFalse();
        assertThat(fridgeRepository.existsById(lastOwner.fridge())).isFalse();
    }

    @Test void simultaneousInvitationsAndAcceptsCreateOneInvitationAndOneMembership() throws Exception {
        var f = fixture();
        concurrently(List.of(
                () -> { service.invite(f.fridge(), f.owner().getId(), f.recipient().getEmail()); return 202; },
                () -> { service.invite(f.fridge(), f.owner().getId(), f.recipient().getEmail()); return 202; }));
        var inbox = inbox(f.recipient());
        assertThat(inbox.size()).isEqualTo(1);
        String id = inbox.get(0).path("id").asText();
        var results = concurrently(List.of(
                () -> accept(id, f.recipient()).andReturn().getResponse().getStatus(),
                () -> accept(id, f.recipient()).andReturn().getResponse().getStatus()));
        assertThat(results).containsExactlyInAnyOrder(200, 404);
        assertThat(members.countByFridgeId(f.fridge())).isEqualTo(2);
    }

    @Test void acceptingAndDecliningAtOnceResolveInvitationOnlyOnce() throws Exception {
        var f = fixture();
        String id = send(f);
        var results = concurrently(List.of(
                () -> accept(id, f.recipient()).andReturn().getResponse().getStatus(),
                () -> decline(id, f.recipient()).andReturn().getResponse().getStatus()));
        assertThat(results).contains(404);
        assertThat(results.stream().filter(status -> status == 200 || status == 204).count()).isEqualTo(1);
        assertThat(members.existsByFridgeIdAndUserId(f.fridge(), f.recipient().getId())).isEqualTo(results.get(0) == 200);
        assertThat(invitations.existsById(UUID.fromString(id))).isFalse();
    }

    @Test void acceptingTwoFridgesAtOnceChoosesOnlyOneDefault() throws Exception {
        var f = fixture();
        UUID secondFridge = fridges.createFridge("Second", f.owner().getId()).getId();
        String first = send(f);
        service.invite(secondFridge, f.owner().getId(), f.recipient().getEmail());
        UUID second = invitations.findByFridgeIdAndInvitedUserId(secondFridge, f.recipient().getId()).orElseThrow().getId();
        concurrently(List.of(
                () -> { service.accept(UUID.fromString(first), f.recipient().getId()); return 200; },
                () -> { service.accept(second, f.recipient().getId()); return 200; }));
        assertThat(defaultCount(f.recipient())).isEqualTo(1);
        assertThat(fridges.listMyFridges(f.recipient().getId())).hasSize(2);
    }

    @Test void creatingFridgeWhileAcceptingInvitationChoosesOnlyOneDefault() throws Exception {
        var f = fixture();
        UUID id = UUID.fromString(send(f));
        concurrently(List.of(
                () -> { service.accept(id, f.recipient().getId()); return 200; },
                () -> { fridges.createFridge("Own", f.recipient().getId()); return 201; }));
        assertThat(defaultCount(f.recipient())).isEqualTo(1);
        assertThat(fridges.listMyFridges(f.recipient().getId())).hasSize(2);
    }

    @Test void deletingInviterWhileAcceptingDoesNotLeaveOrphanInvitationOrFridge() throws Exception {
        var f = fixture();
        UUID id = UUID.fromString(send(f));
        var results = concurrently(List.of(
                () -> {
                    try { service.accept(id, f.recipient().getId()); return 200; }
                    catch (NotFoundException ex) { return 404; }
                },
                () -> { deletion.delete(f.owner().getId(), PASSWORD); return 204; }));
        assertThat(users.existsById(f.owner().getId())).isFalse();
        assertThat(invitations.existsById(id)).isFalse();
        if (results.get(0) == 200) {
            assertThat(members.findByFridgeIdAndUserId(f.fridge(), f.recipient().getId()).orElseThrow().getRoleInFridge())
                    .isEqualTo(FridgeRole.OWNER);
        } else {
            assertThat(fridgeRepository.existsById(f.fridge())).isFalse();
        }
    }

    private long defaultCount(UserEntity user) {
        return jdbc.queryForObject("select count(*) from fridge_member where user_id = ? and is_default = true",
                Long.class, user.getId());
    }

    private void addMember(UUID fridgeId, UserEntity user, FridgeRole role) {
        var member = new FridgeMember();
        member.setFridge(fridgeRepository.findById(fridgeId).orElseThrow());
        member.setUserId(user.getId());
        member.setRoleInFridge(role);
        members.saveAndFlush(member);
    }

    private Fixture fixture() {
        var owner = user();
        return new Fixture(owner, user(), fridges.createFridge("Shared fridge", owner.getId()).getId());
    }

    private UserEntity user() {
        var user = new UserEntity();
        user.setUsername(UUID.randomUUID() + "@test.local");
        user.setEmail(user.getUsername());
        user.setPassword(passwords.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        user.setRoles(Set.of(Role.USER));
        return users.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UserEntity user) {
        return request.header("Authorization", "Bearer " + jwt.generateToken(user.getUsername(), user.getId(),
                List.of("ROLE_USER"), user.getTokenVersion()));
    }

    private ResultActions invite(Fixture f, String email) throws Exception { return invite(f.fridge(), f.owner(), email); }

    private ResultActions invite(UUID fridgeId, UserEntity sender, String email) throws Exception {
        return mvc.perform(as(post("/api/fridges/{id}/invitations", fridgeId), sender)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email))));
    }

    private String send(Fixture f) throws Exception {
        invite(f, f.recipient().getEmail()).andExpect(status().isAccepted());
        return inbox(f.recipient()).get(0).path("id").asText();
    }

    private JsonNode inbox(UserEntity user) throws Exception {
        return json.readTree(mvc.perform(as(get(INBOX), user)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions accept(String id, UserEntity user) throws Exception {
        return mvc.perform(as(post(INBOX + "/{id}/accept", id), user));
    }

    private ResultActions decline(String id, UserEntity user) throws Exception {
        return mvc.perform(as(post(INBOX + "/{id}/decline", id), user));
    }

    private List<Integer> concurrently(List<Callable<Integer>> operations) throws Exception {
        var pool = Executors.newFixedThreadPool(operations.size());
        var start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (var operation : operations) futures.add(pool.submit(() -> { start.await(); return operation.call(); }));
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }

    private record Fixture(UserEntity owner, UserEntity recipient, UUID fridge) {}
}
