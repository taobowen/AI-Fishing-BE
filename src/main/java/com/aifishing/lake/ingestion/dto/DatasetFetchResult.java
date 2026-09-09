package com.aifishing.lake.ingestion.dto;

import java.util.List;

public record DatasetFetchResult(
        Availability availability,
        String sourceReference,
        List<RawPage> pages,
        String message
) {
    public enum Availability {
        FETCHED,
        NOT_AVAILABLE
    }

    public static DatasetFetchResult fetched(String sourceReference, List<RawPage> pages) {
        return new DatasetFetchResult(Availability.FETCHED, sourceReference, pages, null);
    }

    public static DatasetFetchResult notAvailable(String sourceReference, String message) {
        return new DatasetFetchResult(Availability.NOT_AVAILABLE, sourceReference, List.of(), message);
    }
}
