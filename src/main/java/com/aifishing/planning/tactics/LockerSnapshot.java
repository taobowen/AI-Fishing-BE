package com.aifishing.planning.tactics;

import com.aifishing.common.enums.LureColorFamily;
import com.aifishing.common.enums.LureFamily;

import java.util.List;

public record LockerSnapshot(
        LureFamily family,
        String size,
        List<LureColorFamily> colors,
        Double weight,
        String label
) {
    public LockerSnapshot {
        colors = colors == null ? List.of() : List.copyOf(colors);
    }
}
