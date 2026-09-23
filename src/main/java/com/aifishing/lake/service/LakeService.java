package com.aifishing.lake.service;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.WaterDepth;
import com.aifishing.lake.api.BathymetryContourDto;
import com.aifishing.lake.api.LakePlanningCapabilitiesResponse;
import com.aifishing.lake.api.LakePlanningCapabilitiesResponse.PipelineCapability;
import com.aifishing.lake.api.LakeResponse;
import com.aifishing.lake.api.LakeSummaryResponse;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.service.StructurePipelineReadiness;
import com.aifishing.lake.processing.service.StructurePipelineReadinessService;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LakeService {

    private final LakeRepository lakeRepository;
    private final BathymetryContourRepository contourRepository;
    private final GeoMapper geoMapper;
    private final StructurePipelineReadinessService readinessService;
    private final LakeCardImageResolver cardImageResolver;

    public LakeService(
            LakeRepository lakeRepository,
            BathymetryContourRepository contourRepository,
            GeoMapper geoMapper,
            StructurePipelineReadinessService readinessService,
            LakeCardImageResolver cardImageResolver
    ) {
        this.lakeRepository = lakeRepository;
        this.contourRepository = contourRepository;
        this.geoMapper = geoMapper;
        this.readinessService = readinessService;
        this.cardImageResolver = cardImageResolver;
    }

    @Transactional(readOnly = true)
    public List<LakeSummaryResponse> search(String query) {
        List<Lake> lakes = lakeRepository.searchByName(query);
        var images = cardImageResolver.urlsFor(lakes);
        return lakes.stream()
                .map(lake -> toSummary(lake, images.get(lake.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public LakeResponse get(UUID id) {
        return toDetail(lakeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lake not found")));
    }

    @Transactional(readOnly = true)
    public LakePlanningCapabilitiesResponse planningCapabilities(UUID id) {
        if (!lakeRepository.existsById(id)) {
            throw new NotFoundException("Lake not found");
        }
        Map<Pipeline, PipelineCapability> pipelines = new LinkedHashMap<>();
        pipelines.put(Pipeline.GIS, capability(readinessService.evaluate(id, Pipeline.GIS)));
        pipelines.put(Pipeline.HYBRID, capability(readinessService.evaluate(id, Pipeline.HYBRID)));
        return new LakePlanningCapabilitiesResponse(id, pipelines);
    }

    private PipelineCapability capability(StructurePipelineReadiness readiness) {
        return new PipelineCapability(
                readiness.available(),
                readiness.available() ? readiness.analysisVersion() : null,
                readiness.available() ? "READY" : "NOT_READY",
                readiness.available() ? null : readiness.availability()
        );
    }

    @Transactional(readOnly = true)
    public List<BathymetryContourDto> contours(UUID id) {
        if (!lakeRepository.existsById(id)) {
            throw new NotFoundException("Lake not found");
        }
        return contourRepository.findByLakeId(id).stream()
                .filter(contour -> contour.getGeometry() != null && !contour.getGeometry().isEmpty())
                .map(contour -> new BathymetryContourDto(
                        WaterDepth.meters(contour.getDepthM()),
                        geoMapper.toMapDto(contour.getGeometry())
                ))
                .filter(dto -> dto.geometry() != null)
                .toList();
    }

    private LakeSummaryResponse toSummary(Lake lake, String cardImageUrl) {
        return new LakeSummaryResponse(
                lake.getId(),
                lake.getName(),
                lake.getProvince(),
                lake.getCountry(),
                lake.getSource(),
                geoMapper.toDto(lake.getCentroid()),
                lake.getMeanDepthM(),
                lake.getMaxDepthM(),
                lake.getTimeZoneId(),
                cardImageUrl,
                lake.getCreatedAt(),
                lake.getUpdatedAt()
        );
    }

    private LakeResponse toDetail(Lake lake) {
        return new LakeResponse(
                lake.getId(),
                lake.getName(),
                lake.getProvince(),
                lake.getCountry(),
                lake.getSource(),
                lake.getSourceLakeId(),
                geoMapper.toDto(lake.getCentroid()),
                geoMapper.toDto(lake.getBoundary()),
                lake.getMeanDepthM(),
                lake.getMaxDepthM(),
                lake.getTimeZoneId(),
                cardImageResolver.urlFor(lake),
                lake.getCreatedAt(),
                lake.getUpdatedAt()
        );
    }
}
