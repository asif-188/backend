package com.globalisor.backend.repository;

import com.globalisor.backend.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    @Query("{ '$or': [ { 'clientId': 'all' }, { 'clientId': ?0 } ] }")
    List<Notification> findNotificationsForClient(String clientId);

    List<Notification> findByClientIdIn(Collection<String> clientIds);

    List<Notification> findByClientIdAndRelatedId(String clientId, String relatedId);

    List<Notification> findByRelatedId(String relatedId);
}
