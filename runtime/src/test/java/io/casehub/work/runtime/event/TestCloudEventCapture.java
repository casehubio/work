package io.casehub.work.runtime.event;

import io.casehub.work.api.WorkCloudEventTypes;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class TestCloudEventCapture {

    private final List<CloudEvent> captured = new CopyOnWriteArrayList<>();

    void onCloudEvent(@ObservesAsync final CloudEvent ce) {
        if (ce.getType() != null && ce.getType().startsWith(WorkCloudEventTypes.PREFIX)) {
            captured.add(ce);
        }
    }

    public List<CloudEvent> ofType(final String type) {
        return captured.stream().filter(ce -> type.equals(ce.getType())).toList();
    }

    public List<CloudEvent> ofTypeAndSubject(final String type, final String subject) {
        return captured.stream()
                       .filter(ce -> type.equals(ce.getType()) && subject.equals(ce.getSubject()))
                       .toList();
    }

    public void clear() {
        captured.clear();
    }
}
