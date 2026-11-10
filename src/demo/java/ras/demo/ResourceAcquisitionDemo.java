package ras.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import ras.ResourceAcquisitionCommand;
import ras.SimpleResourceAcquisitionService;
import ras.TimeSpan;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** An in-memory demonstration, separate from the published library. */
public final class ResourceAcquisitionDemo implements AutoCloseable {
    private static final Pattern RESOURCE_KEY = Pattern.compile("[a-z][a-z0-9/_-]{0,63}");
    private static final Set<String> USERS = Set.of("alice", "bob");
    private static final Map<String, String> ASSETS = Map.of(
            "/", "index.html", "/demo.js", "demo.js", "/demo.css", "demo.css");
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final SimpleResourceAcquisitionService<String> service;

    public ResourceAcquisitionDemo(int port, long ttlMillis) throws IOException {
        this(port, ttlMillis, Schedulers.computation());
    }

    ResourceAcquisitionDemo(int port, long ttlMillis, Scheduler scheduler) throws IOException {
        if (ttlMillis < 1 || ttlMillis > 60_000) {
            throw new IllegalArgumentException("TTL must be between 1 and 60000 milliseconds");
        }
        server = HttpServer.create(new InetSocketAddress(port), 32);
        service = new SimpleResourceAcquisitionService<>(scheduler, new TimeSpan(ttlMillis, TimeUnit.MILLISECONDS));
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/command")) {
                command(exchange);
            } else if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "text/plain", "GET required");
            } else if (path.equals("/health")) {
                send(exchange, 200, "application/json", "{\"status\":\"ok\"}");
            } else if (ASSETS.containsKey(path)) {
                asset(exchange, ASSETS.get(path));
            } else {
                send(exchange, 404, "text/plain", "Not found");
            }
        }
    }

    private void command(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            send(exchange, 405, "text/plain", "POST required");
            return;
        }
        try {
            Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
            String key = query.getOrDefault("key", "");
            String user = query.getOrDefault("user", "");
            if (!RESOURCE_KEY.matcher(key).matches() || !USERS.contains(user)) {
                throw new IllegalArgumentException("Use alice or bob and a resource label of up to 64 lowercase letters, digits, slashes, underscores or dashes");
            }
            ResourceAcquisitionCommand action = switch (query.getOrDefault("action", "")) {
                case "lock" -> ResourceAcquisitionCommand.Lock;
                case "unlock" -> ResourceAcquisitionCommand.Unlock;
                default -> throw new IllegalArgumentException("Use action=lock or action=unlock");
            };
            var result = service.commit(action, user, key);
            // All interpolated strings come from enums or the bounded allowlists above.
            String body = "{\"result\":\"%s\",\"owner\":\"%s\",\"key\":\"%s\",\"state\":\"%s\"}"
                    .formatted(result.getCommitResult().name(), result.getResource().getUserName(),
                            key, result.getResource().getState().name());
            send(exchange, 200, "application/json", body);
        } catch (IllegalArgumentException invalid) {
            send(exchange, 400, "text/plain", invalid.getMessage());
        }
    }

    private static Map<String, String> parameters(String raw) {
        if (raw == null || raw.length() > 2048) {
            throw new IllegalArgumentException("A bounded command query is required");
        }
        Map<String, String> result = new HashMap<>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Each parameter needs a value");
            }
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            if (!Set.of("action", "user", "key").contains(key) || result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Unknown or duplicate parameter");
            }
        }
        return result;
    }

    private static void asset(HttpExchange exchange, String name) throws IOException {
        try (var stream = ResourceAcquisitionDemo.class.getResourceAsStream("/demo/" + name)) {
            if (stream == null) {
                send(exchange, 500, "text/plain", "Demo asset is missing");
                return;
            }
            String type = switch (name) {
                case "demo.js" -> "text/javascript";
                case "demo.css" -> "text/css";
                default -> "text/html";
            };
            send(exchange, 200, type, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type + "; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        service.close();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        long ttl = Long.parseLong(System.getenv().getOrDefault("DEMO_TTL_MILLIS", "10000"));
        var demo = new ResourceAcquisitionDemo(port, ttl);
        Runtime.getRuntime().addShutdownHook(new Thread(demo::close));
        demo.start();
        System.out.println("ResourceAcquisitionService demo listening on port " + demo.port());
    }
}
