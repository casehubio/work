package io.casehub.work.federation;

import io.casehub.work.federation.transport.FederationTransport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Alternative
@Priority(100)
public class InMemoryFederationTransport implements FederationTransport {

    @Inject
    FederationReceiver receiver;

    private final List<SentEvent> sentEvents = new ArrayList<>();

    @Override
    public void send(String cloudEventJson, String callbackUrl, byte[] hmacSecret) {
        sentEvents.add(new SentEvent(cloudEventJson, callbackUrl));
        String signature = io.casehub.work.federation.transport.HmacSigner.sign(cloudEventJson, hmacSecret);
        receiver.onEvent(cloudEventJson, signature, hmacSecret);
    }

    public List<SentEvent> sentEvents() {
        return List.copyOf(sentEvents);
    }

    public void clear() {
        sentEvents.clear();
    }

    public record SentEvent(String cloudEventJson, String callbackUrl) {}
}
