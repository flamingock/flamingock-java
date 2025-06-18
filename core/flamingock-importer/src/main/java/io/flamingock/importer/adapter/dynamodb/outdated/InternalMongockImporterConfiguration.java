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

package io.flamingock.importer.adapter.dynamodb.outdated;

import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.internal.common.core.audit.AuditWriter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

@NonLockGuarded
public class InternalMongockImporterConfiguration {

    private final DynamoDbTable<ChangeEntryDynamoDB> sourceTable;

    private final AuditWriter auditWriter;

    public InternalMongockImporterConfiguration(DynamoDbTable<ChangeEntryDynamoDB> sourceTable, AuditWriter auditWriter) {
        this.sourceTable = sourceTable;
        this.auditWriter = auditWriter;
    }

    public DynamoDbTable<ChangeEntryDynamoDB> getSourceTable() {
        return sourceTable;
    }

    public AuditWriter getAuditWriter() {
        return auditWriter;
    }

    @Override
    public String toString() {
        return "MongockLegacyImporterConfiguration{" + "dynamoDBTable=" + sourceTable.toString() +
                ", auditWriter=" + auditWriter.toString() +
                '}';
    }
}
