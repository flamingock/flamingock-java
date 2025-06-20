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

package io.flamingock.importer.mongodb;

import com.mongodb.client.MongoCollection;
import io.flamingock.common.test.mongock.MongockTestHelper;
import io.flamingock.importer.mongock.MongockChangeEntry;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MongoDbMongockTestHelper implements MongockTestHelper {

    private final MongoCollection<Document> changeLogCollection;

    public MongoDbMongockTestHelper(MongoCollection<Document> changeLogCollection) {
        this.changeLogCollection = changeLogCollection;
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
        document.put("state", entry.getState() != null ? entry.getState().toString() : null);
        document.put("type", entry.getType() != null ? entry.getType().toString() : null);
        document.put("changeLogClass", entry.getChangeLogClass());
        document.put("changeSetMethod", entry.getChangeSetMethod());
        document.put("metadata", entry.getMetadata());
        document.put("executionMillis", entry.getExecutionMillis());
        document.put("executionHostname", entry.getExecutionHostname());
        document.put("errorTrace", entry.getErrorTrace());
        document.put("systemChange", entry.getSystemChange());
        document.put("originalTimestamp", entry.getOriginalTimestamp());
        return document;
    }
}