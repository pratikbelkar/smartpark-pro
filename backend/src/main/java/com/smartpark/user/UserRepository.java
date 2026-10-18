package com.smartpark.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    org.springframework.data.domain.Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email, String name, org.springframework.data.domain.Pageable pageable);
}
