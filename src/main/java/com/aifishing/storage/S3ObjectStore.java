package com.aifishing.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.raw.storage", havingValue = "s3")
public class S3ObjectStore implements ObjectStore {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    @Autowired
    public S3ObjectStore(@Value("${app.s3.bucket}") String bucket) {
        this.bucket = bucket;
        this.s3 = S3Client.create();
        this.presigner = S3Presigner.create();
    }

    S3ObjectStore(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder().bucket(bucket).key(key);
        if (contentType != null && !contentType.isBlank()) {
            builder.contentType(contentType);
        }
        s3.putObject(builder.build(), RequestBody.fromBytes(body));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            String type = response.contentType() == null ? "application/octet-stream" : response.contentType();
            return Optional.of(new StoredObject(key, response.contentLength(), type));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public String presignPut(String key, String contentType, int ttlSeconds) {
        PutObjectRequest.Builder put = PutObjectRequest.builder().bucket(bucket).key(key);
        if (contentType != null && !contentType.isBlank()) {
            put.contentType(contentType);
        }
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .putObjectRequest(put.build())
                .build()).url().toString();
    }

    @Override
    public String presignGet(String key, int ttlSeconds) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build()).url().toString();
    }
}
