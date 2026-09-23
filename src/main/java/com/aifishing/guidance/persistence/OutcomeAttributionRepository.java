package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutcomeAttributionRepository extends JpaRepository<OutcomeAttributionEntity, UUID> {

    List<OutcomeAttributionEntity> findByFishingSessionIdOrderByAttributedAtAsc(UUID fishingSessionId);

    List<OutcomeAttributionEntity> findByDeliveredDecisionIdOrderByAttributedAtAsc(UUID deliveredDecisionId);

    List<OutcomeAttributionEntity> findByAttributedAtGreaterThanEqualAndAttributedAtLessThanOrderByAttributedAtAsc(
            Instant windowStart,
            Instant windowEnd
    );

    Optional<OutcomeAttributionEntity> findByDeliveredDecisionIdAndAttributionDimensionAndRecommendationRoleAndFishInteractionId(
            UUID deliveredDecisionId,
            AttributionDimension attributionDimension,
            RecommendationRole recommendationRole,
            UUID fishInteractionId
    );

    Optional<OutcomeAttributionEntity> findByDeliveredDecisionIdAndAttributionDimensionAndRecommendationRoleAndOutcomeKindAndFishInteractionIdIsNull(
            UUID deliveredDecisionId,
            AttributionDimension attributionDimension,
            RecommendationRole recommendationRole,
            OutcomeKind outcomeKind
    );
}
