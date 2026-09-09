package com.aifishing.feedback.catchlog.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchPhoto;
import com.aifishing.feedback.catchlog.domain.CatchPhotoStatus;
import com.aifishing.feedback.catchlog.dto.CatchPhotoResponse;
import com.aifishing.feedback.catchlog.dto.PhotoUploadRequest;
import com.aifishing.feedback.catchlog.dto.PhotoUploadResponse;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.catchlog.repo.CatchPhotoRepository;
import com.aifishing.storage.ObjectStore;
import com.aifishing.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CatchPhotoService {

    static final String PREFIX = "catch-photos/";

    private final CurrentUser currentUser;
    private final CatchEventRepository catchEventRepository;
    private final CatchPhotoRepository photoRepository;
    private final ObjectStore objectStore;
    private final FeedbackProperties properties;

    public CatchPhotoService(
            CurrentUser currentUser,
            CatchEventRepository catchEventRepository,
            CatchPhotoRepository photoRepository,
            ObjectStore objectStore,
            FeedbackProperties properties
    ) {
        this.currentUser = currentUser;
        this.catchEventRepository = catchEventRepository;
        this.photoRepository = photoRepository;
        this.objectStore = objectStore;
        this.properties = properties;
    }

    @Transactional
    public PhotoUploadResponse startUpload(UUID catchId, PhotoUploadRequest request) {
        CatchEvent catchEvent = requireOwnedCatch(catchId);
        String contentType = normalizeType(request.contentType());
        requireAllowedType(contentType);
        UUID photoId = UUID.randomUUID();
        String key = PREFIX + currentUser.id() + "/" + catchEvent.getId() + "/" + photoId;
        CatchPhoto photo = new CatchPhoto();
        photo.setId(photoId);
        photo.setCatchEventId(catchEvent.getId());
        photo.setS3Key(key);
        photo.setStatus(CatchPhotoStatus.PENDING);
        photoRepository.save(photo);
        String putUrl = objectStore.presignPut(key, contentType, properties.getPhoto().getPresignTtlSeconds());
        return new PhotoUploadResponse(photoId, putUrl);
    }

    @Transactional
    public CatchPhotoResponse complete(UUID catchId, UUID photoId) {
        CatchEvent catchEvent = requireOwnedCatch(catchId);
        CatchPhoto photo = photoRepository.findByIdAndCatchEventId(photoId, catchEvent.getId())
                .orElseThrow(() -> new NotFoundException("Catch photo not found"));
        if (photo.getStatus() == CatchPhotoStatus.READY) {
            return toResponse(photo, true);
        }
        if (photo.getStatus() == CatchPhotoStatus.DELETED) {
            throw new BadRequestException("Photo is deleted");
        }
        String expectedPrefix = PREFIX + currentUser.id() + "/" + catchEvent.getId() + "/";
        if (photo.getS3Key() == null || !photo.getS3Key().startsWith(expectedPrefix)) {
            reject(photo, "Photo key does not match the server-owned prefix");
        }
        StoredObject stored = objectStore.head(photo.getS3Key())
                .orElseThrow(() -> new BadRequestException("Photo object is missing"));
        if (!photo.getS3Key().equals(stored.key())) {
            reject(photo, "Photo key does not match the stored object");
        }
        if (stored.sizeBytes() <= 0 || stored.sizeBytes() > properties.getPhoto().getMaxBytes()) {
            reject(photo, "Photo exceeds the maximum allowed size");
        }
        String type = normalizeType(stored.contentType());
        if (!allowedType(type)) {
            reject(photo, "Photo content type is not allowed");
        }
        photo.setContentType(type);
        photo.setSizeBytes(stored.sizeBytes());
        photo.setStatus(CatchPhotoStatus.READY);
        photo.setCompletedAt(Instant.now());
        return toResponse(photoRepository.save(photo), true);
    }

    @Transactional(readOnly = true)
    public List<CatchPhotoResponse> list(UUID catchId) {
        CatchEvent catchEvent = requireOwnedCatch(catchId);
        return photoRepository.findByCatchEventIdAndStatusOrderByCreatedAtAsc(catchEvent.getId(), CatchPhotoStatus.READY)
                .stream()
                .map(photo -> toResponse(photo, true))
                .toList();
    }

    @Transactional
    public void delete(UUID catchId, UUID photoId) {
        CatchEvent catchEvent = requireOwnedCatch(catchId);
        CatchPhoto photo = photoRepository.findByIdAndCatchEventId(photoId, catchEvent.getId())
                .orElseThrow(() -> new NotFoundException("Catch photo not found"));
        if (photo.getStatus() == CatchPhotoStatus.DELETED) {
            return;
        }
        objectStore.delete(photo.getS3Key());
        photo.setStatus(CatchPhotoStatus.DELETED);
        photoRepository.save(photo);
    }

    private void reject(CatchPhoto photo, String message) {
        objectStore.delete(photo.getS3Key());
        throw new BadRequestException(message);
    }

    private CatchPhotoResponse toResponse(CatchPhoto photo, boolean includeGetUrl) {
        String getUrl = null;
        if (includeGetUrl && photo.getStatus() == CatchPhotoStatus.READY) {
            getUrl = objectStore.presignGet(photo.getS3Key(), properties.getPhoto().getPresignTtlSeconds());
        }
        return new CatchPhotoResponse(
                photo.getId(),
                photo.getCatchEventId(),
                photo.getStatus(),
                photo.getContentType(),
                photo.getSizeBytes(),
                getUrl,
                photo.getCreatedAt(),
                photo.getCompletedAt()
        );
    }

    private CatchEvent requireOwnedCatch(UUID catchId) {
        return catchEventRepository.findByIdAndUserId(catchId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Catch not found"));
    }

    private void requireAllowedType(String contentType) {
        if (!allowedType(contentType)) {
            throw new BadRequestException("Photo content type is not allowed");
        }
    }

    private boolean allowedType(String contentType) {
        return properties.getPhoto().getAllowedContentTypes().stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(contentType));
    }

    private static String normalizeType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String type = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
