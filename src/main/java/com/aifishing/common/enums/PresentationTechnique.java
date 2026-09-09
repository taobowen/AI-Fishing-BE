package com.aifishing.common.enums;

public enum PresentationTechnique {
    STEADY_RETRIEVE,
    TWITCH_PAUSE,
    STOP_AND_GO,
    WALK_THE_DOG,
    POP_AND_PAUSE,
    BUZZ,
    LIFT_DROP,
    HOP_ALONG_BOTTOM,
    DRAG_AND_SHAKE,
    SWIM_NEAR_BOTTOM,
    YO_YO,
    DEAD_STICK,
    SLOW_ROLL,
    BURN,
    VERTICAL_JIG,
    CAST_ACROSS_CONTOUR,
    OTHER;

    public String displayName() {
        return switch (this) {
            case STEADY_RETRIEVE -> "Steady retrieve";
            case TWITCH_PAUSE -> "Twitch and pause";
            case STOP_AND_GO -> "Stop and go";
            case WALK_THE_DOG -> "Walk the dog";
            case POP_AND_PAUSE -> "Pop and pause";
            case BUZZ -> "Buzz the surface";
            case LIFT_DROP -> "Lift and drop";
            case HOP_ALONG_BOTTOM -> "Hop along the bottom";
            case DRAG_AND_SHAKE -> "Drag and shake";
            case SWIM_NEAR_BOTTOM -> "Swim near the bottom";
            case YO_YO -> "Yo-yo";
            case DEAD_STICK -> "Dead stick";
            case SLOW_ROLL -> "Slow roll";
            case BURN -> "Burn";
            case VERTICAL_JIG -> "Vertical jig";
            case CAST_ACROSS_CONTOUR -> "Cast across the contour";
            case OTHER -> "Other";
        };
    }
}
