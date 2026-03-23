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
package io.flamingock.targetsystem.mongodb.springdata.changes.unhappypath;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@TargetSystem( id = "mongodb-ts")
@Change(id = "insert-clients", author = "aperezdieppa")
public class _002__UnhappyInsertClientsChange {

    @Apply
    public void apply(@NonLockGuarded MongoTemplate mongoTemplate
    ) {
        mongoTemplate.insert(new Document().append("name", "Should Have Been Rolled Back"), "clientCollection");
        throw new RuntimeException("Intended exception");
    }
}
