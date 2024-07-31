/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.examples.mysql.standalone;

import io.flamingock.cloud.transaction.sql.SqlCloudTransactioner;
import io.flamingock.cloud.transaction.sql.SqlDialect;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.template.sql.SqlTemplateModule;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MysqlStandaloneApplication {

    private static final String SERVICE_NAME = "clients-service";
    private static final String ENVIRONMENT = "development";
    private static final String API_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImZsYW1pbmdvY2staW50ZXJuYWwifQ.eyJpc3MiOiJodHRwczovL2ZsYW1pbmdvY2suZXUuYXV0aDAuY29tIiwiYXVkIjoiaHR0cHM6Ly9hcGkuZmxhbWluZ29jay5pbyIsImlhdCI6MTcyMjMzMzQ4MSwiZXhwIjoxNzUzODY5NDgxLCJ0b2tlbl90eXBlIjoiYXBpX3Rva2VuIiwib3JnYW5pemF0aW9uIjoiMjNjMWE1OTYtNmIwYy00Mzk0LTg3NTYtOWU0NGVhNTI3MzI4IiwicHJvamVjdCI6ImFkMjFlZTgzLTQ0ZjgtNDMyOS1iMzVhLTNiNTNiMWQyMmNmYyIsImVudmlyb25tZW50IjoiYTlmYzFjYTctM2VhMi00ODZiLTkwYTgtNjM0OGNmYmJmMTRmIiwic2VydmljZSI6ImIyOTY0NWJhLTU3MjctNDk4Yi05NmI4LWNiOGY5MzkyM2QyNyJ9.g8NQs6KIvfctV6AdWigYWbiUgaWuCNNgO3wL8Q0dRqsmlzUS06CyhN8u109Soteg2GLmg_X75tyE5kBtQ65kMQb1dF0vDKl2488uuQwOycnBXQO9zA88ACJsbkgUUz_VXEm2eedugJnApCAVWw3g4TY9s5L7o4TyT41bYqC2TCIUFxrzDf08bR70xO5Abky0wc8DXimn5vwNygzvmRLZBo-3grkQvjUd7-3LQk5bdxY7J8tRQR8guJndTQEB5L9YJM_OxtksQkuyQ221Vvu9CO657Bz2X6MgGGjC59eeMU3K8NrlfP0NIhdvIEg-SIThhtXD1XMXwFDKWJObRA";

    public static void main(String[] args) throws ClassNotFoundException {
        new MysqlStandaloneApplication()
                .run();

    }

    public void run() throws ClassNotFoundException {
        try (CloudTransactioner cloudTransactioner = getSqlCloudTransactioner()) {
            FlamingockStandalone
                    .cloud()
                    .setHost("http://localhost:8080")
                    .setApiToken(API_TOKEN)
                    .setEnvironment(ENVIRONMENT)
                    .setService(SERVICE_NAME)
                    .setCloudTransactioner(cloudTransactioner)//for cloud transactions with Sql
                    .setLockAcquiredForMillis(6 * 1000L)//this is just to show how is set. Default value is still 60 * 1000L
                    .setLockQuitTryingAfterMillis(10 * 1000L)//this is just to show how is set. Default value is still 3 * 60 * 1000L
                    .setLockTryFrequencyMillis(3000L)//this is just to show how is set. Default value is still 1000L
                    .addStage(new Stage("database_stage").addFileDirectory("flamingock/stage1"))
                    .addTemplateModule(new SqlTemplateModule())
                    .addDependency(Connection.class, getConnection())
                    .build()
                    .run();
        }
    }

    private static SqlCloudTransactioner getSqlCloudTransactioner() throws ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return new SqlCloudTransactioner()
                .setDialect(SqlDialect.MYSQL)
                .setUrl("jdbc:mysql://localhost:3307/flamingock")
                .setUser("root")
                .setPassword("strong_password");
    }

    //Temporally because we haven't injected transactional = true
    private static Connection getConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection("jdbc:mysql://localhost:3307/flamingock", "root", "strong_password");
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}