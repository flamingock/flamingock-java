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

package io.flamingock.oss.driver.mongodb.springdata.v4.internal;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import io.flamingock.core.engine.audit.writer.AuditStageStatus;
import io.flamingock.core.util.Result;
import io.flamingock.oss.driver.common.mongodb.CollectionInitializator;
import io.flamingock.oss.driver.common.mongodb.MongoDBAuditMapper;
import io.flamingock.oss.driver.mongodb.springdata.v4.internal.mongodb.SpringDataMongoV4CollectionWrapper;
import io.flamingock.oss.driver.mongodb.springdata.v4.internal.mongodb.SpringDataMongoV4DocumentWrapper;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.mongodb.ReadWriteConfiguration;
import io.flamingock.core.engine.local.Auditor;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.LinkedList;
import java.util.stream.Collectors;

import static io.flamingock.community.internal.AuditEntryField.KEY_AUTHOR;
import static io.flamingock.community.internal.AuditEntryField.KEY_CHANGE_ID;
import static io.flamingock.community.internal.AuditEntryField.KEY_EXECUTION_ID;

public class SpringDataMongoV4Auditor implements Auditor {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringDataMongoV4Auditor.class);

    private final MongoCollection<Document> collection;
    private final MongoDBAuditMapper<SpringDataMongoV4DocumentWrapper> mapper = new MongoDBAuditMapper<>(() -> new SpringDataMongoV4DocumentWrapper(new Document()));

    SpringDataMongoV4Auditor(MongoTemplate mongoTemplate,
                      String collectionName,
                      ReadWriteConfiguration readWriteConfiguration) {
        this.collection = mongoTemplate.getCollection(collectionName)
                .withReadConcern(readWriteConfiguration.getReadConcern())
                .withReadPreference(readWriteConfiguration.getReadPreference())
                .withWriteConcern(readWriteConfiguration.getWriteConcern());
    }

    protected void initialize(boolean indexCreation) {
        CollectionInitializator<SpringDataMongoV4DocumentWrapper> initializer = new CollectionInitializator<>(
                new SpringDataMongoV4CollectionWrapper(collection),
                () -> new SpringDataMongoV4DocumentWrapper(new Document()),
                new String[]{KEY_EXECUTION_ID, KEY_AUTHOR, KEY_CHANGE_ID}
        );
        if(indexCreation) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }

    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {

        Bson filter = Filters.and(
                Filters.eq(KEY_EXECUTION_ID, auditEntry.getExecutionId()),
                Filters.eq(KEY_CHANGE_ID, auditEntry.getTaskId()),
                Filters.eq(KEY_AUTHOR, auditEntry.getAuthor())
        );

        Document entryDocument = mapper.toDocument(auditEntry).getDocument();

        UpdateResult result = collection.replaceOne(filter, entryDocument, new ReplaceOptions().upsert(true));
        logger.debug("SaveOrUpdate[{}] with result" +
                "\n[upsertId:{}, matches: {}, modifies: {}, acknowledged: {}]", auditEntry, result.getUpsertedId(), result.getMatchedCount(), result.getModifiedCount(), result.wasAcknowledged());

        return Result.OK();
    }


    @Override
    public AuditStageStatus getAuditStageStatus() {
        AuditStageStatus.EntryBuilder builder = AuditStageStatus.entryBuilder();
        collection.find()
                .into(new LinkedList<>())
                .stream()
                .map(SpringDataMongoV4DocumentWrapper::new)
                .map(mapper::fromDocument)
                .collect(Collectors.toList())
                .forEach(builder::addEntry);
        return builder.build();

    }
}
