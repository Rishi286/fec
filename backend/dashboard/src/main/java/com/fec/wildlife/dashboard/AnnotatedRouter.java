package com.fec.wildlife.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Reflection-driven {@code @Route} annotation dispatch mirroring the fog module's AnnotatedRouter, duplicated here rather than shared since each Maven module deploys independently. */
public class AnnotatedRouter {

    private record RouteEntry(String method, Method javaMethod) {}

    public static HttpServer bind(int port, int workerThreads, Object target) throws IOException {
        Map<String, List<RouteEntry>> byPath = new HashMap<>();
        for (Method method : target.getClass().getDeclaredMethods()) {
            Route route = method.getAnnotation(Route.class);
            if (route == null) continue;
            method.setAccessible(true);
            byPath.computeIfAbsent(route.path(), p -> new ArrayList<>()).add(new RouteEntry(route.method(), method));
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        for (var entry : byPath.entrySet()) {
            List<RouteEntry> candidates = entry.getValue();
            server.createContext(entry.getKey(), exchange -> dispatch(exchange, target, candidates));
        }
        server.setExecutor(Executors.newFixedThreadPool(workerThreads));
        return server;
    }

    private static void dispatch(HttpExchange exchange, Object target, List<RouteEntry> candidates) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        RouteEntry match = null;
        for (RouteEntry candidate : candidates) {
            if (candidate.method().equals(requestMethod)) {
                match = candidate;
                break;
            }
        }
        if (match == null) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        try {
            match.javaMethod().invoke(target, exchange);
        } catch (InvocationTargetException e) {
            System.out.println(exchange.getRequestURI() + " failed: " + e.getCause());
            respondError(exchange);
        } catch (IllegalAccessException e) {
            System.out.println(exchange.getRequestURI() + " reflection failure: " + e);
            respondError(exchange);
        }
    }

    private static void respondError(HttpExchange exchange) throws IOException {
        byte[] bytes = "{\"error\":\"internal error\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(500, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
