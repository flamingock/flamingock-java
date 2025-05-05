package io.flamingock.core.event;

import io.flamingock.core.event.model.Event;

import java.util.function.Consumer;

public interface EventListener {

    <T extends Event> EventListener addListener(Class<T> eventType, Consumer<T> listener);
}
