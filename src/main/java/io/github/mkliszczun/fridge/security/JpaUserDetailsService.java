package io.github.mkliszczun.fridge.security;

import io.github.mkliszczun.fridge.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository repo;

    public JpaUserDetailsService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        return repo.findByUsername(usernameOrEmail)
                .or(() -> repo.findByEmail(usernameOrEmail))
                .map(AppUserDetails::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));
    }

    public AppUserDetails loadById(UUID id) throws UsernameNotFoundException {
        return repo.findById(id)
                .map(AppUserDetails::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }
}