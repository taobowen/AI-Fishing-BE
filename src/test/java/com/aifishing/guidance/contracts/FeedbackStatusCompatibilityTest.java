package com.aifishing.guidance.contracts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackStatusCompatibilityTest {

    @Test
    void historicalAcceptedAndRejectedFeedbackStillDeserialize() throws Exception {
        GuidanceFeedbackRequest accepted = GuidanceContracts.mapper().readValue(
                "{\"status\":\"ACCEPTED\"}",
                GuidanceFeedbackRequest.class
        );
        assertThat(accepted.status()).isEqualTo(FeedbackStatus.ACCEPTED);
        assertThat(accepted.rejectReason()).isNull();

        GuidanceFeedbackRequest rejected = GuidanceContracts.mapper().readValue(
                "{\"status\":\"REJECTED\",\"rejectReason\":\"TOO_FAR\"}",
                GuidanceFeedbackRequest.class
        );
        assertThat(rejected.status()).isEqualTo(FeedbackStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo(GuidanceRejectReason.TOO_FAR);

        GuidanceFeedbackRequest partial = GuidanceContracts.mapper().readValue(
                "{\"status\":\"PARTIALLY_FOLLOWED\"}",
                GuidanceFeedbackRequest.class
        );
        assertThat(partial.status()).isEqualTo(FeedbackStatus.PARTIALLY_FOLLOWED);

        GuidanceFeedbackRequest acknowledged = GuidanceContracts.mapper().readValue(
                "{\"status\":\"ACKNOWLEDGED\"}",
                GuidanceFeedbackRequest.class
        );
        assertThat(acknowledged.status()).isEqualTo(FeedbackStatus.ACKNOWLEDGED);
        assertThat(acknowledged.rejectReason()).isNull();
    }

    @Test
    void historicalAdviceAcceptedAndRejectedSessionEventsStillDeserialize() throws Exception {
        SessionEvent accepted = GuidanceContracts.mapper().readValue(
                """
                {"schemaVersion":"guidance.contracts.v1","type":"ADVICE_ACCEPTED","occurredAt":"2026-09-16T14:05:00Z","source":"CLIENT","idempotencyKey":"old-accept","payload":{"status":"ACCEPTED","onWaypoint":true}}
                """,
                SessionEvent.class
        );
        assertThat(accepted.type()).isEqualTo(SessionEventType.ADVICE_ACCEPTED);
        assertThat(accepted.payload()).containsEntry("status", "ACCEPTED");

        SessionEvent rejected = GuidanceContracts.mapper().readValue(
                """
                {"schemaVersion":"guidance.contracts.v1","type":"ADVICE_REJECTED","occurredAt":"2026-09-16T14:06:00Z","source":"CLIENT","idempotencyKey":"old-reject","payload":{"status":"REJECTED","rejectReason":"TOO_FAR"}}
                """,
                SessionEvent.class
        );
        assertThat(rejected.type()).isEqualTo(SessionEventType.ADVICE_REJECTED);
        assertThat(rejected.payload()).containsEntry("status", "REJECTED");
    }
}
