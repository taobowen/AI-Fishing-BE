package com.aifishing.user.repo;

import com.aifishing.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByAuthProviderAndAuthSubject(String authProvider, String authSubject);

    Optional<User> findByEmail(String email);
}
