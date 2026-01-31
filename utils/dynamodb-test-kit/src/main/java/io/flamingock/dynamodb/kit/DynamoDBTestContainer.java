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
package io.flamingock.dynamodb.kit;

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
 * 
 * <p>This class provides standardized DynamoDB Local container setup and client creation
 * for testing scenarios. It simplifies the process of setting up isolated DynamoDB
 * instances for comprehensive E2E testing.</p>
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Standardized DynamoDB Local container configuration</li>
 *   <li>Automatic client creation with proper credentials</li>
 *   <li>Integration with Flamingock TestKit infrastructure</li>
 *   <li>Clean container lifecycle management</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * &#64;Container
 * static GenericContainer<?> dynamoContainer = DynamoDBTestContainer.createContainer();
 * 
 * &#64;BeforeEach
 * void setUp() {
 *     DynamoDbClient client = DynamoDBTestContainer.createClient(dynamoContainer);
 *     TestKit testKit = DynamoDBTestKit.create(client, driver);
 * }
 * }</pre>
 */
public class DynamoDBTestContainer {
    
    private static final String DYNAMO_DB_LOCAL_IMAGE = "amazon/dynamodb-local:latest";
    private static final int DYNAMO_DB_PORT = 8000;
    private static final String DUMMY_ACCESS_KEY = "dummy";
    private static final String DUMMY_SECRET_KEY = "dummy";
    
    /**
     * Creates a new DynamoDB Local container with standard configuration.
     * 
     * <p>The container is configured with the latest DynamoDB Local image
     * and exposes the standard DynamoDB port (8000) for client connections.</p>
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
     * <p>The client is pre-configured with dummy credentials (required by DynamoDB Local)
     * and the appropriate endpoint override to connect to the container.</p>
     * 
     * @param container the running DynamoDB container
     * @return configured DynamoDbClient ready for use
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


}