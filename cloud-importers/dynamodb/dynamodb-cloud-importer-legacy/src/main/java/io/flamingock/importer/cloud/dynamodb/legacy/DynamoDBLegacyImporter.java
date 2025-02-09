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

package io.flamingock.importer.cloud.dynamodb.legacy;

import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.importer.cloud.common.Importer;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.util.Collections;
import java.util.List;


public class DynamoDBLegacyImporter implements Importer {
    private final DynamoDbTable<ChangeEntry> sourceTable;
    private List<Dependency> dependencies;

    public DynamoDBLegacyImporter(DynamoDbTable<ChangeEntry> sourceTable) {
        this.sourceTable = sourceTable;
    }

    @Override
    public void initialise(EnvironmentId environmentId, ServiceId serviceId, String jwt, String serverHost) {
        dependencies = Collections.singletonList(
                new Dependency(
                        DynamoDBLegacyImportConfiguration.class,
                        new DynamoDBLegacyImportConfiguration(
                                environmentId, serviceId, jwt, serverHost, sourceTable
                        )
                )
        );
    }

    @Override
    public List<Dependency> getDependencies() {
        return dependencies;
    }
}
