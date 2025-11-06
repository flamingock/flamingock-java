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
package io.flamingock.community.sql;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;

public class InformixContainer extends JdbcDatabaseContainer<InformixContainer> {

    private static final String IMAGE = "icr.io/informix/informix-developer-database";
    private static final String DEFAULT_TAG = "14.10.FC9W1DE";
    private static final int INFORMIX_PORT = 9088;

    public InformixContainer() {
        this(IMAGE + ":" + DEFAULT_TAG);
    }

    public InformixContainer(String dockerImageName) {
        super(DockerImageName.parse(dockerImageName));
        withExposedPorts(INFORMIX_PORT);
        withEnv("LICENSE", "accept");
        withEnv("USER_THREADS", "100");
        withEnv("NUM_LOCKS", "20000");
        withCommand("sh", "-c",
                "touch /opt/ibm/informix/etc/.com.ibm.informix.sbspace && " +
                        "/opt/ibm/scripts/server_init.sh && " +
                        "onmode -wf USER_THREADS=100 && " +
                        "onspaces -c -S sbspace1 -p /opt/ibm/data/sbspace1 -o 0 -s 100000 && " +
                        "tail -f /dev/null");
    }

    @Override
    public String getDriverClassName() {
        return "com.informix.jdbc.IfxDriver";
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:informix-sqli://" + getHost() + ":" + getMappedPort(INFORMIX_PORT) +
                "/sysmaster:INFORMIXSERVER=informix;user=informix;password=in4mix";
    }

    @Override
    public String getUsername() {
        return "informix";
    }

    @Override
    public String getPassword() {
        return "in4mix";
    }

    @Override
    protected String getTestQueryString() {
        return "SELECT 1 FROM systables WHERE tabid=1";
    }
}
