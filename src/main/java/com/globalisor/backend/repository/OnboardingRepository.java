package com.globalisor.backend.repository;

import com.globalisor.backend.model.Onboarding;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface OnboardingRepository extends MongoRepository<Onboarding, String> {
    Optional<Onboarding> findByClientId(String clientId);
    java.util.List<Onboarding> findByClientIdIn(java.util.List<String> clientIds);
    java.util.List<Onboarding> findAllByOrderByCreatedAtDesc();
    @org.springframework.data.mongodb.repository.Query(value = "{}", fields = "{ 'id': 1, 'clientId': 1, 'displayClientId': 1, 'clientEmail': 1, 'clientName': 1, 'journeyType': 1, 'status': 1, 'progressPercent': 1, 'createdAt': 1, 'updatedAt': 1 }")
    java.util.List<Onboarding> findAllLightweight();
}
