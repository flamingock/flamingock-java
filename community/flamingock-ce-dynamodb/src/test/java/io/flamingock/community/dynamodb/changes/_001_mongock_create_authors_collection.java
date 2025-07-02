package io.flamingock.community.dynamodb.changes;

import io.flamingock.community.dynamodb.changes.common.DynamoDBUtil;
import io.flamingock.community.dynamodb.changes.common.UserEntity;
import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import io.mongock.api.annotations.RollbackExecution;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

import static java.util.Collections.emptyList;


@io.flamingock.api.annotations.ChangeUnit(id = "client-initializer", order = "001", author = "mongock")
@ChangeUnit(id = "client-initializer", order = "001", author = "mongock")
public class _001_mongock_create_authors_collection {

    public final static int INITIAL_CLIENTS = 10;
    public final static String CLIENTS_TABLE_NAME = "mongockClientTable";

    private final DynamoDBUtil dynamoDBUtil = new DynamoDBUtil();

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<UserEntity> table;

    @BeforeExecution
    public void beforeExecution(DynamoDbClient client) {

        dynamoDBUtil.createTable(
                client,
                dynamoDBUtil.getAttributeDefinitions(UserEntity.pkName, UserEntity.skName),
                dynamoDBUtil.getKeySchemas(UserEntity.pkName, UserEntity.skName),
                dynamoDBUtil.getProvisionedThroughput(UserEntity.readCap, UserEntity.writeCap),
                CLIENTS_TABLE_NAME,
                emptyList(),
                emptyList()
        );
        client.describeTable(
                DescribeTableRequest.builder()
                        .tableName(CLIENTS_TABLE_NAME)
                        .build()
        );

        this.enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        this.table = this.enhancedClient.table(CLIENTS_TABLE_NAME, TableSchema.fromBean(UserEntity.class));
    }

    @RollbackBeforeExecution
    public void rollbackBeforeExecution(DynamoDbClient client) {
        client.deleteTable(
                DeleteTableRequest.builder()
                        .tableName(CLIENTS_TABLE_NAME)
                        .build()
        );
    }

    @io.flamingock.api.annotations.Execution
    @Execution
    public void execution(DynamoDbClient client) {
        final TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = TransactWriteItemsEnhancedRequest.builder();

        for (int i = 0; i < INITIAL_CLIENTS; i++)
            writeRequestBuilder.addPutItem(table, new UserEntity("nombre-" + i, "apellido-" + i));

        enhancedClient.transactWriteItems(writeRequestBuilder.build());
    }

    @io.flamingock.api.annotations.RollbackExecution
    @RollbackExecution
    public void rollbackExecution(DynamoDbClient client) {
        final TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = TransactWriteItemsEnhancedRequest.builder();

        for (int i = 0; i < INITIAL_CLIENTS; i++)
            writeRequestBuilder.addDeleteItem(table, new UserEntity("nombre-" + i, "apellido-" + i));

        enhancedClient.transactWriteItems(writeRequestBuilder.build());
    }
}
