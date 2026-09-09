package com.aifishing.lake.processing.storage;

import com.aifishing.lake.processing.ProcessingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.raw.storage", havingValue = "local", matchIfMissing = true)
public class LocalArtifactObjectStorage implements ArtifactObjectStorage {

    private final Path root;

    public LocalArtifactObjectStorage(ProcessingProperties properties) {
        this.root = Path.of(properties.getDerivedDir());
    }

    @Override
    public String putRelative(String relativePath, byte[] body, String contentType) {
        try {
            Path file = root.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.write(file, body);
            return file.toAbsolutePath().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist derived artifact " + relativePath, ex);
        }
    }

    @Override
    public Optional<byte[]> read(String storageUri) {
        if (storageUri == null || storageUri.isBlank() || storageUri.startsWith("s3://")) {
            return Optional.empty();
        }
        Path path = Path.of(storageUri);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read derived artifact", ex);
        }
    }
}
