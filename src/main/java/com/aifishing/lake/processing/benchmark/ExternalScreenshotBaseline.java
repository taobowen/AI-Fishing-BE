package com.aifishing.lake.processing.benchmark;

import com.aifishing.lake.processing.BenchmarkProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Component
public class ExternalScreenshotBaseline {

    private final ResourceLoader resourceLoader;
    private final BenchmarkProperties properties;
    private final ObjectMapper objectMapper;

    public ExternalScreenshotBaseline(
            ResourceLoader resourceLoader,
            BenchmarkProperties properties,
            ObjectMapper objectMapper
    ) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ExternalScreenshotSection load() {
        Resource resource = resourceLoader.getResource(properties.getScreenshotMetricsResource());
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> body = objectMapper.readValue(in, new TypeReference<>() {
            });
            return ExternalScreenshotSection.from(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Direct Screenshot Vision metrics", ex);
        }
    }
}
