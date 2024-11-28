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
    private static final String API_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImZsYW1pbmdvY2staW50ZXJuYWwifQ.eyJpc3MiOiJodHRwczovL2ZsYW1pbmdvY2suZXUuYXV0aDAuY29tIiwiYXVkIjoiaHR0cHM6Ly9hcGkuZmxhbWluZ29jay5pbyIsImlhdCI6MTcyNDExODgxNSwiZXhwIjoxNzU1NjU0ODE1LCJ0b2tlbl90eXBlIjoiYXBpX3Rva2VuIiwib3JnYW5pemF0aW9uIjoiOWRkYWY2NWQtYzkwOS00MmYwLWE1NjEtZjYyMTM1MzM2MjUyIiwicHJvamVjdCI6ImMxYzA0ZDc3LTIyZGMtNGY3Mi1iYzUyLTM0MjNkYWRlYjhiZCIsImVudmlyb25tZW50IjoiOWY2OGQ3OGQtZDA5Yy00NzMxLTliNWEtMTRjNjgzZmJmMWE0Iiwic2VydmljZSI6IjYyMDEyOTJiLTUyN2UtNDFiNi1iYTRkLWNkY2NhYjc2OWM4ZiJ9.vm6Ukw2JKhqsjsyZyTV_gMWN16IDjHeav04nSYf8VJY8Ak38onfUMDT-vg2zliPMTzu48Nf9idxVv1OGlnC1E71hO2bGZmL-mNwBolpq5QaV4zqT-RVF6U3Yl9kLVUQQejQgmgETQANqgPOtG_AhqFYz-uS5v9eMN9vawd9_94beRubnu5aW2zAfVrWThNTNA1EBw-2wtdMwbohBl_Nac7NoMt7RbN1zKK6DfsG89SSonoUuIbTOQ_bjUJIVbxo4vwDQyMO4xRPEWuQ6HP-z91zvMKGVAV_ezbDXH9ioXdkPQrba5hAn3-i1ZKkZOJWclW1OD_F8x1O8DQGzHA";

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