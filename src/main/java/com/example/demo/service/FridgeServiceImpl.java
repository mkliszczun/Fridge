package com.example.demo.service;

import com.example.demo.enums.FridgeRole;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.fridge.Fridge;
import com.example.demo.fridge.FridgeMember;
import com.example.demo.repository.FridgeMemberRepository;
import com.example.demo.repository.FridgeRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FridgeServiceImpl implements FridgeService {

    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository memberRepository;

    public FridgeServiceImpl(FridgeRepository fridgeRepository,
                         FridgeMemberRepository memberRepository) {
        this.fridgeRepository = fridgeRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    @Override
    public Fridge createFridge(String name, UUID currentUserId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // (opcjonalnie) sprawdź duplikaty nazw per user — pomijam na teraz

        Fridge fridge = new Fridge();
        fridge.setName(name);
        fridge = fridgeRepository.save(fridge);

        FridgeMember owner = new FridgeMember();
        owner.setFridge(fridge);
        owner.setUserId(currentUserId);
        owner.setRoleInFridge(FridgeRole.OWNER);
        owner.setIsDefault(!memberRepository.existsDefaultForUser(currentUserId));
        memberRepository.save(owner);

        return fridge;
    }

    @Transactional
    @Override
    public void deleteFridge(UUID fridgeId, UUID currentUserId, boolean hardDeleteIfEmpty) {
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new NotFoundException("Fridge not found"));

        FridgeMember membership = memberRepository.findByFridgeIdAndUserId(fridgeId, currentUserId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this fridge"));

        if (membership.getRoleInFridge() != FridgeRole.OWNER) {
            throw new ForbiddenException("Only OWNER can delete fridge");
        }

        // Strategia na start: twarde usunięcie tylko jeśli brak aktywnych itemów (sprawdzi serwis itemów).
        if (!hardDeleteIfEmpty) {
            throw new ConflictException("Soft delete not implemented yet");
        }

        // Usuń członkostwa, potem lodówkę
        // (Cascade orphanRemoval=true na Fridge.members może to załatwić,
        // ale mamy Fridge -> members jednostronnie; więc kasujemy repozytorium członków)
        memberRepository.findByFridgeIdAndUserId(fridgeId, currentUserId)
                .ifPresent(memberRepository::delete);
        // W praniu: usuń też innych członków
        // (dla uproszczenia pobierz i usuń wszystkich)
        List<FridgeMember> allMembers = fridge.getMembers().stream().toList();
        memberRepository.deleteAll(allMembers);

        fridgeRepository.delete(fridge);
    }

    @Override
    @Transactional
    public Fridge requireMembership(UUID fridgeId, UUID currentUserId) {
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new NotFoundException("Fridge not found"));
        if (!memberRepository.existsByFridgeIdAndUserId(fridgeId, currentUserId)) {
            throw new ForbiddenException("Not a member of this fridge");
        }
        return fridge;
    }

    @Override
    @Transactional
    public List<Fridge> listMyFridges(UUID currentUserId) {
        // Najprościej przez members → fridges. Możesz dodać dedykowane zapytanie; tu użyję encji:
        // (W praktyce lepiej napisać custom query + DTO projection)
        // Poniżej: pobierz wszystkie członkostwa i zmapuj na lodówki
        return memberRepository.findAll().stream()
                .filter(m -> m.getUserId().equals(currentUserId))
                .map(FridgeMember::getFridge)
                .distinct()
                .toList();
    }

    @Override
    public long countMembers(UUID fridgeId) {
        return memberRepository.countByFridgeId(fridgeId);
    }
}
