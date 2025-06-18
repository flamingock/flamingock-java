package io.flamingock.community.mongodb.sync.changes;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import org.bson.Document;

import java.util.stream.Collectors;
import java.util.stream.IntStream;


@io.flamingock.api.annotations.ChangeUnit(id = "create-author-collection", order = "000", author = "mongock")
@ChangeUnit(id = "create-author-collection", order = "000", author = "mongock")
public class _0_mongock_create_authors_collection {

    public final static int INITIAL_CLIENTS = 10;
    public final static String CLIENTS_COLLECTION_NAME = "mongockClientCollection";

    @BeforeExecution
    public void beforeExecution(MongoDatabase mongoDatabase) {

        mongoDatabase.createCollection(CLIENTS_COLLECTION_NAME);
    }

    @RollbackBeforeExecution
    public void rollbackBeforeExecution(MongoDatabase mongoDatabase) {

        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME).drop();
    }

    @io.flamingock.api.annotations.Execution
    @Execution
    public void execution(MongoDatabase mongoDatabase) {

        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME)
                .insertMany(IntStream.range(0, INITIAL_CLIENTS)
                        .mapToObj(_0_mongock_create_authors_collection::getClient)
                        .collect(Collectors.toList()));
    }

    @io.flamingock.api.annotations.RollbackExecution
    @RollbackExecution
    public void rollbackExecution(MongoDatabase mongoDatabase) {
        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME).deleteMany(new Document());
    }

    private static Document getClient(int i) {
        return new Document()
                .append("name", "name-" + i)
                .append("email","email-" + i)
                .append("phone","phone" + i).
                append("country","country" + i);
    }
}
