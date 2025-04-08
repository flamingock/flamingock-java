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

package io.flamingock.template.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.api.template.ChangeTemplate;
import io.flamingock.core.api.template.annotations.Config;
import io.flamingock.core.api.template.annotations.ChangeTemplateConfigValidator;
import io.flamingock.core.api.template.annotations.ChangeTemplateExecution;
import io.flamingock.core.api.template.annotations.ChangeTemplateRollbackExecution;
import io.flamingock.template.mongodb.model.MongoOperation;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

public class MongoChangeTemplate implements ChangeTemplate {
    //avoids static loading at building time to keep GraalVM happy
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private MongoChangeTemplateConfig config;

    @Config
    public void setConfiguration(MongoChangeTemplateConfig config) {
        logger.trace("setting MongoChangeTemplate config: " + config);
        this.config = config;
    }

    @ChangeTemplateConfigValidator
    public void validate() {
        logger.trace("validating MongoChangeTemplate");
        config.validate();
    }

    @ChangeTemplateExecution
    public void execute(MongoDatabase db, @Nullable ClientSession clientSession) {
        logger.debug("MongoChangeTemplate changes with clientSession[{}]", clientSession != null);
        config.getChanges().forEach(executionOperation -> executeOp(db, executionOperation, clientSession));
    }

    @ChangeTemplateRollbackExecution
    public void rollback(MongoDatabase db, @Nullable ClientSession clientSession) {
        logger.debug("MongoChangeTemplate rollbacks with clientSession[{}]", clientSession != null);
        config.getRollbacks().forEach(executionOperation -> executeOp(db, executionOperation, clientSession));
    }

    private void executeOp(MongoDatabase db, MongoOperation op, ClientSession clientSession) {
        op.getOperator(db).apply(clientSession);
    }

    @Override
    public Collection<Class<?>> getReflectiveClasses() {
        return Arrays.asList(
                MongoChangeTemplateConfig.class,
                MongoOperation.class
        );
    }
}