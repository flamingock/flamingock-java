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

import io.flamingock.core.context.ContextInjectable;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.core.context.DependencyInjectable;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import io.flamingock.core.preview.PreviewMethod;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.preview.builder.PreviewTaskBuilder;
import io.flamingock.core.context.Dependency;
import io.flamingock.core.system.SystemModule;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.util.Collections;
import java.util.List;

public class MongockImporterModule implements SystemModule {


    private static final List<CodePreviewChangeUnit> MONGOCK_CHANGE_UNITS = Collections.singletonList(
            PreviewTaskBuilder.getCodeBuilder()
                    .setId("mongock-local-legacy-importer-dynamodb")
                    .setOrder("1")
                    .setSourceClassPath(MongockImporterChangeUnit.class.getName())
                    .setExecutionMethod(new PreviewMethod("execution", Collections.singletonList(
                            InternalMongockImporterConfiguration.class.getName())))
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setNewChangeUnit(true)
                    .setSystem(true)
                    .build()
    );
    private final DynamoDbTable<ChangeEntryDynamoDB> sourceTable;
    private final AuditWriter auditWriter;

    public MongockImporterModule(DynamoDbTable<ChangeEntryDynamoDB> sourceTable, AuditWriter auditWriter) {
        this.sourceTable = sourceTable;
        this.auditWriter = auditWriter;
    }

    @Override
    public void initialize(ContextResolver dependencyContext) {

    }


    @Override
    public void contributeToContext(ContextInjectable dependencyInjectable) {
        InternalMongockImporterConfiguration configuration = new InternalMongockImporterConfiguration(
                sourceTable, auditWriter
        );
        dependencyInjectable.addDependency(new Dependency(InternalMongockImporterConfiguration.class, configuration));
    }

    @Override
    public PreviewStage getStage() {
        return PreviewStage.builder()
                .setName("dynamodb-local-legacy-importer")
                .setDescription("DynamoDB importer from Mongock")
                .setChanges(MONGOCK_CHANGE_UNITS)
                .build();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean isBeforeUserStages() {
        return true;
    }
}
