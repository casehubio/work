package io.casehub.work.runtime.event;

import io.casehub.work.api.WorkCloudEventTypes;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TestCloudEventCapture {

    private final ConcurrentHashMap<String, CloudEvent> captured = new ConcurrentHashMap<>();

    void onCloudEvent(@ObservesAsync final CloudEvent ce) {
        if (ce.getType() != null && ce.getType().startsWith(WorkCloudEventTypes.PREFIX) && ce.getId() != null) {
            captured.putIfAbsent(ce.getId(), ce);
        }
    }

    public List<CloudEvent> ofType(final String type) {
        return captured.values().stream().filter(ce -> type.equals(ce.getType())).toList();
    }

    public List<CloudEvent> ofTypeAndSubject(final String type, final String subject) {
        return captured.values().stream()
                       .filter(ce -> type.equals(ce.getType()) && subject.equals(ce.getSubject()))
                       .toList();
    }

    public void clear() {
        captured.clear();
    }
}
