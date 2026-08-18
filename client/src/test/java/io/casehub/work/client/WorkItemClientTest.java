package io.casehub.work.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class WorkItemClientTest {

    private HttpServer server;
    private String baseUrl;
    private final WorkItemClient client = new WorkItemClient(Duration.ofSeconds(5));

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
        server.setExecutor(null);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void claimReturnsSuccessOn200() {
        server.createContext("/workitems/abc/claim", exchange -> {
            assertEquals("PUT", exchange.getRequestMethod());
            assertTrue(exchange.getRequestURI().getQuery().contains("claimant=user1"));
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes());
            exchange.getResponseBody().close();
        });

        var response = client.claim(baseUrl, "abc", "user1", "token123");
        assertTrue(response.isSuccess());
        assertEquals(200, response.statusCode());
    }

    @Test
    void claimReturnsConflictOn409() {
        server.createContext("/workitems/abc/claim", exchange -> {
            exchange.sendResponseHeaders(409, 2);
            exchange.getResponseBody().write("{}".getBytes());
            exchange.getResponseBody().close();
        });

        var response = client.claim(baseUrl, "abc", "user1", null);
        assertTrue(response.isConflict());
    }

    @Test
    void returnsUnavailableOn500() {
        server.createContext("/workitems/abc/claim", exchange -> {
            exchange.sendResponseHeaders(500, 2);
            exchange.getResponseBody().write("{}".getBytes());
            exchange.getResponseBody().close();
        });

        var response = client.claim(baseUrl, "abc", "user1", null);
        assertTrue(response.isUnavailable());
    }

    @Test
    void returnsUnavailableOnConnectionRefused() {
        server.stop(0);
        var response = client.claim("http://localhost:1", "abc", "user1", null);
        assertTrue(response.isUnavailable());
        assertEquals(503, response.statusCode());
    }

    @Test
    void sendsAuthorizationHeader() {
        server.createContext("/workitems/abc/claim", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            assertEquals("Bearer my-token", auth);
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes());
            exchange.getResponseBody().close();
        });

        client.claim(baseUrl, "abc", "user1", "my-token");
    }
}
