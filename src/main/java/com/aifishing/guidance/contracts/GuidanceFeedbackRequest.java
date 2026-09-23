package com.aifishing.guidance.contracts;

public record GuidanceFeedbackRequest(
        FeedbackStatus status,
        GuidanceRejectReason rejectReason,
        String note
) {
}
