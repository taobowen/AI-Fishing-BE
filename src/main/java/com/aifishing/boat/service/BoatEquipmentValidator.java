package com.aifishing.boat.service;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class BoatEquipmentValidator {

    private static final Set<PropulsionType> ELECTRIC = EnumSet.of(
            PropulsionType.ELECTRIC_TROLLING,
            PropulsionType.ELECTRIC_OUTBOARD
    );
    private static final Set<PropulsionType> NO_POWER = EnumSet.of(
            PropulsionType.NONE,
            PropulsionType.PADDLE,
            PropulsionType.PEDAL
    );

    public void applyDefaultsAndValidate(Boat boat) {
        if (boat.getType() == null) {
            boat.setType(BoatType.OTHER);
        }
        List<PropulsionType> types = boat.getPropulsionTypes() == null ? List.of() : boat.getPropulsionTypes();
        if (types.isEmpty()) {
            types = List.of(PropulsionType.NONE);
            boat.setPropulsionTypes(types);
        }
        PropulsionType primary = boat.getPrimaryTransitPropulsionType();
        if (primary == null || primary == PropulsionType.NONE && !types.contains(PropulsionType.NONE)) {
            primary = types.contains(PropulsionType.GAS_OUTBOARD) ? PropulsionType.GAS_OUTBOARD : types.getFirst();
            boat.setPrimaryTransitPropulsionType(primary);
        }
        if (!types.contains(boat.getPrimaryTransitPropulsionType())) {
            throw new BadRequestException("primaryTransitPropulsionType must be one of propulsionTypes");
        }
        List<BoatMotor> motors = boat.getMotors() == null ? new ArrayList<>() : new ArrayList<>(boat.getMotors());
        if (motors.isEmpty()) {
            for (PropulsionType type : types) {
                motors.add(new BoatMotor(type, null, null, null, null));
            }
            boat.setMotors(motors);
        }
        for (BoatMotor motor : boat.getMotors()) {
            if (motor == null || motor.propulsionType() == null) {
                throw new BadRequestException("Each motor must include propulsionType");
            }
            PropulsionType type = motor.propulsionType();
            if (motor.horsepower() != null && type != PropulsionType.GAS_OUTBOARD) {
                throw new BadRequestException("Horsepower is only allowed on GAS_OUTBOARD motors");
            }
            if (motor.thrustLb() != null && !ELECTRIC.contains(type)) {
                throw new BadRequestException("Thrust is only allowed on electric motors");
            }
            if (NO_POWER.contains(type) && (motor.horsepower() != null || motor.thrustLb() != null)) {
                throw new BadRequestException("Paddle, pedal, and none propulsion cannot include HP or thrust");
            }
        }
    }
}
