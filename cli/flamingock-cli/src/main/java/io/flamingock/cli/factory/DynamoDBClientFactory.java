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
package io.flamingock.cli.factory;

import io.flamingock.cli.config.DatabaseConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

public class DynamoDBClientFactory {

    public static DynamoDbClient createDynamoDBClient(DatabaseConfig.DynamoDBConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DynamoDB configuration is required");
        }

        if (config.getRegion() == null) {
            throw new IllegalArgumentException("DynamoDB region is required");
        }

        try {
            DynamoDbClientBuilder builder = DynamoDbClient.builder()
                    .region(Region.of(config.getRegion()));

            // Set endpoint if provided (for local DynamoDB)
            if (config.getEndpoint() != null) {
                builder.endpointOverride(URI.create(config.getEndpoint()));
            }

            // Set credentials if provided, otherwise use default provider chain
            if (config.getAccessKey() != null && config.getSecretKey() != null) {
                AwsCredentials credentials = AwsBasicCredentials.create(
                        config.getAccessKey(),
                        config.getSecretKey()
                );
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
            }

            DynamoDbClient client = builder.build();

            // Test the connection by listing tables
            client.listTables();

            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DynamoDB client: " + e.getMessage(), e);
        }
    }
}
