package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerPriorityTest {

    @Test
    void safetyBeatsNoBiteAndKeepsBothReasons() {
        GuidanceTriggerOutboxEntity open = new GuidanceTriggerOutboxEntity();
        open.setPrimaryTrigger(GuidanceTrigger.SAFETY_STATE_CHANGED);
        open.setRelatedTriggers(List.of());
        open.setReasonCodes(List.of("SAFETY_THUNDERSTORM"));
        open.setSource(GuidanceTriggerOutboxSource.EVENT);

        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.NO_BITE_THRESHOLD,
                        List.of(),
                        List.of("NO_BITE_THRESHOLD_CROSSED")
                ),
                GuidanceTriggerOutboxSource.HEARTBEAT
        );

        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.SAFETY_STATE_CHANGED);
        assertThat(open.getRelatedTriggers()).containsExactly(GuidanceTrigger.NO_BITE_THRESHOLD);
        assertThat(open.getReasonCodes()).containsExactly("SAFETY_THUNDERSTORM", "NO_BITE_THRESHOLD_CROSSED");
        assertThat(open.getSource()).isEqualTo(GuidanceTriggerOutboxSource.EVENT);
    }

    @Test
    void higherIncomingPrimaryWinsAndDemotesExisting() {
        GuidanceTriggerOutboxEntity open = new GuidanceTriggerOutboxEntity();
        open.setPrimaryTrigger(GuidanceTrigger.NO_BITE_THRESHOLD);
        open.setRelatedTriggers(List.of(GuidanceTrigger.REPEATED_BITE_PATTERN));
        open.setReasonCodes(List.of("NO_BITE_THRESHOLD_CROSSED"));
        open.setSource(GuidanceTriggerOutboxSource.HEARTBEAT);

        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.FISH_ON,
                        List.of(),
                        List.of("FISH_ON")
                ),
                GuidanceTriggerOutboxSource.EVENT
        );

        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.FISH_ON);
        assertThat(open.getRelatedTriggers()).containsExactlyInAnyOrder(
                GuidanceTrigger.NO_BITE_THRESHOLD,
                GuidanceTrigger.REPEATED_BITE_PATTERN
        );
        assertThat(open.getSource()).isEqualTo(GuidanceTriggerOutboxSource.EVENT);
    }

    @Test
    void adHocStartOutranksLocationGroupAndEndSharesLocationRank() {
        assertThat(TriggerPriority.rank(GuidanceTrigger.SAFETY_STATE_CHANGED)).isEqualTo(1);
        assertThat(TriggerPriority.rank(GuidanceTrigger.RETURN_RISK_CHANGED)).isEqualTo(1);
        assertThat(TriggerPriority.rank(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING)).isEqualTo(2);
        assertThat(TriggerPriority.rank(GuidanceTrigger.ROUTE_DEVIATION)).isEqualTo(3);
        assertThat(TriggerPriority.rank(GuidanceTrigger.SIGNIFICANT_LOCATION_CHANGE)).isEqualTo(3);
        assertThat(TriggerPriority.rank(GuidanceTrigger.SIGNIFICANT_WEATHER_CHANGE)).isEqualTo(3);
        assertThat(TriggerPriority.rank(GuidanceTrigger.WAYPOINT_REACHED)).isEqualTo(3);
        assertThat(TriggerPriority.rank(GuidanceTrigger.USER_ENDED_AD_HOC_FISHING)).isEqualTo(3);
        assertThat(TriggerPriority.rank(GuidanceTrigger.FISH_ON)).isEqualTo(4);
        assertThat(TriggerPriority.rank(GuidanceTrigger.REPEATED_BITE_PATTERN)).isEqualTo(5);
        assertThat(TriggerPriority.rank(GuidanceTrigger.PLAN_STEP_COMPLETED)).isEqualTo(5);
        assertThat(TriggerPriority.rank(GuidanceTrigger.CONSECUTIVE_FAILURE)).isEqualTo(5);
        assertThat(TriggerPriority.rank(GuidanceTrigger.NO_BITE_THRESHOLD)).isEqualTo(6);
        assertThat(TriggerPriority.rank(GuidanceTrigger.USER_REQUEST)).isEqualTo(99);
        assertThat(TriggerPriority.higher(
                GuidanceTrigger.USER_STARTED_AD_HOC_FISHING,
                GuidanceTrigger.ROUTE_DEVIATION
        )).isEqualTo(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING);
        assertThat(TriggerPriority.higher(
                GuidanceTrigger.SAFETY_STATE_CHANGED,
                GuidanceTrigger.USER_STARTED_AD_HOC_FISHING
        )).isEqualTo(GuidanceTrigger.SAFETY_STATE_CHANGED);
        assertThat(TriggerPriority.higher(
                GuidanceTrigger.USER_STARTED_AD_HOC_FISHING,
                GuidanceTrigger.USER_ENDED_AD_HOC_FISHING
        )).isEqualTo(GuidanceTrigger.USER_ENDED_AD_HOC_FISHING);
        assertThat(TriggerPriority.higher(
                GuidanceTrigger.USER_ENDED_AD_HOC_FISHING,
                GuidanceTrigger.USER_STARTED_AD_HOC_FISHING
        )).isEqualTo(GuidanceTrigger.USER_ENDED_AD_HOC_FISHING);
    }

    @Test
    void adHocStartCoalescesOverLocationDeviationAndKeepsRelatedReasons() {
        GuidanceTriggerOutboxEntity open = new GuidanceTriggerOutboxEntity();
        open.setPrimaryTrigger(GuidanceTrigger.SIGNIFICANT_LOCATION_CHANGE);
        open.setRelatedTriggers(List.of(GuidanceTrigger.ROUTE_DEVIATION));
        open.setReasonCodes(List.of("SIGNIFICANT_LOCATION_CHANGE", "ROUTE_DEVIATION"));
        open.setSource(GuidanceTriggerOutboxSource.HEARTBEAT);

        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.USER_STARTED_AD_HOC_FISHING,
                        List.of(),
                        List.of("USER_STARTED_AD_HOC_FISHING")
                ),
                GuidanceTriggerOutboxSource.EVENT
        );

        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING);
        assertThat(open.getRelatedTriggers()).containsExactlyInAnyOrder(
                GuidanceTrigger.SIGNIFICANT_LOCATION_CHANGE,
                GuidanceTrigger.ROUTE_DEVIATION
        );
        assertThat(open.getReasonCodes()).contains(
                "USER_STARTED_AD_HOC_FISHING",
                "SIGNIFICANT_LOCATION_CHANGE",
                "ROUTE_DEVIATION"
        );
        assertThat(open.getSource()).isEqualTo(GuidanceTriggerOutboxSource.EVENT);
    }

    @Test
    void safetyStillOutranksIncomingAdHocStart() {
        GuidanceTriggerOutboxEntity open = new GuidanceTriggerOutboxEntity();
        open.setPrimaryTrigger(GuidanceTrigger.SAFETY_STATE_CHANGED);
        open.setRelatedTriggers(List.of());
        open.setReasonCodes(List.of("SAFETY_THUNDERSTORM"));
        open.setSource(GuidanceTriggerOutboxSource.EVENT);

        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.USER_STARTED_AD_HOC_FISHING,
                        List.of(),
                        List.of("USER_STARTED_AD_HOC_FISHING")
                ),
                GuidanceTriggerOutboxSource.EVENT
        );

        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.SAFETY_STATE_CHANGED);
        assertThat(open.getRelatedTriggers()).containsExactly(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING);
        assertThat(open.getReasonCodes()).contains("SAFETY_THUNDERSTORM", "USER_STARTED_AD_HOC_FISHING");
    }

    @Test
    void openAdHocStartThenIncomingEndPromotesEndAndKeepsStartRelated() {
        GuidanceTriggerOutboxEntity open = new GuidanceTriggerOutboxEntity();
        open.setPrimaryTrigger(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING);
        open.setRelatedTriggers(List.of());
        open.setReasonCodes(List.of("USER_STARTED_AD_HOC_FISHING"));
        open.setSource(GuidanceTriggerOutboxSource.EVENT);

        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.USER_ENDED_AD_HOC_FISHING,
                        List.of(),
                        List.of("USER_ENDED_AD_HOC_FISHING")
                ),
                GuidanceTriggerOutboxSource.EVENT
        );

        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.USER_ENDED_AD_HOC_FISHING);
        assertThat(open.getRelatedTriggers()).containsExactly(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING);
        assertThat(open.getReasonCodes()).contains("USER_STARTED_AD_HOC_FISHING", "USER_ENDED_AD_HOC_FISHING");
    }

    @Test
    void safetyStillOutranksIncomingAdHocEnd() {
        GuidanceTriggerOutboxEntity open = new GuidanceTriggerOutboxEntity();
        open.setPrimaryTrigger(GuidanceTrigger.SAFETY_STATE_CHANGED);
        open.setRelatedTriggers(List.of());
        open.setReasonCodes(List.of("SAFETY_THUNDERSTORM"));
        open.setSource(GuidanceTriggerOutboxSource.EVENT);

        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.USER_ENDED_AD_HOC_FISHING,
                        List.of(),
                        List.of("USER_ENDED_AD_HOC_FISHING")
                ),
                GuidanceTriggerOutboxSource.EVENT
        );

        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.SAFETY_STATE_CHANGED);
        assertThat(open.getRelatedTriggers()).containsExactly(GuidanceTrigger.USER_ENDED_AD_HOC_FISHING);
    }

    @Test
    void duplicateLocationThenActivityStayOneMergedRow() {
        GuidanceTriggerOutboxEntity open = new GuidanceTriggerOutboxEntity();
        open.setPrimaryTrigger(GuidanceTrigger.SIGNIFICANT_LOCATION_CHANGE);
        open.setRelatedTriggers(List.of());
        open.setReasonCodes(List.of("SIGNIFICANT_LOCATION_CHANGE"));
        open.setSource(GuidanceTriggerOutboxSource.HEARTBEAT);

        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.SIGNIFICANT_LOCATION_CHANGE,
                        List.of(),
                        List.of("SIGNIFICANT_LOCATION_CHANGE")
                ),
                GuidanceTriggerOutboxSource.HEARTBEAT
        );
        GuidanceTriggerOutboxService.mergeInto(
                open,
                new TriggerRoutingDecision(
                        GuidanceTrigger.FISH_ON,
                        List.of(),
                        List.of("FISH_ON")
                ),
                GuidanceTriggerOutboxSource.EVENT
        );

        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.SIGNIFICANT_LOCATION_CHANGE);
        assertThat(open.getRelatedTriggers()).containsExactly(GuidanceTrigger.FISH_ON);
        assertThat(open.getReasonCodes()).containsExactly("SIGNIFICANT_LOCATION_CHANGE", "FISH_ON");
        assertThat(open.getSource()).isEqualTo(GuidanceTriggerOutboxSource.EVENT);
    }
}
