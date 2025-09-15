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
package io.flamingock.community.mongodb.springdata.changes;

import com.mongodb.client.MongoCollection;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@TargetSystem(id = "mongodb")
@Change( id="insert-jorge-document" , order = "003", author = "aperezdieppa")
public class _003_insert_jorge_failed_transactional_non_rollback {

    @Apply
    public void execution(MongoTemplate mongotemplate) {
        MongoCollection<Document> collection = mongotemplate.getCollection("clientCollection");
        collection.insertOne(new Document().append("name", "Jorge"));
        throw new RuntimeException("test");
    }

}
