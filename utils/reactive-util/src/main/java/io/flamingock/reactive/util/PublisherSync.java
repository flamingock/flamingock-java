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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class PublisherSync {

    private PublisherSync() {
    }

    public static <T> T first(Publisher<T> publisher) {
        List<T> values = collect(publisher);
        return values.isEmpty() ? null : values.get(0);
    }

    public static <T> List<T> collect(Publisher<T> publisher) {
        List<T> values = new ArrayList<>();
        await(publisher, values::add);
        return values;
    }

    public static void complete(Publisher<?> publisher) {
        await(publisher, value -> {
        });
    }

    private static <T> void await(Publisher<T> publisher, ItemConsumer<T> consumer) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T value) {
                consumer.accept(value);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
        await(latch);
        Throwable throwable = error.get();
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable != null) {
            throw new PublisherSyncException(throwable);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublisherSyncException(e);
        }
    }

    private interface ItemConsumer<T> {
        void accept(T value);
    }
}
