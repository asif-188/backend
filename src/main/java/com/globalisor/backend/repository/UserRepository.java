package com.globalisor.backend.repository;

import com.globalisor.backend.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findFirstByEmail(String email);
    Optional<User> findFirstByEmailIgnoreCase(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Boolean existsByEmail(String email);
    List<User> findByRole(String role);
    List<User> findByRoleIgnoreCase(String role);
    List<User> findByRoleIn(List<String> roles);
    long countByRoleIgnoreCase(String role);
    long countByRoleIn(List<String> roles);
}
