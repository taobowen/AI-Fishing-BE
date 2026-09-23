package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.persistence.UserActionEventEntity;
import com.aifishing.guidance.persistence.UserActionEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UserActionObserver {

    private final UserActionEventRepository actionRepository;

    public UserActionObserver(UserActionEventRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    public List<ObservedUserAction> observe(
            UUID fishingSessionId,
            DeliveredSnapshot snapshot,
            SessionEventType type,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        if (fishingSessionId == null || snapshot == null) {
            return List.of();
        }
        List<ObservedUserAction> inferred = UserActionInference.infer(snapshot, type, occurredAt, withSource(payload));
        if (inferred.isEmpty()) {
            return List.of();
        }
        List<UserActionEventEntity> existing = actionRepository.findByFishingSessionIdOrderByOccurredAtAsc(fishingSessionId);
        List<ObservedUserAction> written = new ArrayList<>();
        for (ObservedUserAction action : inferred) {
            if (alreadyObserved(existing, action)) {
                continue;
            }
            UserActionEventEntity entity = new UserActionEventEntity();
            entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            entity.setFishingSessionId(fishingSessionId);
            entity.setDeliveredDecisionId(action.deliveredDecisionId());
            entity.setOccurredAt(action.occurredAt());
            entity.setFollowedPrimary(action.followedPrimary());
            entity.setFollowedRecommendation(action.followedRecommendation());
            entity.setRecommendationRole(action.recommendationRole());
            entity.setActualAction(action.actualAction());
            entity.setPayload(new HashMap<>(action.payload() == null ? Map.of() : action.payload()));
            actionRepository.save(entity);
            existing.add(entity);
            written.add(action);
        }
        return written;
    }

    public List<ObservedUserAction> load(UUID fishingSessionId) {
        List<ObservedUserAction> actions = new ArrayList<>();
        for (UserActionEventEntity row : actionRepository.findByFishingSessionIdOrderByOccurredAtAsc(fishingSessionId)) {
            actions.add(new ObservedUserAction(
                    row.getDeliveredDecisionId(),
                    row.getOccurredAt(),
                    row.getActualAction(),
                    Boolean.TRUE.equals(row.getFollowedRecommendation()),
                    row.getRecommendationRole(),
                    row.isFollowedPrimary(),
                    row.getPayload() == null ? Map.of() : row.getPayload()
            ));
        }
        return actions;
    }

    private static boolean alreadyObserved(List<UserActionEventEntity> existing, ObservedUserAction candidate) {
        for (UserActionEventEntity row : existing) {
            ObservedUserAction current = new ObservedUserAction(
                    row.getDeliveredDecisionId(),
                    row.getOccurredAt(),
                    row.getActualAction(),
                    Boolean.TRUE.equals(row.getFollowedRecommendation()),
                    row.getRecommendationRole(),
                    row.isFollowedPrimary(),
                    row.getPayload()
            );
            if (UserActionInference.sameObservation(current, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> withSource(Map<String, Object> payload) {
        return payload == null ? new HashMap<>() : new HashMap<>(payload);
    }

    public static GuidanceAction actualAction(ObservedUserAction action) {
        return action == null ? null : action.actualAction();
    }
}
