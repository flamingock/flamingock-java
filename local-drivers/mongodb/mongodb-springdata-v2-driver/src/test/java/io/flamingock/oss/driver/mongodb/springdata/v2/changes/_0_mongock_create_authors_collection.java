package io.flamingock.oss.driver.mongodb.springdata.v2.changes;

import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.stream.Collectors;
import java.util.stream.IntStream;


@ChangeUnit(id = "create-author-collection", order = "0", author = "mongock")
public class _0_mongock_create_authors_collection {

    public final static int INITIAL_CLIENTS = 10;
    public final static String CLIENTS_COLLECTION_NAME = "mongockClientCollection";

    @BeforeExecution
    public void beforeExecution(MongoTemplate mongoTemplate) {

        mongoTemplate.createCollection(CLIENTS_COLLECTION_NAME);
    }

    @RollbackBeforeExecution
    public void rollbackBeforeExecution(MongoTemplate mongoTemplate) {

        mongoTemplate.getCollection(CLIENTS_COLLECTION_NAME).drop();
    }

    @Execution
    public void execution(MongoTemplate mongoTemplate) {

        mongoTemplate.getCollection(CLIENTS_COLLECTION_NAME)
                .insertMany(IntStream.range(0, INITIAL_CLIENTS)
                        .mapToObj(_0_mongock_create_authors_collection::getClient)
                        .collect(Collectors.toList()));
    }

    @RollbackExecution
    public void rollbackExecution(MongoTemplate mongoTemplate) {
        mongoTemplate.getCollection(CLIENTS_COLLECTION_NAME).deleteMany(new Document());
    }

    private static Document getClient(int i) {
        return new Document()
                .append("name", "name-" + i)
                .append("email","email-" + i)
                .append("phone","phone" + i).
                append("country","country" + i);
    }
}
