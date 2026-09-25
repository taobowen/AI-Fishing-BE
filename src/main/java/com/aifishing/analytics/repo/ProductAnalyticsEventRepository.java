package com.aifishing.analytics.repo;

import com.aifishing.analytics.domain.ProductAnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAnalyticsEventRepository extends JpaRepository<ProductAnalyticsEvent, UUID> {

    List<ProductAnalyticsEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);
}
