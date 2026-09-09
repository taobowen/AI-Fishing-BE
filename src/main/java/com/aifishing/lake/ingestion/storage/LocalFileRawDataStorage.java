package com.aifishing.lake.ingestion.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.raw.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileRawDataStorage implements RawDataStorage {

    private final Path root;

    public LocalFileRawDataStorage(@Value("${app.raw.local-dir}") String localDir) {
        this.root = Path.of(localDir);
    }

    @Override
    public StoredRawObject put(RawObjectKey key, byte[] body, String contentType, Map<String, Object> requestMetadata) {
        try {
            Path file = root.resolve(key.path());
            Files.createDirectories(file.getParent());
            Files.write(file, body);
            return new StoredRawObject(file.toAbsolutePath().toString(), sha256(body));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store raw object " + key.path(), ex);
        }
    }

    @Override
    public Optional<byte[]> get(RawObjectKey key) {
        Path file = root.resolve(key.path());
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read raw object " + key.path(), ex);
        }
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
