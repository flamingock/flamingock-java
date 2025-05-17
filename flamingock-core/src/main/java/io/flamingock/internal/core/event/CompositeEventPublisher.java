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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CompositeEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger("CompositeEventPublisher");

    private final List<EventPublisher> publishers;

    public CompositeEventPublisher(List<EventPublisher> publishers) {
        this.publishers = publishers;
    }

    @Override
    public void publish(Event event) {
        logger.trace("starting multi-event publishing: {}", event);
        publishers.forEach(publisher -> {
            publisher.publish(event);
        });
        logger.trace("finished multi-event publishing: {}", event);
    }

}
