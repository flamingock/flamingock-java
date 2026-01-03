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
package io.flamingock.template.mongodb.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container class for MongoDB apply/rollback payload that supports both:
 * <ul>
 *   <li>Single operation format (backward compatible):
 *     <pre>
 *     apply:
 *       type: createCollection
 *       collection: users
 *     </pre>
 *   </li>
 *   <li>Multiple operations format:
 *     <pre>
 *     apply:
 *       operations:
 *         - type: createCollection
 *           collection: users
 *         - type: createIndex
 *           collection: users
 *           parameters:
 *             keys: { name: 1 }
 *     </pre>
 *   </li>
 * </ul>
 */
public class MongoApplyPayload {

    private List<MongoOperation> operations;

    // For backward compatibility
    private String type;
    private String collection;
    private Map<String, Object> parameters;

    /**
     * Returns the list of operations to execute.
     * Handles both formats:
     * <ul>
     *   <li>Multiple: returns the operations list directly</li>
     *   <li>Single: wraps the single operation in a list</li>
     * </ul>
     *
     * @return list of operations to execute, never null
     */
    public List<MongoOperation> getOperations() {
        if (operations != null && !operations.isEmpty()) {
            return operations;
        }
        if (type != null) {
            MongoOperation singleOp = new MongoOperation();
            singleOp.setType(type);
            singleOp.setCollection(collection);
            singleOp.setParameters(parameters != null ? parameters : new HashMap<>());
            return Collections.singletonList(singleOp);
        }
        return Collections.emptyList();
    }

    public void setOperations(List<MongoOperation> operations) {
        this.operations = operations;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        if (operations != null && !operations.isEmpty()) {
            return "MongoApplyPayload{operations=" + operations + "}";
        }
        return "MongoApplyPayload{type='" + type + "', collection='" + collection + "', parameters=" + parameters + "}";
    }
}
