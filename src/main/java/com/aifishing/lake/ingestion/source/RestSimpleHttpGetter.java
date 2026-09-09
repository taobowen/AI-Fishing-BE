package com.aifishing.lake.ingestion.source;

import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@Profile("!test")
public class RestSimpleHttpGetter implements SimpleHttpGetter {

    private final RestClient restClient;

    public RestSimpleHttpGetter() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public HttpGetResponse get(String url) {
        try {
            return restClient.get()
                    .uri(URI.create(url))
                    .exchange((request, response) -> {
                        byte[] body = response.bodyTo(byte[].class);
                        if (body == null) {
                            body = new byte[0];
                        }
                        return new HttpGetResponse(
                                response.getStatusCode().value(),
                                body,
                                response.getHeaders().getFirst("Content-Type")
                        );
                    });
        } catch (RestClientResponseException ex) {
            byte[] body = ex.getResponseBodyAsByteArray();
            if (body == null) {
                body = ex.getStatusText().getBytes(StandardCharsets.UTF_8);
            }
            return new HttpGetResponse(ex.getStatusCode().value(), body, ex.getStatusText());
        }
    }
}
