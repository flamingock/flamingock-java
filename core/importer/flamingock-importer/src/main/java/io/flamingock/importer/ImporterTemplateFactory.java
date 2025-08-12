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
package io.flamingock.importer;

import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.internal.common.core.template.ChangeTemplateFactory;
import org.jetbrains.annotations.NotNull;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class ImporterTemplateFactory implements ChangeTemplateFactory {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("ImporterFactory");

    private static final String MONGO_TEMPLATE_CLASS = "io.flamingock.importer.mongodb.MongoDbImporterChangeTemplate";
    private static final String DYNAMO_TEMPLATE_CLASS = "io.flamingock.importer.dynamodb.DynamoDbImporterChangeTemplate";
    private static final String COUCHBASE_TEMPLATE_CLASS = "io.flamingock.importer.couchbase.CouchbaseImporterChangeTemplate";


    @Override
    public Collection<ChangeTemplate<?, ?, ?>> getTemplates() {
        try {
            Optional<String> className = getClassName();
            if (className.isPresent()) {
                logger.info("Loading importer template: {}", className);
                Class<?> changeTemplateClass = Class.forName(className.get());
                return Collections.singletonList(
                        (ChangeTemplate<?, ?, ?>) changeTemplateClass.getDeclaredConstructor().newInstance()
                );
            } else {
                return Collections.emptyList();
            }


        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Importer importer template class not found", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate importer class ", e);
        }
    }

    @NotNull
    private static Optional<String> getClassName() {

        if (isMongoDbAdapter()) {
            return Optional.of(MONGO_TEMPLATE_CLASS);
        } else if (isDynamoDbAdapter()) {
            return Optional.of(DYNAMO_TEMPLATE_CLASS);
        } else if (isCouchbaseAdapter()) {
            return Optional.of(COUCHBASE_TEMPLATE_CLASS);
        } else {
            logger.debug("No compatible database driver detected. Please include a supported database dependency (MongoDB, DynamoDB, or Couchbase) in your project classpath.");
        }
        return Optional.empty();
    }

    private static boolean isMongoDbAdapter() {
        try {
            Class.forName("com.mongodb.client.MongoCollection");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("MongoDB adapter not found, skipping");
            return false;
        }
    }

    private static boolean isDynamoDbAdapter() {
        try {
            Class.forName("software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("DynamoDB adapter not found, skipping");
            return false;
        }
    }

    private static boolean isCouchbaseAdapter() {
        try {
            Class.forName("com.couchbase.client.java.Cluster");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("Couchbase adapter not found, skipping");
            return false;
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Importer importer template class not found", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate importer class ", e);
        }
    }

    @NotNull
    private static ChangeTemplate<?, ?, ?> getInstance(Class<?> changeTemplateClass) {
        try {
            return (ChangeTemplate<?, ?, ?>) changeTemplateClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException("Failed to instantiate importer class ", e);
        }
    }
}

