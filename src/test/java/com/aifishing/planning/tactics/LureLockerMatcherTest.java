package com.aifishing.planning.tactics;

import com.aifishing.common.enums.LureColorFamily;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.LureLengthBand;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.gear.lure.LureProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LureLockerMatcherTest {

    private static final UUID VISIT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
    private static final UUID PADDLETAIL_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001");
    private static final UUID FROG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000002");
    private static final UUID ALT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000003");

    @Test
    void whitePearlIsSufficientForIdealFamilyAndSize() {
        StopTacticalProfile profile = dropShot(List.of());
        profile = new StopTacticalProfile(VISIT, paddletailIdeal(), List.of());
        TacticalRecommendation rec = LureLockerMatcher.match(profile, List.of(lure(
                PADDLETAIL_ID,
                LureFamily.PADDLETAIL,
                LureLengthBand.THREE_TO_4_IN,
                List.of(LureColorFamily.WHITE_PEARL)
        )));
        assertThat(rec.bestLockerLureId()).isEqualTo(PADDLETAIL_ID);
        assertThat(rec.idealAlreadyOwned()).isTrue();
        assertThat(rec.showIdealOption()).isFalse();
    }

    @Test
    void frogIsNeverPickedForDropShot() {
        StopTacticalProfile profile = dropShot(List.of(nedAlternative()));
        TacticalRecommendation rec = LureLockerMatcher.match(profile, List.of(lure(
                FROG_ID,
                LureFamily.FROG,
                LureLengthBand.THREE_TO_4_IN,
                List.of(LureColorFamily.BLACK)
        )));
        assertThat(rec.bestLockerLureId()).isNull();
        assertThat(rec.lockerSnapshot()).isNull();
        assertThat(rec.showIdealOption()).isTrue();
        assertThat(rec.idealAlreadyOwned()).isFalse();
    }

    @Test
    void lockerTechniqueComesFromSelectedAlternative() {
        StopTacticalProfile profile = dropShot(List.of(nedAlternative()));
        TacticalRecommendation rec = LureLockerMatcher.match(profile, List.of(lure(
                ALT_ID,
                LureFamily.NED_RIG,
                LureLengthBand.UNDER_3_IN,
                List.of(LureColorFamily.GREEN_PUMPKIN)
        )));
        assertThat(rec.bestLockerLureId()).isEqualTo(ALT_ID);
        assertThat(rec.lockerTechnique().presentation()).isEqualTo(PresentationTechnique.DRAG_AND_SHAKE);
        assertThat(rec.lockerTechnique().instructions()).contains("Drag");
        assertThat(rec.showIdealOption()).isTrue();
        assertThat(rec.idealAlreadyOwned()).isFalse();
    }

    @Test
    void neverInventForeignIds() {
        UUID foreign = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        StopTacticalProfile profile = dropShot(List.of());
        TacticalRecommendation rec = LureLockerMatcher.match(profile, List.of());
        assertThat(rec.bestLockerLureId()).isNull();
        assertThat(rec.bestLockerLureId()).isNotEqualTo(foreign);
    }

    @Test
    void sizeDimensionMustMatchForSufficient() {
        StopTacticalProfile profile = new StopTacticalProfile(VISIT, paddletailIdeal(), List.of());
        TacticalRecommendation rec = LureLockerMatcher.match(profile, List.of(lure(
                PADDLETAIL_ID,
                LureFamily.PADDLETAIL,
                LureLengthBand.OVER_5_IN,
                List.of(LureColorFamily.NATURAL)
        )));
        assertThat(rec.bestLockerLureId()).isEqualTo(PADDLETAIL_ID);
        assertThat(rec.idealAlreadyOwned()).isFalse();
        assertThat(rec.showIdealOption()).isTrue();
    }

    @Test
    void emptyLockerLeavesIdealVisible() {
        TacticalRecommendation rec = LureLockerMatcher.match(dropShot(List.of(nedAlternative())), List.of());
        assertThat(rec.bestLockerLureId()).isNull();
        assertThat(rec.showIdealOption()).isTrue();
        assertThat(rec.idealTactic().lureFamily()).isEqualTo(LureFamily.DROP_SHOT_BAIT);
    }

    private static StopTacticalProfile dropShot(List<TacticProfile> alternatives) {
        TacticProfile ideal = new TacticProfile(
                LureFamily.DROP_SHOT_BAIT,
                LureLengthBand.THREE_TO_4_IN,
                null,
                null,
                null,
                List.of(LureColorFamily.NATURAL),
                null,
                PresentationTechnique.LIFT_DROP,
                "Lift the bait and let it settle.",
                "Drop-shot on this break.",
                "slow",
                "4.0m",
                null
        );
        return new StopTacticalProfile(VISIT, ideal, alternatives);
    }

    private static TacticProfile paddletailIdeal() {
        return new TacticProfile(
                LureFamily.PADDLETAIL,
                LureLengthBand.THREE_TO_4_IN,
                null,
                null,
                null,
                List.of(LureColorFamily.NATURAL),
                null,
                PresentationTechnique.SWIM_NEAR_BOTTOM,
                "Swim it just above the cover.",
                "Paddletail match.",
                "moderate",
                "3.0m",
                null
        );
    }

    private static TacticProfile nedAlternative() {
        return new TacticProfile(
                LureFamily.NED_RIG,
                LureLengthBand.UNDER_3_IN,
                null,
                null,
                null,
                List.of(LureColorFamily.GREEN_PUMPKIN),
                null,
                PresentationTechnique.DRAG_AND_SHAKE,
                "Drag it a few inches, pause, and shake in place.",
                "Ned is a valid fallback.",
                "slow",
                "4.0m",
                null
        );
    }

    private static LockerLure lure(
            UUID id,
            LureFamily family,
            LureLengthBand length,
            List<LureColorFamily> colors
    ) {
        return new LockerLure(id, new LureProfile(family, length, null, null, null, colors, null), family.displayName());
    }
}
