package com.aifishing.lake.ingestion.storage;

import com.aifishing.storage.ObjectStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.raw.storage", havingValue = "s3")
public class S3RawDataStorage implements RawDataStorage {

    private final ObjectStore objectStore;
    private final String bucket;

    public S3RawDataStorage(ObjectStore objectStore, @Value("${app.s3.bucket}") String bucket) {
        this.objectStore = objectStore;
        this.bucket = bucket;
    }

    @Override
    public StoredRawObject put(RawObjectKey key, byte[] body, String contentType, Map<String, Object> requestMetadata) {
        objectStore.put(key.path(), body, contentType);
        return new StoredRawObject("s3://" + bucket + "/" + key.path(), sha256(body));
    }

    @Override
    public Optional<byte[]> get(RawObjectKey key) {
        return objectStore.get(key.path());
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
