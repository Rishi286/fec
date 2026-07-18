package com.fec.wildlife.fog;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the reflection-driven dispatch directly: routes are discovered
 * from @Route-annotated methods on a plain target object (no hand-written
 * route table anywhere in this test), and a request only reaches the method
 * whose (method, path) combination it actually matches.
 */
class AnnotatedRouterTest {

    /** Minimal target object -- AnnotatedRouter finds these three methods purely via reflection. */
    static class TestTarget {
        @Route(method = "GET", path = "/one")
        void handleOne(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        }

        @Route(method = "GET", path = "/two")
        void handleTwoGet(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        }

        @Route(method = "POST", path = "/two")
        void handleTwoPost(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        }

        // Deliberately NOT annotated -- AnnotatedRouter must never register this.
        void notARoute(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(999, -1);
            exchange.close();
        }
    }

    private HttpServer server;
    private String base;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        server = AnnotatedRouter.bind(0, 2, new TestTarget());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private int statusOf(String method, String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
        builder = method.equals("POST") ? builder.POST(HttpRequest.BodyPublishers.noBody()) : builder.GET();
        return client.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void invokesTheAnnotatedMethodForItsOwnRoute() throws Exception {
        assertEquals(200, statusOf("GET", "/one"));
    }

    @Test
    void sameHandlerPathDifferentMethodReachesADifferentAnnotatedMethod() throws Exception {
        assertEquals(201, statusOf("GET", "/two"));
        assertEquals(202, statusOf("POST", "/two"));
    }

    @Test
    void unregisteredPathReturns404() throws Exception {
        assertEquals(404, statusOf("GET", "/nope"));
    }

    @Test
    void knownPathWithNoMatchingMethodReturns405() throws Exception {
        assertEquals(405, statusOf("POST", "/one"));
    }
}
