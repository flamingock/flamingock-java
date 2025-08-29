/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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


import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CollectionInitializator<DOCUMENT_WRAPPER extends DocumentHelper> {

    private final static Logger logger = FlamingockLoggerFactory.getLogger("CollectionInit");


    private final static int INDEX_ENSURE_MAX_TRIES = 3;

    private final String[] uniqueFields;
    private final Supplier<DOCUMENT_WRAPPER> documentWrapperSupplier;
    private boolean ensuredCollectionIndex = false;

    private final CollectionHelper<DOCUMENT_WRAPPER> collectionWrapper;

    public CollectionInitializator(CollectionHelper<DOCUMENT_WRAPPER> collectionWrapper,
                                   Supplier<DOCUMENT_WRAPPER> documentWrapperSupplier,
                                   String[] uniqueFields) {
        this.collectionWrapper = collectionWrapper;
        this.documentWrapperSupplier = documentWrapperSupplier;
        this.uniqueFields = uniqueFields;
    }


    public synchronized void initialize() {
        if (!this.ensuredCollectionIndex) {
            ensureIndex(INDEX_ENSURE_MAX_TRIES);
            this.ensuredCollectionIndex = true;
        }
    }

    public void justValidateCollection() {
        if (isIndexWrong()) {
            throw new RuntimeException("Index creation not allowed, but not created or wrongly created for collection " + getCollectionName());
        }
    }

    private void ensureIndex(int tryCounter) {
        if (tryCounter <= 0) {
            throw new RuntimeException("Max tries " + INDEX_ENSURE_MAX_TRIES + " index  creation");
        }
        if (isIndexWrong()) {
            cleanResidualUniqueKeys();
            if (indexCreatedNotRequired()) {
                createRequiredUniqueIndex();
            }
            ensureIndex(tryCounter - 1);
        }
    }

    protected boolean isIndexWrong() {
        return !getResidualKeys().isEmpty() || indexCreatedNotRequired();
    }

    protected void cleanResidualUniqueKeys() {
        logger.debug("Removing residual uniqueKeys for collection [{}]", getCollectionName());
        getResidualKeys().stream()
                .peek(index -> logger.debug("Removed residual uniqueKey [{}] for collection [{}]", index.toString(), getCollectionName()))
                .forEach(this::dropIndex);
    }

    private List<DocumentHelper> getResidualKeys() {
        return StreamSupport.stream(listIndexes().spliterator(), false)
                .filter(this::doesNeedToBeRemoved)
                .collect(Collectors.toList());
    }

    private Iterable<DocumentHelper> listIndexes() {
        return collectionWrapper.listIndexes();
    }

    protected boolean doesNeedToBeRemoved(DocumentHelper index) {
        return !isIdIndex(index) && isUniqueIndex(index) && !isRightIndex(index);
    }

    protected boolean isIdIndex(DocumentHelper index) {
        return index.getWithWrapper("key").get("_id") != null;
    }

    protected boolean indexCreatedNotRequired() {
        return StreamSupport.stream(
                        collectionWrapper.listIndexes().spliterator(),
                        false)
                .noneMatch(this::isRightIndex);
    }

    protected void createRequiredUniqueIndex() {
        collectionWrapper.createUniqueIndex(getIndexDocument(uniqueFields));
        logger.debug("Index in collection [{}] was recreated", getCollectionName());
    }

    protected boolean isRightIndex(DocumentHelper index) {
        final DocumentHelper key = index.getWithWrapper("key");
        boolean keyContainsAllFields = Stream.of(uniqueFields).allMatch(uniqueField -> key.get(uniqueField) != null);
        boolean onlyTheseFields = key.size() == uniqueFields.length;
        return keyContainsAllFields && onlyTheseFields && isUniqueIndex(index);
    }

    protected boolean isUniqueIndex(DocumentHelper index) {
        return index.getBoolean("unique", false);// checks it'unique
    }

    private String getCollectionName() {
        return collectionWrapper.getCollectionName();
    }

    protected DOCUMENT_WRAPPER getIndexDocument(String[] uniqueFields) {
        final DOCUMENT_WRAPPER indexDocument = documentWrapperSupplier.get();
        Stream.of(uniqueFields).forEach(field -> indexDocument.append(field, 1));
        return indexDocument;
    }

    protected void dropIndex(DocumentHelper index) {
        collectionWrapper.dropIndex(index.get("name").toString());
    }


    /**
     * Only for testing
     */
    public void deleteAll() {
        collectionWrapper.deleteMany(documentWrapperSupplier.get());
    }
}
