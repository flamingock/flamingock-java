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
package io.flamingock.importer.mongock.mongodb;

import com.mongodb.client.MongoCollection;
import io.flamingock.common.test.mongock.MongockChangeEntry;
import io.flamingock.common.test.mongock.MongockTestHelper;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MongoDBMongockTestHelper implements MongockTestHelper {

    private final MongoCollection<Document> changeLogCollection;

    public MongoDBMongockTestHelper(MongoCollection<Document> changeLogCollection) {
        this.changeLogCollection = changeLogCollection;
    }

    @Override
    public void reset() {
        changeLogCollection.drop();
    }

    public void write(MongockChangeEntry entry) {
        changeLogCollection.insertOne(convertToDocument(entry));
    }

    public int writeAll(List<MongockChangeEntry> entries) {
        List<Document> documents = new ArrayList<>(entries.size());
        for (MongockChangeEntry entry : entries) {
            documents.add(convertToDocument(entry));
        }
        changeLogCollection.insertMany(documents);
        return documents.size();
    }

    private Document convertToDocument(MongockChangeEntry entry) {
        Document document = new Document();
        document.put("executionId", entry.getExecutionId());
        document.put("changeId", entry.getChangeId());
        document.put("author", entry.getAuthor());
        document.put("timestamp", entry.getTimestamp());
        putIfNotNull(document, "state", entry.getState() != null ? entry.getState().toString() : null);
        putIfNotNull(document, "type", entry.getType() != null ? entry.getType().toString() : null);
        putIfNotNull(document, "changeLogClass", entry.getChangeLogClass());
        putIfNotNull(document, "changeSetMethod", entry.getChangeSetMethod());
        putIfNotNull(document, "metadata", entry.getMetadata());
        document.put("executionMillis", entry.getExecutionMillis());
        putIfNotNull(document, "executionHostname", entry.getExecutionHostname());
        putIfNotNull(document, "errorTrace", entry.getErrorTrace());
        putIfNotNull(document, "systemChange", entry.getSystemChange());
        putIfNotNull(document, "originalTimestamp", entry.getOriginalTimestamp());
        return document;
    }

    private void putIfNotNull(Document document, String key, Object value) {
        if (value != null) {
            document.put(key, value);
        }
    }
}
