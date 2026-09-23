package com.aifishing.lake.ingestion.job;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.OntarioProperties;
import com.aifishing.lake.ingestion.admin.DatasetStatusResponse;
import com.aifishing.lake.ingestion.admin.LakeImportSummaryResponse;
import com.aifishing.lake.ingestion.admin.PaginationReportAssembler;
import com.aifishing.lake.ingestion.dto.DatasetFetchResult;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.processor.DatasetProcessor;
import com.aifishing.lake.ingestion.processor.GeoJsonFeatureParser;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.service.DatasetStatusService;
import com.aifishing.lake.ingestion.service.IdentityResolutionException;
import com.aifishing.lake.ingestion.service.LakeIdentityResolver;
import com.aifishing.lake.ingestion.service.RawPagePersistenceService;
import com.aifishing.lake.ingestion.source.OntarioDatasetSource;
import com.aifishing.lake.ops.LakeOpsFailureCode;
import com.aifishing.lake.ops.LakeOpsJobException;
import com.aifishing.lake.repo.LakeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LakeImportJob implements ImportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(LakeImportJob.class);

    private final LakeRepository lakeRepository;
    private final LakeIdentityResolver identityResolver;
    private final List<OntarioDatasetSource> sources;
    private final Map<DatasetType, DatasetProcessor> processors;
    private final GeoJsonFeatureParser parser;
    private final RawPagePersistenceService rawPagePersistenceService;
    private final DatasetStatusService datasetStatusService;
    private final LakeDatasetStatusRepository datasetStatusRepository;
    private final OntarioProperties properties;
    private final PaginationReportAssembler paginationReportAssembler;

    public LakeImportJob(
            LakeRepository lakeRepository,
            LakeIdentityResolver identityResolver,
            List<OntarioDatasetSource> sources,
            List<DatasetProcessor> processors,
            GeoJsonFeatureParser parser,
            RawPagePersistenceService rawPagePersistenceService,
            DatasetStatusService datasetStatusService,
            LakeDatasetStatusRepository datasetStatusRepository,
            OntarioProperties properties,
            PaginationReportAssembler paginationReportAssembler
    ) {
        this.lakeRepository = lakeRepository;
        this.identityResolver = identityResolver;
        this.sources = sources.stream()
                .sorted(Comparator.comparing(OntarioDatasetSource::type))
                .toList();
        this.processors = processors.stream()
                .collect(Collectors.toMap(DatasetProcessor::type, Function.identity(), (a, b) -> a, () -> new EnumMap<>(DatasetType.class)));
        this.parser = parser;
        this.rawPagePersistenceService = rawPagePersistenceService;
        this.datasetStatusService = datasetStatusService;
        this.datasetStatusRepository = datasetStatusRepository;
        this.properties = properties;
        this.paginationReportAssembler = paginationReportAssembler;
    }

    @Override
    public LakeImportSummaryResponse run(UUID lakeId) {
        return run(lakeId, null);
    }

    @Override
    public LakeImportSummaryResponse run(UUID lakeId, DatasetType dataset) {
        Lake lake = lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        boolean skipIdentity = dataset == DatasetType.ACCESS_POINT && lake.getOgfId() != null;
        if (!skipIdentity) {
            try {
                lake = identityResolver.resolve(lake);
            } catch (IdentityResolutionException | IllegalArgumentException ex) {
                log.warn("Identity resolution failed for lake {}: {}", lakeId, ex.getMessage());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("lakeId", lake.getId().toString());
                result.put("lakeName", lake.getName());
                result.put("identityResolved", false);
                result.put("identityError", ex.getMessage());
                result.put("ogfId", lake.getOgfId());
                result.put("officialName", lake.getOfficialName());
                throw new LakeOpsJobException(
                        LakeOpsFailureCode.IDENTITY_RESOLUTION_FAILED,
                        ex.getMessage() == null ? "identity resolution failed" : ex.getMessage(),
                        result
                );
            }
        }

        boolean bathymetryCovered = true;
        String importVersion = UUID.randomUUID().toString();
        for (OntarioDatasetSource source : sources) {
            DatasetType type = source.type();
            if (dataset != null && type != dataset) {
                continue;
            }
            if (!bathymetryCovered && (type == DatasetType.BATHYMETRY_LINE || type == DatasetType.BATHYMETRY_POINT)) {
                datasetStatusService.markAttemptStarted(lake.getId(), type, properties.getProvider(), null);
                datasetStatusService.markOutcome(
                        lake.getId(),
                        type,
                        properties.getProvider(),
                        DatasetStatusCode.NOT_AVAILABLE,
                        0,
                        null,
                        Map.of(),
                        "Bathymetry index has no coverage for this lake"
                );
                continue;
            }
            DatasetStatusCode outcome = importDataset(lake, source, importVersion);
            if (type == DatasetType.BATHYMETRY_INDEX && outcome == DatasetStatusCode.NOT_AVAILABLE) {
                bathymetryCovered = false;
            }
            if (type == DatasetType.FISH_SPECIES) {
                lakeRepository.save(lake);
            }
        }

        List<DatasetStatusResponse> datasets = datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lake.getId())
                .stream()
                .map(status -> DatasetStatusResponse.from(
                        status,
                        paginationReportAssembler.forDataset(status.getLakeId(), status.getDatasetType())
                ))
                .toList();
        return new LakeImportSummaryResponse(
                lake.getId(),
                lake.getName(),
                true,
                null,
                lake.getOgfId(),
                lake.getOfficialName(),
                datasets
        );
    }

    private DatasetStatusCode importDataset(Lake lake, OntarioDatasetSource source, String importVersion) {
        DatasetType type = source.type();
        String provider = properties.getProvider();
        try {
            datasetStatusService.markAttemptStarted(lake.getId(), type, provider, null);
            DatasetFetchResult fetchResult = source.fetch(lake);
            if (fetchResult.availability() == DatasetFetchResult.Availability.NOT_AVAILABLE) {
                datasetStatusService.markOutcome(
                        lake.getId(),
                        type,
                        provider,
                        DatasetStatusCode.NOT_AVAILABLE,
                        0,
                        fetchResult.sourceReference(),
                        Map.of(),
                        fetchResult.message()
                );
                return DatasetStatusCode.NOT_AVAILABLE;
            }
            rawPagePersistenceService.persistPages(lake.getId(), type, provider, importVersion, fetchResult.pages());
            DatasetProcessor processor = processors.get(type);
            if (processor == null) {
                throw new IllegalStateException("No processor for dataset " + type);
            }
            List<ParsedFeature> features = new ArrayList<>();
            if (type != DatasetType.REGULATION) {
                for (var page : fetchResult.pages()) {
                    features.addAll(parser.parse(page.body()));
                }
            }
            DatasetProcessor.NormalizeResult normalized = processor.normalize(
                    lake,
                    provider,
                    importVersion,
                    features,
                    fetchResult.pages()
            );
            if (normalized.status() == DatasetStatusCode.AVAILABLE || normalized.status() == DatasetStatusCode.PARTIAL) {
                processor.replaceCanonical(lake.getId(), provider, normalized.records());
            }
            datasetStatusService.markOutcome(
                    lake.getId(),
                    type,
                    provider,
                    normalized.status(),
                    normalized.recordCount(),
                    fetchResult.sourceReference(),
                    normalized.metadata(),
                    normalized.message()
            );
            return normalized.status();
        } catch (Exception ex) {
            log.warn("Dataset {} failed for lake {}: {}", type, lake.getId(), ex.getMessage());
            datasetStatusService.markOutcome(
                    lake.getId(),
                    type,
                    provider,
                    DatasetStatusCode.FAILED,
                    null,
                    null,
                    Map.of(),
                    truncate(ex.getMessage())
            );
            return DatasetStatusCode.FAILED;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Import failed";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
