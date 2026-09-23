package com.aifishing.guidance.horizon;

import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveGuidanceTargetTest {

    private static final UUID SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VERSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SPOT_3 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003");

    @Mock
    private GuidancePlanVersionRepository versionRepository;
    @Mock
    private GuidancePlanStepRepository stepRepository;

    private ActiveGuidanceTarget resolver;

    @BeforeEach
    void setUp() {
        resolver = new ActiveGuidanceTarget(versionRepository, stepRepository);
    }

    @Test
    void committedMoveReturnsTripWaypointId() {
        stubVersion(committed(GuidanceAction.MOVE, SPOT_3));

        assertThat(resolver.resolve(SESSION)).isEqualTo(SPOT_3);
        assertThat(ActiveGuidanceTarget.fromHorizon(List.of(
                new HorizonStep(1, GuidanceAction.MOVE, SPOT_3, null, true),
                new HorizonStep(2, GuidanceAction.STAY, null, 20, false)
        ))).isEqualTo(SPOT_3);
    }

    @Test
    void stayOrMissingOrDegradedReturnsNull() {
        stubVersion(committed(GuidanceAction.STAY, SPOT_3));
        assertThat(resolver.resolve(SESSION)).isNull();

        when(versionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION))
                .thenReturn(Optional.empty());
        assertThat(resolver.resolve(SESSION)).isNull();

        assertThat(ActiveGuidanceTarget.fromHorizon(List.of(
                new HorizonStep(1, GuidanceAction.MOVE, SPOT_3, null, true),
                new HorizonStep(2, GuidanceAction.MOVE, SPOT_3, null, true)
        ))).isNull();
        assertThat(ActiveGuidanceTarget.fromHorizon(List.of(
                new HorizonStep(1, GuidanceAction.MOVE, null, null, true)
        ))).isNull();
        assertThat(ActiveGuidanceTarget.fromHorizon(List.of())).isNull();
        assertThat(resolver.resolve((UUID) null)).isNull();
    }

    private void stubVersion(GuidancePlanStepEntity step) {
        GuidancePlanVersionEntity version = new GuidancePlanVersionEntity();
        version.setId(VERSION);
        when(versionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION))
                .thenReturn(Optional.of(version));
        when(stepRepository.findByGuidancePlanVersionIdOrderByStepAsc(VERSION)).thenReturn(List.of(step));
    }

    private static GuidancePlanStepEntity committed(GuidanceAction type, UUID tripWaypointId) {
        GuidancePlanStepEntity step = new GuidancePlanStepEntity();
        step.setType(type);
        step.setCommitted(true);
        step.setTripWaypointId(tripWaypointId);
        return step;
    }
}
