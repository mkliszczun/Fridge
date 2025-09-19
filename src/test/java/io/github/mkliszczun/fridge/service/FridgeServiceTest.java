package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.FridgeRole;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.ForbiddenException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.fridge.FridgeMember;
import io.github.mkliszczun.fridge.repository.FridgeMemberRepository;
import io.github.mkliszczun.fridge.repository.FridgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FridgeServiceTest {

    @Mock
    FridgeRepository fridgeRepository;
    @Mock
    FridgeMemberRepository memberRepository;

    @InjectMocks
    FridgeServiceImpl fridgeService;

    UUID userId = UUID.randomUUID();
    UUID fridgeId = UUID.randomUUID();

    Fridge persistedFridge;

    @BeforeEach
    void setup() {
        persistedFridge = new Fridge();
        // „simulated” ID
        try {
            var f = Fridge.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(persistedFridge, fridgeId);
        } catch (Exception ignore) {
        }
        persistedFridge.setName("Dom");
    }

    // -------- createFridge --------

    @Test
    void createFridge_createsFridge_andOwnerMembership_setDefaultIfNone() {
        when(fridgeRepository.save(any(Fridge.class))).thenReturn(persistedFridge);
        when(memberRepository.existsDefaultForUser(userId)).thenReturn(false);

        Fridge result = fridgeService.createFridge("Dom", userId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Dom");
        // weryfikujemy zapis lodówki
        verify(fridgeRepository, times(1)).save(any(Fridge.class));
        // weryfikujemy zapis członkostwa OWNER z isDefault=true
        ArgumentCaptor<FridgeMember> captor = ArgumentCaptor.forClass(FridgeMember.class);
        verify(memberRepository, times(1)).save(captor.capture());
        FridgeMember savedMember = captor.getValue();
        assertThat(savedMember.getUserId()).isEqualTo(userId);
        assertThat(savedMember.getRoleInFridge()).isEqualTo(FridgeRole.OWNER);
        assertThat(savedMember.getIsDefault()).isTrue();
    }

    @Test
    void createFridge_setsDefaultFalse_ifUserAlreadyHasDefault() {
        when(fridgeRepository.save(any(Fridge.class))).thenReturn(persistedFridge);
        when(memberRepository.existsDefaultForUser(userId)).thenReturn(true);

        fridgeService.createFridge("Praca", userId);

        ArgumentCaptor<FridgeMember> captor = ArgumentCaptor.forClass(FridgeMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isFalse();
    }

    @Test
    void createFridge_blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> fridgeService.createFridge("  ", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
        verifyNoInteractions(memberRepository);
    }

    // -------- requireMembership --------

    @Test
    void requireMembership_ok_whenMember() {
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(persistedFridge));
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(true);

        Fridge f = fridgeService.requireMembership(fridgeId, userId);

        assertThat(f).isEqualTo(persistedFridge);
    }

    @Test
    void requireMembership_notFoundFridge_throws() {
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fridgeService.requireMembership(fridgeId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Fridge not found");
    }

    @Test
    void requireMembership_notMember_throwsForbidden() {
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(persistedFridge));
        when(memberRepository.existsByFridgeIdAndUserId(fridgeId, userId)).thenReturn(false);

        assertThatThrownBy(() -> fridgeService.requireMembership(fridgeId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Not a member");
    }

    // -------- deleteFridge --------

    @Test
    void deleteFridge_asOwner_hardDelete_deletesMembersAndFridge() {
        // owner membership
        FridgeMember owner = new FridgeMember();
        owner.setFridge(persistedFridge);
        owner.setUserId(userId);
        owner.setRoleInFridge(FridgeRole.OWNER);
        // symulacja: w encji Fridge.members są jacyś członkowie (np. 2)
        FridgeMember other = new FridgeMember();
        other.setFridge(persistedFridge);
        other.setUserId(UUID.randomUUID());
        other.setRoleInFridge(FridgeRole.MEMBER);

        // „podmienimy” kolekcję members via reflection (na test wystarczy)
        try {
            var m = Fridge.class.getDeclaredField("members");
            m.setAccessible(true);
            m.set(persistedFridge, List.of(owner, other).stream().collect(java.util.stream.Collectors.toSet()));
        } catch (Exception ignore) {
        }

        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(persistedFridge));
        when(memberRepository.findByFridgeIdAndUserId(fridgeId, userId)).thenReturn(Optional.of(owner));

        fridgeService.deleteFridge(fridgeId, userId, true);

        // sprawdzamy usuwanie członkostw i lodówki
        verify(memberRepository, atLeastOnce()).deleteAll(anyList());
        verify(fridgeRepository, times(1)).delete(persistedFridge);
    }

    @Test
    void deleteFridge_asMemberNotOwner_forbidden() {
        FridgeMember member = new FridgeMember();
        member.setFridge(persistedFridge);
        member.setUserId(userId);
        member.setRoleInFridge(FridgeRole.MEMBER);

        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(persistedFridge));
        when(memberRepository.findByFridgeIdAndUserId(fridgeId, userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> fridgeService.deleteFridge(fridgeId, userId, true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only OWNER");
        verify(fridgeRepository, never()).delete(any());
    }

    @Test
    void deleteFridge_notMember_forbidden() {
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(persistedFridge));
        when(memberRepository.findByFridgeIdAndUserId(fridgeId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fridgeService.deleteFridge(fridgeId, userId, true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Not a member");
    }

    @Test
    void deleteFridge_softDeleteNotImplemented_conflict() {
        FridgeMember owner = new FridgeMember();
        owner.setFridge(persistedFridge);
        owner.setUserId(userId);
        owner.setRoleInFridge(FridgeRole.OWNER);

        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.of(persistedFridge));
        when(memberRepository.findByFridgeIdAndUserId(fridgeId, userId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> fridgeService.deleteFridge(fridgeId, userId, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Soft delete not implemented");
    }

    @Test
    void deleteFridge_notFound_throws() {
        when(fridgeRepository.findById(fridgeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fridgeService.deleteFridge(fridgeId, userId, true))
                .isInstanceOf(NotFoundException.class);
    }
}
