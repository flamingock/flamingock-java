/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.reactive.util;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublisherSyncTest {

    @Test
    void collectsPublisherValuesInOrder() {
        assertEquals(Arrays.asList("first", "second"), PublisherSync.collect(values("first", "second")));
    }

    @Test
    void returnsFirstPublisherValue() {
        assertEquals("first", PublisherSync.first(values("first", "second")));
    }

    @Test
    void returnsNullWhenPublisherIsEmpty() {
        assertNull(PublisherSync.first(values()));
    }

    @Test
    void completesPublisher() {
        PublisherSync.complete(values("first", "second"));
    }

    @Test
    void propagatesRuntimePublisherFailure() {
        IllegalStateException failure = new IllegalStateException("failed");
        IllegalStateException result = assertThrows(
                IllegalStateException.class,
                () -> PublisherSync.collect(error(failure)));
        assertSame(failure, result);
    }

    @Test
    void wrapsCheckedPublisherFailure() {
        IOException failure = new IOException("failed");
        PublisherSyncException result = assertThrows(
                PublisherSyncException.class,
                () -> PublisherSync.collect(error(failure)));
        assertSame(failure, result.getCause());
    }

    @Test
    void restoresInterruptedFlagWhenInterrupted() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        Thread thread = new Thread(() -> {
            try {
                PublisherSync.complete(neverCompletes());
            } catch (Throwable throwable) {
                interrupted.set(Thread.currentThread().isInterrupted());
                failure.set(throwable);
            }
        });

        thread.start();
        while (thread.getState() != Thread.State.WAITING) {
            Thread.yield();
        }
        thread.interrupt();
        thread.join(1000);

        assertFalse(thread.isAlive());
        assertTrue(interrupted.get());
        assertTrue(failure.get() instanceof PublisherSyncException);
    }

    @SafeVarargs
    private static <T> Publisher<T> values(T... values) {
        return subscriber -> subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long count) {
                for (T value : values) {
                    subscriber.onNext(value);
                }
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
            }
        });
    }

    private static <T> Publisher<T> error(Throwable failure) {
        return subscriber -> subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long count) {
                subscriber.onError(failure);
            }

            @Override
            public void cancel() {
            }
        });
    }

    private static <T> Publisher<T> neverCompletes() {
        return subscriber -> subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long count) {
            }

            @Override
            public void cancel() {
            }
        });
    }
}
