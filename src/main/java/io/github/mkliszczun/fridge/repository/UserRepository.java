package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByPasswordResetHash(String hash);

    @Query("select u.id from UserEntity u where lower(u.email) = :email and u.emailVerifiedAt is not null")
    List<UUID> findVerifiedIdsByEmail(String email);

    @Query("select u from UserEntity u where lower(u.email) = :address or lower(u.username) = :address")
    List<UserEntity> findAddressOwners(String address);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findLockedById(UUID id);
}
