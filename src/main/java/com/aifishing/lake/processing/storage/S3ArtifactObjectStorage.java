package com.aifishing.lake.processing.storage;

import com.aifishing.storage.ObjectStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.raw.storage", havingValue = "s3")
public class S3ArtifactObjectStorage implements ArtifactObjectStorage {

    static final String PREFIX = "derived/";

    private final ObjectStore objectStore;
    private final String bucket;

    public S3ArtifactObjectStorage(ObjectStore objectStore, @Value("${app.s3.bucket}") String bucket) {
        this.objectStore = objectStore;
        this.bucket = bucket;
    }

    @Override
    public String putRelative(String relativePath, byte[] body, String contentType) {
        String key = PREFIX + relativePath;
        objectStore.put(key, body, contentType);
        return "s3://" + bucket + "/" + key;
    }

    @Override
    public Optional<byte[]> read(String storageUri) {
        return parseKey(storageUri).flatMap(objectStore::get);
    }

    Optional<String> parseKey(String storageUri) {
        if (storageUri == null || !storageUri.startsWith("s3://")) {
            return Optional.empty();
        }
        String path = storageUri.substring("s3://".length());
        int slash = path.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        return Optional.of(path.substring(slash + 1));
    }
}
