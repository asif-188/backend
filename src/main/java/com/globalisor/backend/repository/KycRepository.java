package com.globalisor.backend.repository;

import com.globalisor.backend.model.Kyc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycRepository extends MongoRepository<Kyc, String> {
    Optional<Kyc> findFirstByClientIdOrderByLastUpdatedDesc(String clientId);
    Optional<Kyc> findFirstByClientId(String clientId);
    Optional<Kyc> findByClientId(String clientId);
    List<Kyc> findByClientIdIn(List<String> clientIds);
    long countByStatus(String status);
    long countByStatusIgnoreCase(String status);
}
