package com.globalisor.backend.repository;

import com.globalisor.backend.model.Requirement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequirementRepository extends MongoRepository<Requirement, String> {
    Optional<Requirement> findFirstByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<Requirement> findFirstByUserId(String userId);
    List<Requirement> findAllByUserId(String userId);
    Optional<Requirement> findByUserId(String userId);
    List<Requirement> findByUserIdIn(List<String> userIds);
    List<Requirement> findAllByOrderByUpdatedAtDesc();
    @org.springframework.data.mongodb.repository.Query(value = "{}", fields = "{ 'id': 1, 'userId': 1, 'status': 1, 'staff': 1, 'assignedStaffId': 1, 'assignedStaffName': 1, 'createdAt': 1, 'updatedAt': 1, 'data': 1 }")
    List<Requirement> findAllLightweight();
    @org.springframework.data.mongodb.repository.Query(value = "{ 'status': { $nin: ['approved', 'Approved', 'APPROVED', 'completed', 'Completed', 'COMPLETED'] } }", fields = "{ 'id': 1, 'userId': 1, 'status': 1, 'staff': 1, 'assignedStaffId': 1, 'assignedStaffName': 1, 'createdAt': 1, 'updatedAt': 1, 'data': 1 }")
    List<Requirement> findActiveLightweight();
    long countByStatusIgnoreCase(String status);
    long countByStatusIn(List<String> statuses);
    void deleteByUserId(String userId);
}
