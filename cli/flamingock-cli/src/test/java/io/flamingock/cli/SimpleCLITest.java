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
package io.flamingock.cli;

import io.flamingock.cli.config.ConfigLoader;
import io.flamingock.cli.config.FlamingockConfig;
import io.flamingock.cli.test.TestUtils;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.*;

class SimpleCLITest {

    @Test
    void shouldCreateCommandLine() {
        // When
        CommandLine cmd = new CommandLine(new FlamingockCli());

        // Then
        assertThat(cmd).isNotNull();
        assertThat(cmd.getCommandName()).isEqualTo("flamingock");
    }

    @Test
    void shouldHaveAuditSubcommands() {
        // When
        CommandLine cmd = new CommandLine(new FlamingockCli());

        // Then
        assertThat(cmd.getSubcommands()).containsKey("audit");
        CommandLine auditCmd = cmd.getSubcommands().get("audit");
        assertThat(auditCmd.getSubcommands()).containsKeys("list", "fix");
    }


    @Test
    void shouldDetectDatabaseTypes() {
        // Given
        FlamingockConfig mongoConfig = TestUtils.createMongoConfig();
        FlamingockConfig dynamoConfig = TestUtils.createDynamoConfig();
        FlamingockConfig couchbaseConfig = TestUtils.createCouchbaseConfig();

        // When/Then
        assertThat(ConfigLoader.detectDatabaseType(mongoConfig))
                .isEqualTo(ConfigLoader.DatabaseType.MONGODB);

        assertThat(ConfigLoader.detectDatabaseType(dynamoConfig))
                .isEqualTo(ConfigLoader.DatabaseType.DYNAMODB);

        assertThat(ConfigLoader.detectDatabaseType(couchbaseConfig))
                .isEqualTo(ConfigLoader.DatabaseType.COUCHBASE);
    }

    @Test
    void shouldCreateTestConfigs() {
        // When
        FlamingockConfig mongoConfig = TestUtils.createMongoConfig();
        FlamingockConfig dynamoConfig = TestUtils.createDynamoConfig();
        FlamingockConfig couchbaseoConfig = TestUtils.createCouchbaseConfig();

        // Then
        assertThat(mongoConfig).isNotNull();
        assertThat(mongoConfig.getAudit().getMongodb()).isNotNull();
        assertThat(mongoConfig.getAudit().getMongodb().getDatabase()).isEqualTo("test-db");

        assertThat(dynamoConfig).isNotNull();
        assertThat(dynamoConfig.getAudit().getDynamodb()).isNotNull();
        assertThat(dynamoConfig.getAudit().getDynamodb().getRegion()).isEqualTo("us-east-1");

        assertThat(couchbaseoConfig).isNotNull();
        assertThat(couchbaseoConfig.getAudit().getCouchbase()).isNotNull();
        assertThat(couchbaseoConfig.getAudit().getCouchbase().getBucketName()).isEqualTo("test-bucket");
    }
}
