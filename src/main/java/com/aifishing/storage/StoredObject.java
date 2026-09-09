package com.aifishing.storage;

public record StoredObject(String key, long sizeBytes, String contentType) {
}
