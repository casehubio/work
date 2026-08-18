package io.casehub.work.federation.transport;

public interface FederationTransport {

    void send(String cloudEventJson, String callbackUrl, byte[] hmacSecret);
}
