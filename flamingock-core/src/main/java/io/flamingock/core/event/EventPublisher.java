package io.flamingock.core.event;

import io.flamingock.core.event.model.Event;

public interface EventPublisher {

    void publish(Event event);
}
