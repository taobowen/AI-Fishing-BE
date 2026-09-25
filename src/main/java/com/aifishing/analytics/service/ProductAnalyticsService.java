package com.aifishing.analytics.service;

import com.aifishing.analytics.api.TrackAnalyticsEventRequest;
import com.aifishing.analytics.api.TrackAnalyticsEventResponse;
import com.aifishing.analytics.domain.ProductAnalyticsEvent;
import com.aifishing.analytics.repo.ProductAnalyticsEventRepository;
import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ProductAnalyticsService {

    private final ProductAnalyticsEventRepository eventRepository;
    private final CurrentUser currentUser;

    public ProductAnalyticsService(ProductAnalyticsEventRepository eventRepository, CurrentUser currentUser) {
        this.eventRepository = eventRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public TrackAnalyticsEventResponse track(TrackAnalyticsEventRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("name is required");
        }
        ProductAnalyticsEvent event = new ProductAnalyticsEvent();
        event.setUserId(currentUser.id());
        event.setName(name);
        event.setProperties(request.properties() == null ? Map.of() : request.properties());
        ProductAnalyticsEvent saved = eventRepository.save(event);
        return new TrackAnalyticsEventResponse(
                saved.getId(),
                saved.getName(),
                saved.getProperties(),
                saved.getCreatedAt()
        );
    }
}
