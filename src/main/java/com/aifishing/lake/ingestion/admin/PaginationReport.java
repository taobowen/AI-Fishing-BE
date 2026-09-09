package com.aifishing.lake.ingestion.admin;

import java.util.List;
import java.util.Map;

/**
 * Pagination outcome for one dataset's last import version.
 * {@code exceededTransferLimit} on intermediate ArcGIS pages is normal continuation.
 */
public record PaginationReport(
        Integer pageCount,
        Integer rawRecordCount,
        Boolean transferLimitObserved,
        Boolean paginationComplete,
        String paginationWarning,
        String storageUriScheme
) {
    public static final int SAFETY_CAP_PAGES = 200;

    public static PaginationReport empty() {
        return new PaginationReport(null, null, null, null, null, null);
    }

    public static PaginationReport fromPageMetadata(List<Map<String, Object>> pages, List<String> storageUris) {
        if (pages == null || pages.isEmpty()) {
            return empty();
        }
        int pageCount = pages.size();
        int rawRecordCount = 0;
        boolean transferLimitObserved = false;
        boolean safetyCapHit = false;
        for (Map<String, Object> page : pages) {
            rawRecordCount += intValue(page, "featureCount", 0);
            if (boolValue(page, "exceededTransferLimit", false)) {
                transferLimitObserved = true;
            }
            if (boolValue(page, "paginationSafetyCapHit", false)) {
                safetyCapHit = true;
            }
        }
        Map<String, Object> last = pages.getLast();
        boolean lastExceeded = boolValue(last, "exceededTransferLimit", false);
        boolean complete = !safetyCapHit && !lastExceeded;
        String warning = null;
        if (safetyCapHit) {
            warning = "ArcGIS pagination stopped at the safety cap of " + SAFETY_CAP_PAGES
                    + " pages; dataset may be truncated";
            complete = false;
        } else if (lastExceeded) {
            warning = "Last page still had exceededTransferLimit=true; pagination did not complete";
            complete = false;
        }
        String scheme = null;
        if (storageUris != null) {
            for (String uri : storageUris) {
                if (uri != null && uri.contains(":")) {
                    scheme = uri.substring(0, uri.indexOf(':'));
                    break;
                }
            }
        }
        return new PaginationReport(pageCount, rawRecordCount, transferLimitObserved, complete, warning, scheme);
    }

    static int intValue(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static boolean boolValue(Map<String, Object> map, String key, boolean fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }
}
