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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class FirebirdJdbcContainer extends JdbcDatabaseContainer<FirebirdJdbcContainer> {

    private static final int FIREBIRD_PORT = 3050;
    private String rawDatabaseEnv = "test.fdb";
    private String fbUser = "sysdba";
    private String fbPassword = "masterkey";

    public FirebirdJdbcContainer(String dockerImageName) {
        super(DockerImageName.parse(dockerImageName));
        this.addExposedPort(FIREBIRD_PORT);
        this.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
    }

    @Override
    public FirebirdJdbcContainer withEnv(String key, String value) {
        if ("FIREBIRD_DATABASE".equalsIgnoreCase(key) && value != null) {
            this.rawDatabaseEnv = value.trim();
            String rel = normalizeToRelative(value);
            String containerDatabasePath = "/firebird/data/" + rel;
            super.withEnv("FIREBIRD_DATABASE", rel);
            return this;
        } else if ("FIREBIRD_USER".equalsIgnoreCase(key) && value != null) {
            if (!"sysdba".equalsIgnoreCase(value.trim())) {
                this.fbUser = value;
                super.withEnv(key, value);
            }
            return this;
        } else if ("ISC_PASSWORD".equalsIgnoreCase(key) && value != null) {
            this.fbPassword = value;
            super.withEnv(key, value);
            return this;
        }
        super.withEnv(key, value);
        return this;
    }

    private String normalizeToRelative(String envVal) {
        if (envVal == null) return "test.fdb";
        String v = envVal.trim();
        while (v.startsWith("/")) v = v.substring(1);
        String prefix = "firebird/data/";
        while (v.startsWith(prefix)) {
            v = v.substring(prefix.length());
        }
        return v;
    }

    @Override
    public String getDriverClassName() {
        return "org.firebirdsql.jdbc.FBDriver";
    }

    @Override
    public String getJdbcUrl() {
        String host = getHost();
        Integer mapped = getMappedPort(FIREBIRD_PORT);

        String db = rawDatabaseEnv == null ? "test.fdb" : rawDatabaseEnv.trim();
        while (db.startsWith("/")) db = db.substring(1);
        String prefix = "firebird/data/";
        if (db.startsWith(prefix)) {
            db = db.substring(prefix.length());
        }

        return String.format("jdbc:firebirdsql://%s:%d/%s", host, mapped, db);
    }

    @Override
    public String getUsername() {
        return fbUser == null ? "sysdba" : fbUser;
    }

    @Override
    public String getPassword() {
        return fbPassword == null ? "masterkey" : fbPassword;
    }

    @Override
    public String getTestQueryString() {
        return "SELECT 1 FROM RDB$DATABASE";
    }

    @Override
    protected void configure() {
    }
}
