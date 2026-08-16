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
package io.flamingock.internal.common.mongodb;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.flamingock.internal.common.core.error.FlamingockException;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MongoDBReactiveCollectionHelper implements CollectionHelper<MongoDBDocumentHelper> {

    private final MongoCollection<Document> collection;

    public MongoDBReactiveCollectionHelper(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public String getCollectionName() {
        return collection.getNamespace().getCollectionName();
    }

    @Override
    public Iterable<DocumentHelper> listIndexes() {
        List<Document> indexes = collect(collection.listIndexes());
        return indexes.stream().map(MongoDBDocumentHelper::new).collect(Collectors.toList());
    }

    @Override
    public String createIndex(MongoDBDocumentHelper keyDocument,
                              String name,
                              boolean unique,
                              MongoDBDocumentHelper partialFilterExpression) {
        IndexOptions options = new IndexOptions().unique(unique);
        if (name != null) {
            options.name(name);
        }
        if (partialFilterExpression != null) {
            options.partialFilterExpression(partialFilterExpression.getDocument());
        }
        return first(collection.createIndex(keyDocument.getDocument(), options));
    }

    @Override
    public void dropIndex(String indexName) {
        complete(collection.dropIndex(indexName));
    }

    @Override
    public void deleteMany(MongoDBDocumentHelper documentWrapper) {
        first(collection.deleteMany(documentWrapper.getDocument()));
    }

    private static <T> T first(Publisher<T> publisher) {
        List<T> values = collect(publisher);
        return values.isEmpty() ? null : values.get(0);
    }

    private static <T> List<T> collect(Publisher<T> publisher) {
        List<T> values = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T value) {
                values.add(value);
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
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlamingockException(e);
        }
        Throwable throwable = error.get();
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable != null) {
            throw new FlamingockException(throwable);
        }
        return values;
    }

    private static void complete(Publisher<?> publisher) {
        collect(publisher);
    }
}
