package com.aifishing.fishingtemplate.repo;

import com.aifishing.fishingtemplate.domain.FishingTemplateTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FishingTemplateTargetRepository extends JpaRepository<FishingTemplateTarget, UUID> {

    List<FishingTemplateTarget> findByTemplateIdOrderBySortOrderAscIdAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
