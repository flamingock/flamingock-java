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
package io.flamingock.store.mongodb.reactive.internal;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublisherSyncTest {

    @Test
    void collectsPublisherValuesInOrder() {
        assertEquals(Arrays.asList("first", "second"), PublisherSync.collect(values("first", "second")));
    }

    @Test
    void propagatesPublisherFailure() {
        IllegalStateException failure = new IllegalStateException("failed");
        IllegalStateException result = assertThrows(
                IllegalStateException.class,
                () -> PublisherSync.collect(error(failure)));
        assertEquals(failure, result);
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

    private static <T> Publisher<T> error(RuntimeException failure) {
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
}
