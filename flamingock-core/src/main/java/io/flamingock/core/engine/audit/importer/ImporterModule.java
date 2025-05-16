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

package io.flamingock.core.engine.audit.importer;

import io.flamingock.core.context.ContextInjectable;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.core.context.DependencyInjectable;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.audit.importer.changeunit.FlamingockLocalImporterChangeUnit;
import io.flamingock.core.engine.audit.importer.changeunit.MongockImporterChangeUnit;
import io.flamingock.core.pipeline.PipelineDescriptor;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import io.flamingock.core.preview.PreviewMethod;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.preview.builder.PreviewTaskBuilder;
import io.flamingock.core.context.Dependency;
import io.flamingock.core.system.SystemModule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ImporterModule implements SystemModule {

    public static final String FROM_MONGOCK_NAME = "from-mongock-importer";
    public static final String FROM_FLAMINGOCK_LITE_NAME = "from-flamingock-local-importer";


    private final List<CodePreviewChangeUnit> fromMongockChangeUnits = Collections.singletonList(
            PreviewTaskBuilder.getCodeBuilder()
                    .setId(MongockImporterChangeUnit.IMPORTER_FROM_MONGOCK)
                    .setOrder("1")
                    .setSourceClassPath(MongockImporterChangeUnit.class.getName())
                    .setExecutionMethod(new PreviewMethod("execution", Arrays.asList(
                            ImporterReader.class.getName(),
                            AuditWriter.class.getName(),
                            PipelineDescriptor.class.getName())))
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setNewChangeUnit(true)
                    .setSystem(true)
                    .build()
    );


    private final List<CodePreviewChangeUnit> fromFlamingockChangeUnits = Collections.singletonList(
            PreviewTaskBuilder.getCodeBuilder()
                    .setId(FlamingockLocalImporterChangeUnit.IMPORTER_FROM_FLAMINGOCK_LOCAL)
                    .setOrder("2")
                    .setSourceClassPath(MongockImporterChangeUnit.class.getName())
                    .setExecutionMethod(new PreviewMethod("execution", Arrays.asList(
                            ImporterReader.class.getName(),
                            AuditWriter.class.getName(),
                            PipelineDescriptor.class.getName())))
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setNewChangeUnit(true)
                    .setSystem(true)
                    .build()
    );
    private static final String FROM_MONGOCK_DESC = "Importer from Mongock";
    private static final String FROM_FLAMINGOCK_LITE_DESC = "Importer from Flamingock lite";

    private ImporterReader importReader;
    private boolean fromMongock;

    public ImporterModule() {
    }

    public ImporterModule(ImporterReader importerReader) {
        this.fromMongock = importerReader.isFromMongock();
        this.importReader = importerReader;
    }

    @Override
    public void initialize(ContextResolver dependencyContext) {
    }

    @Override
    public void contributeToContext(ContextInjectable contextInjectable) {
        contextInjectable.addDependency(new Dependency(ImporterReader.class, importReader));
    }

    @Override
    public PreviewStage getStage() {
        return PreviewStage.builder()
                .setName(fromMongock ? FROM_MONGOCK_NAME : FROM_FLAMINGOCK_LITE_NAME)
                .setDescription(fromMongock ? FROM_MONGOCK_DESC : FROM_FLAMINGOCK_LITE_DESC)
                .setChanges(fromMongock ? fromMongockChangeUnits : fromFlamingockChangeUnits)
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
