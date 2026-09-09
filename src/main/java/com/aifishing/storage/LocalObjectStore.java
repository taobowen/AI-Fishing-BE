package com.aifishing.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.raw.storage", havingValue = "local", matchIfMissing = true)
public class LocalObjectStore implements ObjectStore {

    private final Path root;

    public LocalObjectStore(@Value("${app.raw.local-dir}") String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().getParent().resolve("objects");
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
        try {
            Path file = file(key);
            Files.createDirectories(file.getParent());
            Files.write(file, body);
            if (contentType != null) {
                Files.writeString(Path.of(file.toString() + ".ctype"), contentType);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store " + key, ex);
        }
    }

    @Override
    public Optional<StoredObject> head(String key) {
        Path file = file(key);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String type = Files.exists(Path.of(file.toString() + ".ctype"))
                    ? Files.readString(Path.of(file.toString() + ".ctype")).trim()
                    : "application/octet-stream";
            return Optional.of(new StoredObject(key, Files.size(file), type));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to head " + key, ex);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path file = file(key);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(file(key));
            Files.deleteIfExists(Path.of(file(key).toString() + ".ctype"));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete " + key, ex);
        }
    }

    @Override
    public String presignPut(String key, String contentType, int ttlSeconds) {
        return "local://" + file(key);
    }

    @Override
    public String presignGet(String key, int ttlSeconds) {
        return "local://" + file(key);
    }

    public Path file(String key) {
        return root.resolve(key);
    }
}
