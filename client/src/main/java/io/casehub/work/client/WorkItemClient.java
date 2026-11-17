package io.casehub.work.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WorkItemClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public WorkItemClient(Duration timeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.objectMapper = new ObjectMapper();
        this.timeout = timeout;
    }

    public ClientResponse claim(String baseUrl, String workItemId, String bearerToken) {
        String url = baseUrl + "/api/work/lifecycle/claim/" + workItemId;
        return executePost(url, null, bearerToken);
    }

    public ClientResponse complete(String baseUrl, String workItemId,
                                   String resolution, String outcome, String bearerToken) {
        String url  = baseUrl + "/api/work/lifecycle/complete/" + workItemId;
        var    body = objectMapper.createObjectNode();
        body.put("resolution", resolution);
        body.put("outcome", outcome);
        return executePost(url, body.toString(), bearerToken);
    }

    public ClientResponse reject(String baseUrl, String workItemId,
                                 String reason, String outcome, String bearerToken) {
        String url  = baseUrl + "/api/work/lifecycle/reject/" + workItemId;
        var    body = objectMapper.createObjectNode();
        body.put("reason", reason);
        body.put("outcome", outcome);
        return executePost(url, body.toString(), bearerToken);
    }

    public ClientResponse delegate(String baseUrl, String workItemId,
                                   String toAssigneeId, String bearerToken) {
        String url  = baseUrl + "/api/work/lifecycle/delegate/" + workItemId;
        var    body = objectMapper.createObjectNode();
        body.put("to", toAssigneeId);
        return executePost(url, body.toString(), bearerToken);
    }

    public ClientResponse release(String baseUrl, String workItemId, String bearerToken) {
        String url = baseUrl + "/api/work/lifecycle/release/" + workItemId;
        return executePost(url, null, bearerToken);
    }

    private ClientResponse executePost(String url, String body, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                                                 .uri(URI.create(url))
                                                 .timeout(timeout);
        if (body != null) {
            builder.header("Content-Type", "application/json")
                   .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return execute(builder.build());
    }

    private ClientResponse execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = null;
            if (response.body() != null && !response.body().isBlank()) {
                try {
                    body = objectMapper.readTree(response.body());
                } catch (Exception ignored) {}
            }
            return new ClientResponse(response.statusCode(), body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ClientResponse(503, null);
        } catch (Exception e) {
            return new ClientResponse(503, null);
        }
    }

    public record ClientResponse(int statusCode, JsonNode body) {
        public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }
        public boolean isConflict() { return statusCode == 409; }
        public boolean isUnavailable() { return statusCode >= 500; }
    }
}
