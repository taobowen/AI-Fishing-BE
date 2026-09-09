package com.aifishing.lake.ingestion.storage;

public record RawObjectKey(String lakeId, String datasetType, String importVersion, String fileName) {

    public static RawObjectKey page(String lakeId, String datasetType, String importVersion, int pageIndex, String extension) {
        String ext = extension == null || extension.isBlank() ? "bin" : extension;
        return new RawObjectKey(lakeId, datasetType, importVersion, "page-%04d.%s".formatted(pageIndex, ext));
    }

    public static RawObjectKey manifest(String lakeId, String datasetType, String importVersion) {
        return new RawObjectKey(lakeId, datasetType, importVersion, "manifest.json");
    }

    public String path() {
        return "raw/ontario/%s/%s/%s/%s".formatted(lakeId, datasetType, importVersion, fileName);
    }
}
