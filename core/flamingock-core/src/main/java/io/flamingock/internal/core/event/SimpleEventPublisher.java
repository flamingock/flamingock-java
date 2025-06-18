/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.internal.core.event;


import io.flamingock.internal.core.event.model.Event;
import io.flamingock.internal.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class SimpleEventPublisher implements EventPublisher, EventListener {

    private static final Logger logger = LoggerFactory.getLogger("SimpleEventPublisher");

    private final List<Pair<Class<? extends Event>, Consumer<? extends Event>>> listeners = new ArrayList<>();

    public SimpleEventPublisher() {
    }

    @Override
    public <T extends Event> SimpleEventPublisher addListener(Class<T> eventType, Consumer<T> listener) {
        return addListenerInternal(eventType, listener);
    }

    @Override
    public void publish(Event event) {
        logger.trace("publishing event: {}", event);
        Consumer<Event> eventConsumer = getListener(event.getClass())
                .orElse(event1 -> logger.debug("No registered listener for Event[{}]", event1.toString()));
        eventConsumer.accept(event);
        logger.trace("finished event publishing: {}", event);
    }


    //We ensure programmatically that the type of event is aligned with the consumer,
    //although we return a generic Consumer<Event>
    @SuppressWarnings("unchecked")
    private <T extends Event> Optional<Consumer<Event>> getListener(Class<T> eventType) {
        return listeners
                .stream()
                .filter(pair -> pair.getFirst().isAssignableFrom(eventType))
                .map(Pair::getSecond)
                .map(consumer -> (Consumer<Event>) consumer)
                .findFirst();
    }

    private SimpleEventPublisher addListenerInternal(Class<? extends Event> eventType, Consumer<? extends Event> listener) {
        if(listener != null) {
            listeners.add(new Pair<>(eventType, listener));
        }
        return this;
    }
}
