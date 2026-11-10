package ras.demo;

import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ResourceAcquisitionDemoTest {
    private ResourceAcquisitionDemo demo;
    private TestScheduler scheduler;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @BeforeEach
    void start() throws Exception {
        scheduler = new TestScheduler();
        demo = new ResourceAcquisitionDemo(0, 5_000, scheduler);
        demo.start();
    }

    @AfterEach
    void stop() {
        demo.close();
    }

    private HttpResponse<String> request(String method, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + demo.port() + path))
                .timeout(Duration.ofSeconds(3)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String command(String action, String user) throws Exception {
        var response = request("POST", "/api/command?action=" + action + "&user=" + user + "&key=demo/test");
        assertEquals(200, response.statusCode());
        return response.body();
    }

    @Test
    void servesHealthAndBrowserAssets() throws Exception {
        assertEquals("{\"status\":\"ok\"}", request("GET", "/health").body());
        assertTrue(request("GET", "/").body().contains("ResourceAcquisitionService demo"));
        assertTrue(request("GET", "/demo.js").body().contains("URLSearchParams"));
        assertTrue(request("GET", "/demo.css").headers().firstValue("Content-Type").orElseThrow().startsWith("text/css"));
    }

    @Test
    void enforcesOwnershipAndAllowsReleaseAndReacquisition() throws Exception {
        assertTrue(command("lock", "alice").contains("\"result\":\"LockSucceeded\""));
        assertTrue(command("lock", "bob").contains("\"result\":\"LockFailed\""));
        assertTrue(command("unlock", "bob").contains("\"result\":\"UnlockFailed\""));
        assertTrue(command("unlock", "alice").contains("\"result\":\"UnlockSucceeded\""));
        assertTrue(command("lock", "bob").contains("\"result\":\"LockSucceeded\""));
    }

    @Test
    void automaticallyExpiresAnAcquiredLock() throws Exception {
        assertTrue(command("lock", "alice").contains("LockSucceeded"));
        scheduler.advanceTimeBy(4_999, TimeUnit.MILLISECONDS);
        assertTrue(command("lock", "bob").contains("LockFailed"));
        scheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        assertTrue(command("lock", "bob").contains("LockSucceeded"));

        // The new owner receives a full lease measured from reacquisition.
        scheduler.advanceTimeBy(4_999, TimeUnit.MILLISECONDS);
        assertTrue(command("lock", "alice").contains("LockFailed"));
        scheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        assertTrue(command("lock", "alice").contains("LockSucceeded"));
    }

    @Test
    void rejectsWrongMethodsMissingDuplicateAndInvalidInputs() throws Exception {
        assertEquals(405, request("GET", "/api/command?action=lock&user=alice&key=demo").statusCode());
        assertEquals(405, request("POST", "/health").statusCode());
        assertEquals(404, request("GET", "/unknown").statusCode());
        for (String query : new String[]{"", "?action", "?action=lock&user=alice&user=bob&key=demo",
                "?action=lock&user=mallory&key=demo", "?action=reset&user=alice&key=demo",
                "?action=lock&user=alice&key=%22", "?action=lock&user=alice&key=demo&extra=true"}) {
            assertEquals(400, request("POST", "/api/command" + query).statusCode(), query);
        }
        assertTrue(command("lock", "alice").contains("LockSucceeded"));
    }
}
