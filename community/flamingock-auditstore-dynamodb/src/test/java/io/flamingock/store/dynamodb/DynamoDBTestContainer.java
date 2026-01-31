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
package io.flamingock.store.dynamodb;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * Utility class for creating and configuring DynamoDB TestContainers.
 * Provides standardized DynamoDB Local container setup and client creation.
 */
public class DynamoDBTestContainer {

    private static final String DYNAMO_DB_LOCAL_IMAGE = "amazon/dynamodb-local:latest";
    private static final int DYNAMO_DB_PORT = 8000;
    private static final String DUMMY_ACCESS_KEY = "dummy";
    private static final String DUMMY_SECRET_KEY = "dummy";

    /**
     * Creates a new DynamoDB Local container with standard configuration.
     *
     * @return configured GenericContainer for DynamoDB Local
     */
    public static GenericContainer<?> createContainer() {
        return new GenericContainer<>(DockerImageName.parse(DYNAMO_DB_LOCAL_IMAGE))
                .withExposedPorts(DYNAMO_DB_PORT);
    }

    /**
     * Creates a DynamoDB client configured to connect to the provided container.
     *
     * @param container the running DynamoDB container
     * @return configured DynamoDbClient
     */
    public static DynamoDbClient createClient(GenericContainer<?> container) {
        String endpoint = String.format("http://%s:%d",
                container.getHost(),
                container.getMappedPort(DYNAMO_DB_PORT));

        return DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(DUMMY_ACCESS_KEY, DUMMY_SECRET_KEY)
                        )
                )
                .build();
    }

    /**
     * Creates a DynamoDB test helper for the provided client.
     *
     * @param client the DynamoDB client
     * @return configured DynamoDBTestHelper
     */
    public static DynamoDBTestHelper createTestHelper(DynamoDbClient client) {
        return new DynamoDBTestHelper(client);
    }
}
