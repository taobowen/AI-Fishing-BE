package com.aifishing.lake.ingestion.storage;

import java.util.Map;
import java.util.Optional;

public interface RawDataStorage {

    StoredRawObject put(RawObjectKey key, byte[] body, String contentType, Map<String, Object> requestMetadata);

    Optional<byte[]> get(RawObjectKey key);
}
