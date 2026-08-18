package io.casehub.work.federation.transport;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class WebhookFederationTransport implements FederationTransport {

    private static final Logger LOG = Logger.getLogger(WebhookFederationTransport.class);
    private static final String CONTENT_TYPE = "application/cloudevents+json";
    private static final String SIGNATURE_HEADER = "X-Federation-Signature";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void send(String cloudEventJson, String callbackUrl, byte[] hmacSecret) {
        String signature = HmacSigner.sign(cloudEventJson, hmacSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .header("Content-Type", CONTENT_TYPE)
                .header(SIGNATURE_HEADER, signature)
                .POST(HttpRequest.BodyPublishers.ofString(cloudEventJson))
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                LOG.warnf("Federation webhook to %s returned %d: %s",
                        callbackUrl, response.statusCode(), response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.errorf("Federation webhook to %s interrupted", callbackUrl);
        } catch (Exception e) {
            LOG.errorf(e, "Federation webhook to %s failed", callbackUrl);
        }
    }
}
