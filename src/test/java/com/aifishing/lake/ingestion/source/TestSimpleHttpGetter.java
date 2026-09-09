package com.aifishing.lake.ingestion.source;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Profile("test")
public class TestSimpleHttpGetter implements SimpleHttpGetter {

    @Override
    public HttpGetResponse get(String url) {
        return new HttpGetResponse(
                200,
                "Ontario recreational fishing regulations catalogue (fixture)".getBytes(StandardCharsets.UTF_8),
                MediaType.TEXT_PLAIN_VALUE
        );
    }
}
