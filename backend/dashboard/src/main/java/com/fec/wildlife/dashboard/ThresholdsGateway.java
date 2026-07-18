package com.fec.wildlife.dashboard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches the fog gateway's real threshold rules over HTTP. Takes the
 * HttpClient and target URL as plain method arguments (not a static field
 * baked in at class load time), so the proxy behaviour -- success body
 * passthrough, and an exception on an unreachable upstream -- can be
 * exercised directly against a real local HttpServer in tests, without the
 * actual fog container running. See ThresholdsGatewayTest for both paths.
 */
class ThresholdsGateway {

    String fetch(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
