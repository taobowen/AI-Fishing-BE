package com.aifishing.lake.ingestion.dto;

import java.util.Map;

public record RawPage(
        int pageIndex,
        byte[] body,
        String sourceUrl,
        Map<String, Object> requestMetadata,
        int httpStatus,
        String contentType
) {
}
