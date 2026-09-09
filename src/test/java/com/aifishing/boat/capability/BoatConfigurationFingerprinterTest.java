package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BoatConfigurationFingerprinterTest {

    @Test
    void ignoresRawProseButIncludesSanitizedEquipmentFacts() {
        Boat a = dual();
        a.setConfigurationDescription("Please remember my nickname Bob and 12V 100Ah LiFePO4 house battery");
        Boat b = dual();
        b.setConfigurationDescription("totally different story with 12V 100Ah LiFePO4");
        Boat c = dual();
        c.setConfigurationDescription("no battery facts here, just a story");

        String fa = BoatConfigurationFingerprinter.fingerprint(a).hex();
        String fb = BoatConfigurationFingerprinter.fingerprint(b).hex();
        String fc = BoatConfigurationFingerprinter.fingerprint(c).hex();

        assertThat(fa).isEqualTo(fb);
        assertThat(fa).isNotEqualTo(fc);
        Map<String, Object> normalized = BoatConfigurationFingerprinter.fingerprint(a).normalizedConfiguration();
        assertThat(normalized).doesNotContainKey("configurationDescription");
        assertThat(normalized.toString()).doesNotContain("Bob");
        assertThat(normalized.get("sanitizedEquipmentFacts")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) normalized.get("sanitizedEquipmentFacts");
        assertThat(facts).containsEntry("batteryVoltageV", 12.0);
        assertThat(facts).containsEntry("batteryCapacityAh", 100.0);
        assertThat(facts).containsEntry("batteryChemistry", "LiFePO4");
    }

    @Test
    void priorsChangeFingerprintSoRevisionDoesNotReusePriorLessCache() {
        Boat boat = dual();
        boat.setConfigurationDescription("12ft jon 6hp");
        String withoutPriors = BoatConfigurationFingerprinter.fingerprint(boat).hex();
        String withPriors = BoatConfigurationFingerprinter.fingerprint(
                boat,
                new BoatCapabilityPriors(12.0, 18.0, WindWaveCapability.LOW)
        ).hex();
        assertThat(withPriors).isNotEqualTo(withoutPriors);
    }

    @Test
    void motorOrPrimaryTransitChangeCreatesNewFingerprint() {
        Boat dual = dual();
        Boat gasOnly = dual();
        gasOnly.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD));
        gasOnly.setMotors(List.of(new BoatMotor(PropulsionType.GAS_OUTBOARD, null, null, BigDecimal.valueOf(9.9), null)));
        Boat primaryTrolling = dual();
        primaryTrolling.setPrimaryTransitPropulsionType(PropulsionType.ELECTRIC_TROLLING);

        assertThat(BoatConfigurationFingerprinter.fingerprint(dual).hex())
                .isNotEqualTo(BoatConfigurationFingerprinter.fingerprint(gasOnly).hex());
        assertThat(BoatConfigurationFingerprinter.fingerprint(dual).hex())
                .isNotEqualTo(BoatConfigurationFingerprinter.fingerprint(primaryTrolling).hex());
    }

    private static Boat dual() {
        Boat boat = new Boat();
        boat.setType(BoatType.FISHING_BOAT);
        boat.setManufacturer("Lund");
        boat.setModel("1875");
        boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD, PropulsionType.ELECTRIC_TROLLING));
        boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
        boat.setMotors(List.of(
                new BoatMotor(PropulsionType.GAS_OUTBOARD, "Yamaha", "F20", BigDecimal.valueOf(20), null),
                new BoatMotor(PropulsionType.ELECTRIC_TROLLING, "Minn Kota", "Endura", null, BigDecimal.valueOf(55))
        ));
        return boat;
    }
}
