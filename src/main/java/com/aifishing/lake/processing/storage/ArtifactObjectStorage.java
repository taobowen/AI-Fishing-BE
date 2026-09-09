package com.aifishing.lake.processing.storage;

import java.util.Optional;

public interface ArtifactObjectStorage {

    String putRelative(String relativePath, byte[] body, String contentType);

    Optional<byte[]> read(String storageUri);
}
