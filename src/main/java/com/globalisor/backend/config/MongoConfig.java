package com.globalisor.backend.config;

import com.globalisor.backend.model.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;

@Slf4j
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:globalisor}")
    private String databaseName;

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Bean
    public MongoMappingContext mongoMappingContext() {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setAutoIndexCreation(false);
        return mappingContext;
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoClient mongoClient, MongoMappingContext context) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(new SimpleMongoClientDatabaseFactory(mongoClient, databaseName));
        return new MappingMongoConverter(dbRefResolver, context);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoClient mongoClient, MappingMongoConverter converter) {
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(mongoClient, databaseName), converter);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initIndicesAfterStartup(ApplicationReadyEvent event) {
        try {
            log.info("Starting automatic MongoDB index verification and creation for all collections...");
            MongoTemplate template = event.getApplicationContext().getBean(MongoTemplate.class);
            MongoMappingContext mappingContext = event.getApplicationContext().getBean(MongoMappingContext.class);
            IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

            List<Class<?>> entityClasses = List.of(
                    User.class,
                    Task.class,
                    ClientDocument.class,
                    Kyc.class,
                    Compliance.class,
                    ComplianceEvent.class,
                    Onboarding.class,
                    Requirement.class,
                    Notification.class,
                    Message.class,
                    MigrationJob.class
            );

            for (Class<?> entityClass : entityClasses) {
                IndexOperations indexOps = template.indexOps(entityClass);
                resolver.resolveIndexFor(entityClass).forEach(indexDefinition -> {
                    try {
                        indexOps.ensureIndex(indexDefinition);
                        log.info("Ensured index for {}: {}", entityClass.getSimpleName(), indexDefinition);
                    } catch (Exception ex) {
                        log.warn("Could not ensure index on {}: {}", entityClass.getSimpleName(), ex.getMessage());
                    }
                });
            }
            log.info("MongoDB index verification completed successfully.");
        } catch (Exception e) {
            log.warn("Error during automatic index creation: {}", e.getMessage());
        }
    }
}
