package io.flamingock.importer.mongodb.flamingock.mongodb;

import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.api.annotations.Execution;
import io.mongock.api.annotations.BeforeExecution;

@ChangeUnit(id = "client-updater", order = "0002", author = "mongock")
public class ClientUpdater {

    @BeforeExecution
    public void beforeExecution() {
        System.out.println("Client Initializer");
    }

    @Execution
    public void execution() {
        System.out.println("Client Initializer");
    }
}
