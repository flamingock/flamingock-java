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

package io.flamingock.importer.cloud.dynamodb.local;

import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.importer.cloud.common.Importer;
import io.flamingock.importer.cloud.dynamodb.local.entities.AuditEntryEntity;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.ArrayList;
import java.util.List;


public class DynamoDBLocalImporter implements Importer {

    public final static String DEFAULT_FLAMINGOCK_REPOSITORY_NAME = AuditEntryEntity.AUDIT_LOG_TABLE_NAME;

    private final DynamoDbTable<AuditEntryEntity> sourceTable;
    private List<Dependency> dependencies;

    public DynamoDBLocalImporter(DynamoDbEnhancedClient client) {
        this.sourceTable = client.table(DEFAULT_FLAMINGOCK_REPOSITORY_NAME, TableSchema.fromBean(AuditEntryEntity.class));
    }

    public DynamoDBLocalImporter(DynamoDbEnhancedClient client, String overridenChangelogTable) {
        this.sourceTable = client.table(overridenChangelogTable, TableSchema.fromBean(AuditEntryEntity.class));
    }

    @Override
    public void initialise(EnvironmentId environmentId, ServiceId serviceId, String jwt, String serverHost) {
        dependencies = new ArrayList<>();
        dependencies.add(
                new Dependency(
                        DynamoDBLocalImportConfiguration.class,
                        new DynamoDBLocalImportConfiguration(
                                environmentId, serviceId, jwt, serverHost
                        )
                )
        );
        dependencies.add(
                new Dependency(
                        DynamoDBLocalAuditReader.class,
                        new DynamoDBLocalAuditReader(sourceTable)
                )
        );
    }

    @Override
    public List<Dependency> getDependencies() {
        return dependencies;
    }
}
