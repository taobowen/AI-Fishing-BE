package com.aifishing.lake.ingestion.admin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationReportTest {

    @Test
    void intermediateTransferLimitWithCompletedLastPageIsSuccess() {
        PaginationReport report = PaginationReport.fromPageMetadata(List.of(
                Map.of("featureCount", 2000, "exceededTransferLimit", true, "resultRecordCount", 2000),
                Map.of("featureCount", 15, "exceededTransferLimit", false, "resultRecordCount", 2000)
        ), List.of("s3://bucket/raw/page.geojson"));
        assertThat(report.pageCount()).isEqualTo(2);
        assertThat(report.rawRecordCount()).isEqualTo(2015);
        assertThat(report.transferLimitObserved()).isTrue();
        assertThat(report.paginationComplete()).isTrue();
        assertThat(report.paginationWarning()).isNull();
        assertThat(report.storageUriScheme()).isEqualTo("s3");
        assertThat(report.rawRecordCount()).isNotEqualTo(report.pageCount() * 2000);
    }

    @Test
    void lastPageStillLimitedIsIncomplete() {
        PaginationReport report = PaginationReport.fromPageMetadata(List.of(
                Map.of("featureCount", 2000, "exceededTransferLimit", true, "paginationSafetyCapHit", false)
        ), List.of("file:///tmp/raw/page.geojson"));
        assertThat(report.paginationComplete()).isFalse();
        assertThat(report.paginationWarning()).contains("exceededTransferLimit");
        assertThat(report.storageUriScheme()).isEqualTo("file");
    }

    @Test
    void safetyCapIsIncomplete() {
        PaginationReport report = PaginationReport.fromPageMetadata(List.of(
                Map.of("featureCount", 2000, "exceededTransferLimit", true, "paginationSafetyCapHit", true)
        ), List.of());
        assertThat(report.paginationComplete()).isFalse();
        assertThat(report.paginationWarning()).contains("safety cap");
    }
}
