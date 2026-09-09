package com.aifishing.feedback.catchlog.repo;

import com.aifishing.feedback.catchlog.domain.CatchPhoto;
import com.aifishing.feedback.catchlog.domain.CatchPhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatchPhotoRepository extends JpaRepository<CatchPhoto, UUID> {

    List<CatchPhoto> findByCatchEventIdAndStatusOrderByCreatedAtAsc(UUID catchEventId, CatchPhotoStatus status);

    Optional<CatchPhoto> findByIdAndCatchEventId(UUID id, UUID catchEventId);
}
