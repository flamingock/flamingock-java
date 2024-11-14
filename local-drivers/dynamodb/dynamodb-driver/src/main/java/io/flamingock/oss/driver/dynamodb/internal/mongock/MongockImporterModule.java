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

package io.flamingock.oss.driver.dynamodb.internal.mongock;

import io.flamingock.core.api.LocalSystemModule;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.oss.driver.dynamodb.internal.entities.AuditEntryEntity;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MongockImporterModule implements LocalSystemModule {
    public static final List<Class<?>> TASK_CLASSES = Collections.singletonList(
            MongockLocalLegacyImporterChangeUnit.class
    );
    private List<Dependency> dependencies;
    private final DynamoDbTable<ChangeEntryDynamoDB> sourceTable;
    private final AuditWriter auditWriter;

    public MongockImporterModule(DynamoDbTable<ChangeEntryDynamoDB> sourceTable, AuditWriter auditWriter) {
        this.sourceTable = sourceTable;
        this.auditWriter = auditWriter;
    }

    @Override
    public void initialise() {
        InternalMongockImporterConfiguration configuration = new InternalMongockImporterConfiguration(
                sourceTable, auditWriter
        );
        dependencies = Collections.singletonList(
                new Dependency(InternalMongockImporterConfiguration.class, configuration)
        );
    }

    @Override
    public String getName() {
        return "dynamodb-local-legacy-importer";
    }

    @Override
    public Collection<Class<?>> getTaskClasses() {
        return TASK_CLASSES;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isBeforeUserStages() {
        return true;
    }
}
