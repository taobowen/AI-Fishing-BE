package com.aifishing.storage;

import java.util.Optional;

public interface ObjectStore {

    void put(String key, byte[] body, String contentType);

    Optional<StoredObject> head(String key);

    Optional<byte[]> get(String key);

    void delete(String key);

    String presignPut(String key, String contentType, int ttlSeconds);

    String presignGet(String key, int ttlSeconds);
}
