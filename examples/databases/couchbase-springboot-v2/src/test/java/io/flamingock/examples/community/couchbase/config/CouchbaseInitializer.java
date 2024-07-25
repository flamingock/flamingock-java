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

package io.flamingock.examples.community.couchbase.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;

@Configuration
public class CouchbaseInitializer  implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String BUCKET_NAME = "bucket";

    @Container
    public static final CouchbaseContainer container = new CouchbaseContainer("couchbase/server").withBucket(new BucketDefinition(BUCKET_NAME));

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        container.withStartupAttempts(3).start();
        TestPropertyValues
            .of("spring.data.couchbase.bucket-name=" + BUCKET_NAME,
                "spring.couchbase.connection-string=" + container.getConnectionString(),
                "spring.couchbase.username=" + container.getUsername(),
                "spring.couchbase.password=" + container.getPassword(),
                "flamingock.couchbase.scope=",
                "flamingock.couchbase.collection=")
            .applyTo(context.getEnvironment());
    }
}

