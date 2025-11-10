/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.community.sql;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class SybaseContainer extends JdbcDatabaseContainer<SybaseContainer> {

    private static final String IMAGE = "datagrip/sybase";
    private static final int SYBASE_PORT = 5000;
    private static final String DEFAULT_DATABASE = "tempdb";
    private static final String DEFAULT_USERNAME = "sa";
    private static final String DEFAULT_PASSWORD = "myPassword";

    public SybaseContainer() {
        this(IMAGE);
    }

    public SybaseContainer(String dockerImageName) {
        super(DockerImageName.parse(dockerImageName));
        withExposedPorts(SYBASE_PORT);
        withEnv("SYBASE_DB", DEFAULT_DATABASE);
        withEnv("SYBASE_USER", DEFAULT_USERNAME);
        withEnv("SYBASE_PASSWORD", DEFAULT_PASSWORD);
        waitingFor(Wait.forLogMessage(".*Recovery complete.*", 1)
                .withStartupTimeout(Duration.ofMinutes(5)));
    }

    @Override
    public String getDriverClassName() {
        return "com.sybase.jdbc4.jdbc.SybDriver";
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:sybase:Tds:" + getHost() + ":" + getMappedPort(SYBASE_PORT) + "/" + DEFAULT_DATABASE;
    }

    @Override
    public String getUsername() {
        return DEFAULT_USERNAME;
    }

    @Override
    public String getPassword() {
        return DEFAULT_PASSWORD;
    }

    @Override
    protected String getTestQueryString() {
        return "SELECT 1";
    }
}
