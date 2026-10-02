package com.globalisor.backend.repository;

import com.globalisor.backend.model.ClientDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientDocumentRepository extends MongoRepository<ClientDocument, String> {
    List<ClientDocument> findByClientId(String clientId);
    List<ClientDocument> findByClientIdAndTenantId(String clientId, String tenantId);
    List<ClientDocument> findByClientIdAndCategory(String clientId, String category);
    List<ClientDocument> findByClientIdAndSuggestedModule(String clientId, String suggestedModule);
    List<ClientDocument> findByCompanyName(String companyName);
    List<ClientDocument> findByCompanyNameIgnoreCase(String companyName);
    List<ClientDocument> findByClientIdIn(List<String> clientIds);
    @org.springframework.data.mongodb.repository.Query(value = "{ 'clientId': { $in: ?0 } }", fields = "{ 'id': 1, 'clientId': 1, 'status': 1, 'title': 1, 'category': 1 }")
    List<ClientDocument> findLightweightByClientIdIn(List<String> clientIds);
    List<ClientDocument> findByCategoryIgnoreCase(String category);
    List<ClientDocument> findBySubFolderIgnoreCase(String subFolder);
    Optional<ClientDocument> findFirstByTitleIgnoreCase(String title);
    long countByClientId(String clientId);
    long countByClientIdAndStatusIgnoreCase(String clientId, String status);
}
