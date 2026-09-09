package com.aifishing.lake.ingestion.storage;

import com.aifishing.storage.ObjectStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3RawDataStorageTest {

    @Test
    void putAndGetUseObjectStoreKeys() {
        ObjectStore store = mock(ObjectStore.class);
        S3RawDataStorage storage = new S3RawDataStorage(store, "bucket");
        RawObjectKey key = RawObjectKey.page("lake", "BATHYMETRY", "v1", 1, "json");
        byte[] body = "{\"ok\":true}".getBytes();
        when(store.get(key.path())).thenReturn(Optional.of(body));

        StoredRawObject stored = storage.put(key, body, "application/json", Map.of());
        assertThat(stored.storageUri()).isEqualTo("s3://bucket/" + key.path());
        verify(store).put(key.path(), body, "application/json");
        assertThat(storage.get(key)).contains(body);
    }
}
