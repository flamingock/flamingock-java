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
package io.flamingock.cli.config;

import io.flamingock.cli.test.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class SimpleConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadValidMongoConfiguration() throws IOException {
        // Given
        Path configFile = tempDir.resolve("mongo-config.yml");
        Files.write(configFile, TestUtils.getValidMongoYaml().getBytes());

        // When
        FlamingockConfig config = ConfigLoader.loadConfig(configFile.toString());

        // Then
        assertThat(config.getServiceIdentifier()).isEqualTo("test-cli");
        assertThat(config.getAudit()).isNotNull();
        assertThat(config.getAudit().getMongodb()).isNotNull();
        assertThat(config.getAudit().getMongodb().getConnectionString()).isEqualTo("mongodb://localhost:27017");
        assertThat(config.getAudit().getMongodb().getDatabase()).isEqualTo("test");
        assertThat(config.getAudit().getDynamodb()).isNull();
    }

    @Test
    void shouldLoadValidDynamoConfiguration() throws IOException {
        // Given
        Path configFile = tempDir.resolve("dynamo-config.yml");
        Files.write(configFile, TestUtils.getValidDynamoYaml().getBytes());

        // When
        FlamingockConfig config = ConfigLoader.loadConfig(configFile.toString());

        // Then
        assertThat(config.getServiceIdentifier()).isEqualTo("test-cli");
        assertThat(config.getAudit()).isNotNull();
        assertThat(config.getAudit().getDynamodb()).isNotNull();
        assertThat(config.getAudit().getDynamodb().getRegion()).isEqualTo("us-east-1");
        assertThat(config.getAudit().getDynamodb().getEndpoint()).isEqualTo("http://localhost:8000");
        assertThat(config.getAudit().getMongodb()).isNull();
    }

    @Test
    void shouldDetectMongoDbType() throws IOException {
        // Given
        FlamingockConfig config = TestUtils.createMongoConfig();

        // When
        ConfigLoader.DatabaseType type = ConfigLoader.detectDatabaseType(config);

        // Then
        assertThat(type).isEqualTo(ConfigLoader.DatabaseType.MONGODB);
    }

    @Test
    void shouldDetectDynamoDbType() throws IOException {
        // Given
        FlamingockConfig config = TestUtils.createDynamoConfig();

        // When
        ConfigLoader.DatabaseType type = ConfigLoader.detectDatabaseType(config);

        // Then
        assertThat(type).isEqualTo(ConfigLoader.DatabaseType.DYNAMODB);
    }

    @Test
    void shouldThrowExceptionForMissingFile() {
        // When/Then
        assertThatThrownBy(() -> ConfigLoader.loadConfig("non-existent-file.yml"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Configuration file not found");
    }

    @Test
    void shouldThrowExceptionForMultipleDatabases() throws IOException {
        // Given
        String yamlContent = "flamingock:\n" +
                "  audit:\n" +
                "    mongodb:\n" +
                "      connection-string: \"mongodb://localhost:27017\"\n" +
                "      database: \"test\"\n" +
                "    dynamodb:\n" +
                "      region: \"us-east-1\"\n";
        
        Path configFile = tempDir.resolve("multi-db.yml");
        Files.write(configFile, yamlContent.getBytes());

        // When
        FlamingockConfig config = ConfigLoader.loadConfig(configFile.toString());

        // Then
        assertThatThrownBy(() -> ConfigLoader.detectDatabaseType(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiple database configurations found");
    }

    @Test
    void shouldThrowExceptionForNoDatabases() throws IOException {
        // Given
        String yamlContent = "flamingock:\n" +
                "  service-identifier: \"no-db-service\"\n";
        
        Path configFile = tempDir.resolve("no-db.yml");
        Files.write(configFile, yamlContent.getBytes());

        // When
        FlamingockConfig config = ConfigLoader.loadConfig(configFile.toString());

        // Then
        assertThatThrownBy(() -> ConfigLoader.detectDatabaseType(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No audit configuration found");
    }
}