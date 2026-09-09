package com.aifishing.lake.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ontario")
public class OntarioProperties {

    private String provider = "LIO";
    private double identityMaxDistanceKm = 15;
    private int pageSize = 2000;
    private final Lio lio = new Lio();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public double getIdentityMaxDistanceKm() {
        return identityMaxDistanceKm;
    }

    public void setIdentityMaxDistanceKm(double identityMaxDistanceKm) {
        this.identityMaxDistanceKm = identityMaxDistanceKm;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public Lio getLio() {
        return lio;
    }

    public static class Lio {
        private String open01Base;
        private String open07Base;
        private int waterbodyLayer;
        private int shorelineLayer;
        private int hydroPolyLayer;
        private int watercourseLayer;
        private int bathymetryIndexLayer;
        private int bathymetryLineLayer;
        private int bathymetryPointLayer;
        private int wetlandLayer;
        private int araPolyLayer;
        private int fishActivityLayer;
        private int accessLayer;
        private int fmzLayer;
        private String stockingUrl;
        private String regulationsUrl;

        public String layerUrl(String base, int layer) {
            return base + "/" + layer;
        }

        public String getOpen01Base() {
            return open01Base;
        }

        public void setOpen01Base(String open01Base) {
            this.open01Base = open01Base;
        }

        public String getOpen07Base() {
            return open07Base;
        }

        public void setOpen07Base(String open07Base) {
            this.open07Base = open07Base;
        }

        public int getWaterbodyLayer() {
            return waterbodyLayer;
        }

        public void setWaterbodyLayer(int waterbodyLayer) {
            this.waterbodyLayer = waterbodyLayer;
        }

        public int getShorelineLayer() {
            return shorelineLayer;
        }

        public void setShorelineLayer(int shorelineLayer) {
            this.shorelineLayer = shorelineLayer;
        }

        public int getHydroPolyLayer() {
            return hydroPolyLayer;
        }

        public void setHydroPolyLayer(int hydroPolyLayer) {
            this.hydroPolyLayer = hydroPolyLayer;
        }

        public int getWatercourseLayer() {
            return watercourseLayer;
        }

        public void setWatercourseLayer(int watercourseLayer) {
            this.watercourseLayer = watercourseLayer;
        }

        public int getBathymetryIndexLayer() {
            return bathymetryIndexLayer;
        }

        public void setBathymetryIndexLayer(int bathymetryIndexLayer) {
            this.bathymetryIndexLayer = bathymetryIndexLayer;
        }

        public int getBathymetryLineLayer() {
            return bathymetryLineLayer;
        }

        public void setBathymetryLineLayer(int bathymetryLineLayer) {
            this.bathymetryLineLayer = bathymetryLineLayer;
        }

        public int getBathymetryPointLayer() {
            return bathymetryPointLayer;
        }

        public void setBathymetryPointLayer(int bathymetryPointLayer) {
            this.bathymetryPointLayer = bathymetryPointLayer;
        }

        public int getWetlandLayer() {
            return wetlandLayer;
        }

        public void setWetlandLayer(int wetlandLayer) {
            this.wetlandLayer = wetlandLayer;
        }

        public int getAraPolyLayer() {
            return araPolyLayer;
        }

        public void setAraPolyLayer(int araPolyLayer) {
            this.araPolyLayer = araPolyLayer;
        }

        public int getFishActivityLayer() {
            return fishActivityLayer;
        }

        public void setFishActivityLayer(int fishActivityLayer) {
            this.fishActivityLayer = fishActivityLayer;
        }

        public int getAccessLayer() {
            return accessLayer;
        }

        public void setAccessLayer(int accessLayer) {
            this.accessLayer = accessLayer;
        }

        public int getFmzLayer() {
            return fmzLayer;
        }

        public void setFmzLayer(int fmzLayer) {
            this.fmzLayer = fmzLayer;
        }

        public String getStockingUrl() {
            return stockingUrl;
        }

        public void setStockingUrl(String stockingUrl) {
            this.stockingUrl = stockingUrl;
        }

        public String getRegulationsUrl() {
            return regulationsUrl;
        }

        public void setRegulationsUrl(String regulationsUrl) {
            this.regulationsUrl = regulationsUrl;
        }
    }
}
