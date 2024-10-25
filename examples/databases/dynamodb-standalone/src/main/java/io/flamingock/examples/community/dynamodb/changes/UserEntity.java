package io.flamingock.examples.community.dynamodb.changes;


import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class UserEntity {

    static final String tableName = "test_table";
    static final String pkName = "PartitionKey";
    static final String skName = "SortKey";
    static final Long readCap = 5L;
    static final Long writeCap = 5L;

    private String partitionKey;
    private String sortKey;
    private String firstName;
    private String lastName;

    public UserEntity(String firstName, String lastName) {
        this.partitionKey = partitionKey(firstName, lastName);
        this.sortKey = sortKey();
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public UserEntity() {
    }

    public String partitionKey(
            String firstName,
            String lastName
    ) {
        return firstName + ' ' + lastName;
    }

    public String sortKey() {
        return "Usuario";
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute(pkName)
    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute(skName)
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    @DynamoDbAttribute("FirstName")
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @DynamoDbAttribute("LastName")
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
