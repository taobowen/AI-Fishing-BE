package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.repo.FishingRestrictionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RegulationProcessor implements DatasetProcessor {

    private static final int RAW_TEXT_LIMIT = 8000;

    private final FishingRestrictionRepository repository;

    public RegulationProcessor(FishingRestrictionRepository repository) {
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.REGULATION;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        RawPage page = pages.isEmpty() ? null : pages.getFirst();
        FishingRestriction record = new FishingRestriction();
        record.setLakeId(lake.getId());
        record.setProvider(provider);
        record.setImportVersion(importVersion);
        record.setSource(provider);
        record.setSourceRecordId("regulations-catalogue");
        record.setRestrictionType("TABULAR_CATALOGUE");
        record.setSourceReference(page == null ? null : page.sourceUrl());
        record.setGeometry(null);
        if (page != null && page.body() != null) {
            String text = new String(page.body(), StandardCharsets.UTF_8);
            record.setRawText(text.substring(0, Math.min(text.length(), RAW_TEXT_LIMIT)));
        }
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("spatialSanctuaryGeometry", false);
        structured.put("note", "Official structured species/date/geometry fields were not present; sanctuary polygons were not inferred");
        record.setStructuredData(structured);
        return NormalizeResult.partial(
                List.of(record),
                "Regulations stored as catalogue/raw text only; no spatial sanctuary geometry"
        );
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(FishingRestriction.class::cast).toList());
    }
}
