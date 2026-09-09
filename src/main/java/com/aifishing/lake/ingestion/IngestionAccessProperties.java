package com.aifishing.lake.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingestion.access")
public class IngestionAccessProperties {

    private double maxLakeAssociationMeters = 300;

    public double getMaxLakeAssociationMeters() {
        return maxLakeAssociationMeters;
    }

    public void setMaxLakeAssociationMeters(double maxLakeAssociationMeters) {
        this.maxLakeAssociationMeters = maxLakeAssociationMeters;
    }
}
