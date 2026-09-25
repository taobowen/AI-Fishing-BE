package com.aifishing.fishingtemplate.service;

/**
 * Hard limits for fishing template geometry. Exceeding any limit is a 400,
 * never a silent truncate.
 */
public final class TemplateGeometryLimits {

    public static final int MAX_TARGETS = 40;
    public static final int MAX_PATH_VERTICES = 200;
    public static final int MAX_ZONE_VERTICES = 500;
    public static final int MAX_REQUIRED_POINTS = 20;

    private TemplateGeometryLimits() {
    }
}
