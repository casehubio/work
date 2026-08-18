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

    public ClientResponse claim(String baseUrl, String workItemId, String claimantId, String bearerToken) {
        String url = baseUrl + "/workitems/" + workItemId + "/claim?claimant=" + claimantId;
        return executePut(url, bearerToken);
    }

    public ClientResponse complete(String baseUrl, String workItemId, String actorId,
                                    String resolution, String outcome, String bearerToken) {
        String url = baseUrl + "/workitems/" + workItemId + "/complete";
        String body = String.format("{\"actorId\":\"%s\",\"resolution\":\"%s\",\"outcome\":\"%s\"}",
                actorId, escapeJson(resolution), escapeJson(outcome));
        return executePost(url, body, bearerToken);
    }

    public ClientResponse reject(String baseUrl, String workItemId, String actorId,
                                  String reason, String outcome, String bearerToken) {
        String url = baseUrl + "/workitems/" + workItemId + "/reject";
        String body = String.format("{\"actorId\":\"%s\",\"reason\":\"%s\",\"outcome\":\"%s\"}",
                actorId, escapeJson(reason), escapeJson(outcome));
        return executePost(url, body, bearerToken);
    }

    public ClientResponse delegate(String baseUrl, String workItemId, String actorId,
                                    String toAssigneeId, String bearerToken) {
        String url = baseUrl + "/workitems/" + workItemId + "/delegate?actorId=" + actorId + "&to=" + toAssigneeId;
        return executePut(url, bearerToken);
    }

    public ClientResponse release(String baseUrl, String workItemId, String actorId, String bearerToken) {
        String url = baseUrl + "/workitems/" + workItemId + "/release?actorId=" + actorId;
        return executePut(url, bearerToken);
    }

    private ClientResponse executePut(String url, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .timeout(timeout);
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return execute(builder.build());
    }

    private ClientResponse executePost(String url, String body, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout);
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

    private static String escapeJson(String value) {
        return value != null ? value.replace("\"", "\\\"") : "";
    }

    public record ClientResponse(int statusCode, JsonNode body) {
        public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }
        public boolean isConflict() { return statusCode == 409; }
        public boolean isUnavailable() { return statusCode >= 500; }
    }
}
