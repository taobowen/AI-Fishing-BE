package com.aifishing.lake.processing.extract;

import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.ProcessingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContourTopologyTest {

    @Test
    void closedNestedContoursDetectParentChildWithoutRaster() {
        ProcessingProperties properties = new ProcessingProperties();
        ContourTopology topology = new ContourTopology(properties);

        BathymetryContour outer = new BathymetryContour();
        outer.setDepthM(BigDecimal.TEN);
        outer.setSourceRecordId("outer");
        outer.setGeometry(ProcessingFixtures.closedSquare(-78.92, 44.75, 300));

        BathymetryContour inner = new BathymetryContour();
        inner.setDepthM(BigDecimal.valueOf(4));
        inner.setSourceRecordId("inner");
        inner.setGeometry(ProcessingFixtures.closedSquare(-78.92, 44.75, 100));

        List<ContourTopology.ClosedContour> closed = topology.closedPolygons(List.of(outer, inner));
        assertThat(closed).hasSize(2);

        ContourTopology.ClosedContour child = closed.stream()
                .filter(item -> item.areaM2() < 200_000)
                .findFirst()
                .orElseThrow();
        ContourTopology.ClosedContour parent = topology.parentOf(child, closed);
        assertThat(parent).isNotNull();
        assertThat(parent.depthM()).isEqualTo(10.0);
        assertThat(child.depthM()).isEqualTo(4.0);
    }
}
