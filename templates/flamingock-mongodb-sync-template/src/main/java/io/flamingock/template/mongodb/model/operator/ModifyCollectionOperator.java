/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

public class ModifyCollectionOperator extends MongoOperator {

    public ModifyCollectionOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, false);
    }

    @Override
    protected void applyInternal(ClientSession clientSession) {
        Document command = new Document("collMod", op.getCollection());
        if (op.getValidator() != null) {
            command.append("validator", op.getValidator());
        }
        if (op.getValidationLevel() != null) {
            command.append("validationLevel", op.getValidationLevel());
        }
        if (op.getValidationAction() != null) {
            command.append("validationAction", op.getValidationAction());
        }
        mongoDatabase.runCommand(command);
    }
}
