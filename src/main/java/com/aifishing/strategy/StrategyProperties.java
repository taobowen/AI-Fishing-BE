package com.aifishing.strategy;

import com.aifishing.lake.processing.dto.Pipeline;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.strategy")
public class StrategyProperties {

    private Pipeline featurePipeline = Pipeline.GIS;

    public Pipeline getFeaturePipeline() {
        return featurePipeline == null ? Pipeline.GIS : featurePipeline;
    }

    public void setFeaturePipeline(Pipeline featurePipeline) {
        this.featurePipeline = featurePipeline;
    }
}
