package io.github.mkliszczun.fridge.security;

import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.enums.Role;
import io.github.mkliszczun.fridge.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
public class JpaUserDetailsManager implements UserDetailsManager {

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;

    public JpaUserDetailsManager(UserRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    // ---- UserDetailsService ----
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        return repo.findByUsername(usernameOrEmail)
                .or(() -> repo.findByEmail(usernameOrEmail))
                .map(AppUserDetails::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));
    }

    // Pomocniczo, do filtra JWT (ładowanie po UUID)
    @Transactional(readOnly = true)
    public AppUserDetails loadById(UUID id) throws UsernameNotFoundException {
        return repo.findById(id)
                .map(AppUserDetails::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }

    // ---- UserDetailsManager ----
    @Override
    public void createUser(UserDetails user) {
        if (userExists(user.getUsername())) {
            throw new IllegalStateException("User already exists: " + user.getUsername());
        }
        UserEntity entity = new UserEntity();
        entity.setUsername(user.getUsername());
        entity.setEmail(user.getUsername());
        entity.setPassword(user.getPassword());

        Set<Role> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .map(String::toUpperCase)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        entity.setRoles(roles);

        entity.setEnabled(true);
        entity.setAccountNonExpired(true);
        entity.setAccountNonLocked(true);
        entity.setCredentialsNonExpired(true);

        repo.save(entity);
    }

    @Override
    public void updateUser(UserDetails user) {
        var entity = repo.findByUsername(user.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(user.getUsername()));

        // tylko proste pola; rozszerz wg potrzeb
        entity.setPassword(user.getPassword());
        Set<Role> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .map(String::toUpperCase)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        entity.setRoles(roles);

        repo.save(entity);
    }

    @Override
    public void deleteUser(String username) {
        repo.findByUsername(username).ifPresent(u -> repo.deleteById(u.getId()));
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        throw new UnsupportedOperationException("Use dedicated change-password endpoint");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userExists(String username) {
        return repo.findByUsername(username).isPresent();
    }
}
