package com.aifishing.webquota.repo;

import com.aifishing.webquota.domain.WebPlanGenerationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebPlanGenerationRequestRepository extends JpaRepository<WebPlanGenerationRequest, UUID> {

    Optional<WebPlanGenerationRequest> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
