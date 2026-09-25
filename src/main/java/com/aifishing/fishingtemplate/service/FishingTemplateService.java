package com.aifishing.fishingtemplate.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.fishingtemplate.api.CreateFishingTemplateRequest;
import com.aifishing.fishingtemplate.api.FishingTemplateResponse;
import com.aifishing.fishingtemplate.api.FishingTemplateTargetRequest;
import com.aifishing.fishingtemplate.api.FishingTemplateTargetResponse;
import com.aifishing.fishingtemplate.api.UpdateFishingTemplateRequest;
import com.aifishing.fishingtemplate.domain.FishingTemplate;
import com.aifishing.fishingtemplate.domain.FishingTemplateTarget;
import com.aifishing.fishingtemplate.repo.FishingTemplateRepository;
import com.aifishing.fishingtemplate.repo.FishingTemplateTargetRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.candidate.LakePlanningGeometryLoader;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FishingTemplateService {

    private final FishingTemplateRepository templateRepository;
    private final FishingTemplateTargetRepository targetRepository;
    private final LakeRepository lakeRepository;
    private final LakePlanningGeometryLoader geometryLoader;
    private final TemplateGeometryValidator geometryValidator;
    private final GeoMapper geoMapper;
    private final CurrentUser currentUser;

    public FishingTemplateService(
            FishingTemplateRepository templateRepository,
            FishingTemplateTargetRepository targetRepository,
            LakeRepository lakeRepository,
            LakePlanningGeometryLoader geometryLoader,
            TemplateGeometryValidator geometryValidator,
            GeoMapper geoMapper,
            CurrentUser currentUser
    ) {
        this.templateRepository = templateRepository;
        this.targetRepository = targetRepository;
        this.lakeRepository = lakeRepository;
        this.geometryLoader = geometryLoader;
        this.geometryValidator = geometryValidator;
        this.geoMapper = geoMapper;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<FishingTemplateResponse> list(UUID lakeId) {
        if (lakeId == null) {
            throw new BadRequestException("lakeId is required");
        }
        requireLake(lakeId);
        return templateRepository.findByUserIdAndLakeIdOrderByNameAsc(currentUser.id(), lakeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FishingTemplateResponse get(UUID id) {
        return toResponse(requireOwned(id));
    }

    @Transactional
    public FishingTemplateResponse create(CreateFishingTemplateRequest request) {
        Lake lake = requireLake(request.lakeId());
        String name = requireName(request.name());
        List<FishingTemplateTargetRequest> targets = request.targets() == null ? List.of() : request.targets();
        geometryValidator.assertTargetCount(targets.size());
        LakePlanningGeometry geometry = geometryLoader.load(lake);

        FishingTemplate template = new FishingTemplate();
        template.setUserId(currentUser.id());
        template.setLakeId(lake.getId());
        template.setName(name);
        FishingTemplate saved = templateRepository.save(template);
        replaceTargets(saved.getId(), targets, geometry);
        return toResponse(saved);
    }

    @Transactional
    public FishingTemplateResponse update(UUID id, UpdateFishingTemplateRequest request) {
        FishingTemplate template = requireOwned(id);
        if (request.name() != null) {
            template.setName(requireName(request.name()));
        }
        if (request.targets() != null) {
            geometryValidator.assertTargetCount(request.targets().size());
            Lake lake = requireLake(template.getLakeId());
            LakePlanningGeometry geometry = geometryLoader.load(lake);
            replaceTargets(template.getId(), request.targets(), geometry);
        }
        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public void delete(UUID id) {
        FishingTemplate template = requireOwned(id);
        targetRepository.deleteByTemplateId(template.getId());
        templateRepository.delete(template);
    }

    @Transactional(readOnly = true)
    public FishingTemplate requireOwnedSameLake(UUID templateId, UUID lakeId) {
        return templateRepository.findByIdAndUserIdAndLakeId(templateId, currentUser.id(), lakeId)
                .orElseThrow(() -> new BadRequestException("TEMPLATE_REQUIRED",
                        "HYBRID and CUSTOM modes require an owned fishing template for the trip lake"));
    }

    private void replaceTargets(
            UUID templateId,
            List<FishingTemplateTargetRequest> requests,
            LakePlanningGeometry lakeGeometry
    ) {
        targetRepository.deleteByTemplateId(templateId);
        targetRepository.flush();
        List<FishingTemplateTarget> rows = new ArrayList<>();
        int index = 0;
        for (FishingTemplateTargetRequest request : requests) {
            Geometry raw = geoMapper.fromGeoJson(request.geometry());
            if (raw == null || raw.isEmpty()) {
                throw new BadRequestException(
                        TemplateGeometryValidator.GEOMETRY_INVALID,
                        "Template target geometry is invalid or unsupported"
                );
            }
            Geometry normalized = geometryValidator.validateAndNormalize(request.kind(), raw, lakeGeometry);
            FishingTemplateTarget target = new FishingTemplateTarget();
            target.setTemplateId(templateId);
            target.setKind(request.kind());
            target.setName(blankToNull(request.name()));
            target.setGeometry(normalized);
            target.setSortOrder(request.sortOrder() != null ? request.sortOrder() : index);
            rows.add(target);
            index++;
        }
        targetRepository.saveAll(rows);
    }

    private FishingTemplate requireOwned(UUID id) {
        return templateRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Fishing template not found"));
    }

    private Lake requireLake(UUID lakeId) {
        return lakeRepository.findById(lakeId)
                .orElseThrow(() -> new BadRequestException("Lake not found"));
    }

    private FishingTemplateResponse toResponse(FishingTemplate template) {
        List<FishingTemplateTargetResponse> targets = targetRepository
                .findByTemplateIdOrderBySortOrderAscIdAsc(template.getId())
                .stream()
                .map(this::toTargetResponse)
                .toList();
        return new FishingTemplateResponse(
                template.getId(),
                template.getUserId(),
                template.getLakeId(),
                template.getName(),
                targets,
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private FishingTemplateTargetResponse toTargetResponse(FishingTemplateTarget target) {
        return new FishingTemplateTargetResponse(
                target.getId(),
                target.getKind(),
                target.getName(),
                geoMapper.toGeoJson(target.getGeometry()),
                target.getSortOrder()
        );
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Template name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 128) {
            throw new BadRequestException("Template name must be at most 128 characters");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
