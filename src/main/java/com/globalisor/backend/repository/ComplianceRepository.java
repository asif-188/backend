package com.globalisor.backend.repository;

import com.globalisor.backend.model.Compliance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplianceRepository extends MongoRepository<Compliance, String> {
    Optional<Compliance> findFirstByClientIdOrderByLastUpdatedDesc(String clientId);
    Optional<Compliance> findFirstByClientId(String clientId);
    Optional<Compliance> findByClientId(String clientId);
}
