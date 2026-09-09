package com.aifishing.common.enums;

public enum LureColorFamily {
    NATURAL,
    WHITE_PEARL,
    SILVER,
    GOLD,
    GREEN_PUMPKIN,
    BROWN,
    BLACK,
    DARK,
    CHARTREUSE,
    FIRETIGER,
    BRIGHT,
    TRANSLUCENT,
    OTHER;

    public String displayName() {
        return switch (this) {
            case NATURAL -> "Natural";
            case WHITE_PEARL -> "White / pearl";
            case SILVER -> "Silver";
            case GOLD -> "Gold";
            case GREEN_PUMPKIN -> "Green pumpkin";
            case BROWN -> "Brown";
            case BLACK -> "Black";
            case DARK -> "Dark";
            case CHARTREUSE -> "Chartreuse";
            case FIRETIGER -> "Firetiger";
            case BRIGHT -> "Bright";
            case TRANSLUCENT -> "Translucent";
            case OTHER -> "Other";
        };
    }
}
