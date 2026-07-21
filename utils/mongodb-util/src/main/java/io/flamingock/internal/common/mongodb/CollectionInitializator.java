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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CollectionInitializator<DOCUMENT_WRAPPER extends DocumentHelper> {

    private final static Logger logger = FlamingockLoggerFactory.getLogger("CollectionInit");


    private final static int INDEX_ENSURE_MAX_TRIES = 3;

    private final List<IndexDefinition> indexDefinitions;
    private final Supplier<DOCUMENT_WRAPPER> documentWrapperSupplier;
    private boolean ensuredCollectionIndex = false;

    private final CollectionHelper<DOCUMENT_WRAPPER> collectionWrapper;

    /**
     * Backward-compatible constructor: a single ascending unique index on {@code uniqueFields}.
     * Retained so existing callers (audit, lock, target-system markers) keep behaving identically.
     */
    public CollectionInitializator(CollectionHelper<DOCUMENT_WRAPPER> collectionWrapper,
                                   Supplier<DOCUMENT_WRAPPER> documentWrapperSupplier,
                                   String[] uniqueFields) {
        this(collectionWrapper,
                documentWrapperSupplier,
                Collections.singletonList(IndexDefinition.uniqueOn(uniqueFields)));
    }

    public CollectionInitializator(CollectionHelper<DOCUMENT_WRAPPER> collectionWrapper,
                                   Supplier<DOCUMENT_WRAPPER> documentWrapperSupplier,
                                   List<IndexDefinition> indexDefinitions) {
        this.collectionWrapper = collectionWrapper;
        this.documentWrapperSupplier = documentWrapperSupplier;
        this.indexDefinitions = new ArrayList<>(indexDefinitions);
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
            cleanResidualKeys();
            createMissingIndexes();
            ensureIndex(tryCounter - 1);
        }
    }

    protected boolean isIndexWrong() {
        return !getResidualKeys().isEmpty() || !getMissingSpecs().isEmpty();
    }

    protected void cleanResidualKeys() {
        logger.debug("Removing residual indexes for collection [{}]", getCollectionName());
        getResidualKeys().stream()
                .peek(index -> logger.debug("Removed residual index [{}] for collection [{}]", index.toString(), getCollectionName()))
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
        if (isIdIndex(index)) {
            return false;
        }
        boolean matchesAnySpec = indexDefinitions.stream().anyMatch(spec -> matchesSpec(index, spec));
        // Rule (a) — legacy behavior, unchanged for the single-unique callers: a unique, non-_id index
        // that matches none of the desired specs is residual and must be dropped/recreated.
        if (isUniqueIndex(index) && !matchesAnySpec) {
            return true;
        }
        // Rule (b) — only fires for specs carrying an explicit name (i.e. never for the audit/lock specs,
        // whose name is null): an existing index reusing a requested name but not matching that spec is a
        // stale/mis-defined index that must be dropped so the correct one can be recreated.
        String indexName = index.get("name") != null ? index.get("name").toString() : null;
        if (indexName != null) {
            for (IndexDefinition spec : indexDefinitions) {
                if (spec.getName() != null && spec.getName().equals(indexName) && !matchesSpec(index, spec)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isIdIndex(DocumentHelper index) {
        return index.getWithWrapper("key").get("_id") != null;
    }

    protected List<IndexDefinition> getMissingSpecs() {
        List<DocumentHelper> existing = StreamSupport.stream(listIndexes().spliterator(), false)
                .collect(Collectors.toList());
        return indexDefinitions.stream()
                .filter(spec -> existing.stream().noneMatch(index -> matchesSpec(index, spec)))
                .collect(Collectors.toList());
    }

    protected void createMissingIndexes() {
        for (IndexDefinition spec : getMissingSpecs()) {
            collectionWrapper.createIndex(
                    buildKeyDocument(spec),
                    spec.getName(),
                    spec.isUnique(),
                    buildPartialFilterDocument(spec));
            logger.debug("Index {} in collection [{}] was created", spec.getName(), getCollectionName());
        }
    }

    protected boolean matchesSpec(DocumentHelper index, IndexDefinition spec) {
        final DocumentHelper key = index.getWithWrapper("key");
        boolean keyContainsAllFields = spec.getKeys().keySet().stream().allMatch(field -> key.get(field) != null);
        boolean onlyTheseFields = key.size() == spec.getKeys().size();
        if (!keyContainsAllFields || !onlyTheseFields) {
            return false;
        }
        if (isUniqueIndex(index) != spec.isUnique()) {
            return false;
        }
        return partialFilterMatches(index, spec);
    }

    private boolean partialFilterMatches(DocumentHelper index, IndexDefinition spec) {
        boolean indexHasPartial = index.containsKey("partialFilterExpression");
        if (indexHasPartial != spec.hasPartialFilter()) {
            return false;
        }
        if (!spec.hasPartialFilter()) {
            return true;
        }
        DocumentHelper stored = index.getWithWrapper("partialFilterExpression");
        if (stored.size() != spec.getPartialFilterExpression().size()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : spec.getPartialFilterExpression().entrySet()) {
            if (!Objects.equals(stored.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    protected boolean isUniqueIndex(DocumentHelper index) {
        return index.getBoolean("unique", false);// checks it'unique
    }

    private String getCollectionName() {
        return collectionWrapper.getCollectionName();
    }

    protected DOCUMENT_WRAPPER buildKeyDocument(IndexDefinition spec) {
        final DOCUMENT_WRAPPER keyDocument = documentWrapperSupplier.get();
        spec.getKeys().forEach(keyDocument::append);
        return keyDocument;
    }

    protected DOCUMENT_WRAPPER buildPartialFilterDocument(IndexDefinition spec) {
        if (!spec.hasPartialFilter()) {
            return null;
        }
        final DOCUMENT_WRAPPER partialDocument = documentWrapperSupplier.get();
        spec.getPartialFilterExpression().forEach(partialDocument::append);
        return partialDocument;
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
