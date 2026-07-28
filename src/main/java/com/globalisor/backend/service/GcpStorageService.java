package com.globalisor.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GcpStorageService {

    @Value("${gcp.storage.bucket-name:globalisor-app-production}")
    private String bucketName;

    @Value("${gcp.storage.credentials-file:gcp-key.json}")
    private String credentialsFile;

    @Value("${gcp.storage.location:asia-southeast1}")
    private String location;

    @Value("${gcp.storage.credentials-json:}")
    private String credentialsJson;

    private Storage storage;
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials = null;

            // 1. Try loading from file first (gcp-key.json)
            File keyFile = new File(credentialsFile);
            if (keyFile.exists()) {
                try {
                    log.info("Loading GCP Service Account credentials from file: {}", keyFile.getAbsolutePath());
                    try (InputStream is = new FileInputStream(keyFile)) {
                        credentials = GoogleCredentials.fromStream(is);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load credentials from file '{}': {}", credentialsFile, e.getMessage());
                }
            }

            // 2. Try loading from GCP_CREDENTIALS_JSON property or env var
            if (credentials == null) {
                String envJson = System.getenv("GCP_CREDENTIALS_JSON");
                if (envJson == null || envJson.trim().isEmpty()) {
                    envJson = credentialsJson;
                }

                if (envJson != null && !envJson.trim().isEmpty()) {
                    try {
                        log.info("Loading GCP Service Account credentials from environment JSON string...");
                        try (InputStream is = new ByteArrayInputStream(envJson.getBytes(StandardCharsets.UTF_8))) {
                            credentials = GoogleCredentials.fromStream(is);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse GCP_CREDENTIALS_JSON: {}", e.getMessage());
                    }
                }
            }

            // 3. Try loading from GCP_CREDENTIALS_BASE64
            if (credentials == null) {
                String envBase64 = System.getenv("GCP_CREDENTIALS_BASE64");
                if (envBase64 != null && !envBase64.trim().isEmpty()) {
                    try {
                        log.info("Loading GCP Service Account credentials from Base64 environment string...");
                        byte[] decoded = Base64.getDecoder().decode(envBase64.trim());
                        try (InputStream is = new ByteArrayInputStream(decoded)) {
                            credentials = GoogleCredentials.fromStream(is);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse GCP_CREDENTIALS_BASE64: {}", e.getMessage());
                    }
                }
            }

            // 4. Try Application Default Credentials
            if (credentials == null) {
                try {
                    log.info("Attempting to load GCP Application Default Credentials...");
                    credentials = GoogleCredentials.getApplicationDefault();
                } catch (Exception e) {
                    log.warn("GCP Credentials file '{}' not found and ADC unavailable: {}", credentialsFile, e.getMessage());
                }
            }

            if (credentials != null) {
                storage = StorageOptions.newBuilder()
                        .setCredentials(credentials)
                        .build()
                        .getService();
                
                ensureBucketExists();
                initialized = true;
                log.info("GcpStorageService initialized successfully for bucket: {}", bucketName);
            } else {
                log.error("GcpStorageService: No valid GCP Credentials found!");
            }
        } catch (Exception e) {
            log.error("Failed to initialize GcpStorageService: {}", e.getMessage(), e);
        }
    }

    private void ensureBucketExists() {
        if (storage == null) return;
        try {
            if (storage.get(bucketName) == null) {
                log.info("Bucket '{}' not found. Creating automatically in location '{}'...", bucketName, location);
                storage.create(BucketInfo.newBuilder(bucketName)
                        .setLocation(location)
                        .build());
                log.info("GCP Bucket '{}' created successfully!", bucketName);
            } else {
                log.info("GCP Bucket '{}' exists and ready.", bucketName);
            }
        } catch (Exception e) {
            log.warn("Could not check/create GCP bucket '{}': {}. Ensure Service Account has 'Storage Admin' role.", bucketName, e.getMessage());
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public boolean isInitialized() {
        return initialized && storage != null;
    }

    public String uploadFile(String blobName, byte[] content, String contentType) {
        if (!isInitialized()) {
            log.error("GCP Storage not initialized when uploading blob: {}", blobName);
            throw new RuntimeException("GCP Storage service is not initialized. Check GCP credentials.");
        }

        try {
            BlobId blobId = BlobId.of(bucketName, blobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType != null ? contentType : "application/pdf")
                    .build();

            storage.create(blobInfo, content);
            log.info("Successfully uploaded blob to GCS: {}/{}", bucketName, blobName);
            return blobName;
        } catch (Exception e) {
            log.error("Error uploading blob to GCS: {}", e.getMessage(), e);
            throw new RuntimeException("GCP Storage upload failed: " + e.getMessage(), e);
        }
    }

    public String generateSignedUrl(String blobName, long minutes) {
        if (!isInitialized()) {
            return null;
        }
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobName)).build();
            URL signedUrl = storage.signUrl(blobInfo, minutes, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("Error generating signed URL for blob {}: {}", blobName, e.getMessage());
            return null;
        }
    }

    public byte[] downloadFile(String blobName) {
        if (!isInitialized()) {
            throw new IllegalStateException("GCP Storage service is not configured with valid credentials.");
        }
        try {
            Blob blob = storage.get(BlobId.of(bucketName, blobName));
            if (blob == null || !blob.exists()) {
                throw new IllegalArgumentException("File not found in GCP Bucket: " + blobName);
            }
            return blob.getContent();
        } catch (Exception e) {
            log.error("Error downloading blob {}: {}", blobName, e.getMessage(), e);
            throw new RuntimeException("GCP Download failed: " + e.getMessage(), e);
        }
    }

    public boolean deleteFile(String blobName) {
        if (!isInitialized() || blobName == null || blobName.isEmpty()) {
            return false;
        }
        try {
            boolean deleted = storage.delete(BlobId.of(bucketName, blobName));
            if (deleted) {
                log.info("Successfully deleted blob from GCS: {}/{}", bucketName, blobName);
            } else {
                log.warn("Blob not found in GCS for deletion: {}/{}", bucketName, blobName);
            }
            return deleted;
        } catch (Exception e) {
            log.error("Error deleting blob from GCS: {}", e.getMessage(), e);
            return false;
        }
    }
}
