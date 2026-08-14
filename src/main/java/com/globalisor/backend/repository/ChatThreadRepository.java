package com.globalisor.backend.repository;

import com.globalisor.backend.model.ChatThread;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatThreadRepository extends MongoRepository<ChatThread, String> {
    List<ChatThread> findByUserIdOrderByUpdatedAtDesc(String userId);
    List<ChatThread> findAllByOrderByUpdatedAtDesc();
}
