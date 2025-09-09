package com.example.demo.repository;

import com.example.demo.fridge.FridgeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface FridgeMemberRepository extends JpaRepository<FridgeMember, UUID> {

    boolean existsByFridgeIdAndUserId(UUID fridgeId, UUID userId);

    long countByFridgeId(UUID fridgeId);

    Optional<FridgeMember> findByFridgeIdAndUserId(UUID fridgeId, UUID userId);

    @Query("select (count(m) > 0) from FridgeMember m where m.userId = :userId and m.isDefault = true")
    boolean existsDefaultForUser(UUID userId);
}