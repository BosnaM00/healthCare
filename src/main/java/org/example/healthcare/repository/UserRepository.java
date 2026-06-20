package org.example.healthcare.repository;

import org.example.healthcare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Looks up a user by their Google subject identifier.
     * Used during Google Sign-In to check if this Google account is already linked.
     */
    Optional<User> findByGoogleId(String googleId);
}
