package com.aifishing.lake.ingestion.source;

public interface SimpleHttpGetter {

    HttpGetResponse get(String url);

    record HttpGetResponse(int status, byte[] body, String contentType) {
    }
}
