package io.flamingock.targetsystem.dynamodb;

import io.flamingock.api.external.ExternalSystem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public interface DynamoDBExternalSystem extends ExternalSystem {
    DynamoDbClient getClient();
}
