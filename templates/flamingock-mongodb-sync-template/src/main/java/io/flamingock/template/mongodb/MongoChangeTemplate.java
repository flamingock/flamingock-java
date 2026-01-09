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
package io.flamingock.template.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Nullable;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.template.mongodb.model.MongoApplyPayload;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Change Template for executing declarative MongoDB operations defined in YAML.
 *
 * <h2>Apply Behavior</h2>
 * <p>The {@link #apply} method executes all operations defined in the payload sequentially.
 * The behavior differs based on the transactional mode:</p>
 *
 * <h3>Transactional Mode ({@code transactional: true})</h3>
 * <ul>
 *   <li>All operations execute within a MongoDB transaction (ClientSession)</li>
 *   <li>If any operation fails, MongoDB automatically rolls back the entire transaction</li>
 *   <li>Per-operation rollback definitions are NOT executed (the transaction handles atomicity)</li>
 * </ul>
 *
 * <h3>Non-Transactional Mode ({@code transactional: false})</h3>
 * <ul>
 *   <li>Each operation executes independently without a transaction</li>
 *   <li>Successfully completed operations are tracked</li>
 *   <li>If operation N fails, auto-rollback is triggered for operations 1 to N-1</li>
 *   <li>Auto-rollback executes per-operation rollbacks in reverse order</li>
 *   <li>Operations without a rollback definition are skipped during auto-rollback</li>
 *   <li>After auto-rollback completes, the original exception is re-thrown</li>
 * </ul>
 *
 * <h2>YAML Example</h2>
 * <pre>{@code
 * id: create-orders-collection
 * transactional: false
 * template: MongoChangeTemplate
 * targetSystem:
 *   id: "mongodb"
 * apply:
 *   operations:
 *     - type: createCollection
 *       collection: orders
 *       rollback:
 *         type: dropCollection
 *         collection: orders
 *
 *     - type: insert
 *       collection: orders
 *       parameters:
 *         documents:
 *           - orderId: "ORD-001"
 *             customer: "John Doe"
 *       rollback:
 *         type: delete
 *         collection: orders
 *         parameters:
 *           filter: {}
 * }</pre>
 *
 * @see MongoOperation
 * @see MongoApplyPayload
 */
public class MongoChangeTemplate extends AbstractChangeTemplate<Void, MongoApplyPayload, MongoApplyPayload> {

    private static final Logger logger = FlamingockLoggerFactory.getLogger(MongoChangeTemplate.class);

    public MongoChangeTemplate() {
        super(MongoOperation.class);
    }

    @Apply
    public void apply(MongoDatabase db, @Nullable ClientSession clientSession) {
        if (this.isTransactional && clientSession == null) {
            throw new IllegalArgumentException(String.format("Transactional change[%s] requires transactional ecosystem with ClientSession", changeId));
        }
        executeOperationsWithAutoRollback(db, applyPayload, clientSession);
    }

    @Rollback
    public void rollback(MongoDatabase db, @Nullable ClientSession clientSession) {
        if (this.isTransactional && clientSession == null) {
            throw new IllegalArgumentException(String.format("Transactional change[%s] requires transactional ecosystem with ClientSession", changeId));
        }
        executeRollbackOperations(db, applyPayload, clientSession);
    }

    private void executeOperationsWithAutoRollback(MongoDatabase db, MongoApplyPayload payload, ClientSession clientSession) {
        if (payload == null) {
            return;
        }

        List<MongoOperation> operations = payload.getOperations();

        // Transactional, MongoDB handles rollback
        if (this.isTransactional && clientSession != null) {
            for (MongoOperation op : operations) {
                op.getOperator(db).apply(clientSession);
            }
            return;
        }

        // Non-transactional: auto-rollback on failure
        List<MongoOperation> successfulOps = new ArrayList<>();
        for (MongoOperation op : operations) {
            try {
                op.getOperator(db).apply(clientSession);
                successfulOps.add(op);
            } catch (Exception e) {
                rollbackSuccessfulOperations(db, successfulOps, clientSession);
                throw e;
            }
        }
    }

    private void rollbackSuccessfulOperations(MongoDatabase db, List<MongoOperation> successfulOps, ClientSession clientSession) {
        for (int i = successfulOps.size() - 1; i >= 0; i--) {
            MongoOperation op = successfulOps.get(i);
            if (op.getRollback() != null) {
                try {
                    op.getRollback().getOperator(db).apply(clientSession);
                } catch (Exception rollbackEx) {
                    logger.warn("Rollback failed for operation: {}", op, rollbackEx);
                }
            }
        }
    }

    private void executeRollbackOperations(MongoDatabase db, MongoApplyPayload payload, ClientSession clientSession) {
        if (payload == null) {
            return;
        }

        List<MongoOperation> operations = payload.getOperations();
        for (int i = operations.size() - 1; i >= 0; i--) {
            MongoOperation op = operations.get(i);
            if (op.getRollback() != null) {
                op.getRollback().getOperator(db).apply(clientSession);
            }
        }
    }

}