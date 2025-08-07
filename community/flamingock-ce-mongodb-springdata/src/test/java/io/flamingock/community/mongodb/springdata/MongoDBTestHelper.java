/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.community.mongodb.springdata;

import java.util.function.Function;
import com.mongodb.client.MongoDatabase;

import io.flamingock.api.StageType;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.internal.util.Pair;
import io.flamingock.internal.util.Trio;
import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.TimeUtil;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.community.mongodb.springdata.internal.mongodb.SpringDataMongoDocumentWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import static io.flamingock.internal.core.community.Constants.KEY_CHANGE_ID;
import static io.flamingock.internal.core.community.Constants.KEY_TIMESTAMP;

public class MongoDBTestHelper {
    public final MongoDatabase mongoDatabase;
    private final MongoDBAuditMapper<SpringDataMongoDocumentWrapper> mapper = new MongoDBAuditMapper<>(() -> new SpringDataMongoDocumentWrapper(new Document()));

    private static final Function<Class<?>, ChangeInfo> infoExtractor = c -> {
        ChangeUnit ann = c.getAnnotation(ChangeUnit.class);
        TargetSystem targetSystemAnn = c.getAnnotation(TargetSystem.class);
        String targetSystemId = targetSystemAnn != null ? targetSystemAnn.id() : null;
        return new ChangeInfo(ann.id(), ann.order(), targetSystemId, ann.transactional());
    };

    public MongoDBTestHelper(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }

    public boolean collectionExists(String collectionName) {
        return mongoDatabase.listCollectionNames().into(new ArrayList()).contains(collectionName);
    }

    public List<String> getAuditLogSorted(String auditLogCollection) {
        return mongoDatabase.getCollection(auditLogCollection)
                .find()
                .into(new LinkedList<>())
                .stream()
                .sorted(Comparator.comparing(d -> TimeUtil.toLocalDateTime(d.get(KEY_TIMESTAMP))))
                .map(document -> document.getString(KEY_CHANGE_ID))
                .collect(Collectors.toList());
    }

    public List<AuditEntry> getAuditEntriesSorted(String auditLogCollection) {
        return mongoDatabase.getCollection(auditLogCollection).find()
                .into(new LinkedList<>())
                .stream()
                .map(SpringDataMongoDocumentWrapper::new)
                .map(mapper::fromDocument)
                .collect(Collectors.toList());
    }


    /**
     * Builds a {@link PreviewPipeline} composed of a single {@link PreviewStage} containing one or more {@link CodePreviewChangeUnit}s.
     * <p>
     * Each change unit is derived from a {@link Pair} where:
     * <ul>
     *   <li>The first item is the {@link Class} annotated with {@link ChangeUnit} or {@link io.mongock.api.annotations.ChangeUnit}</li>
     *   <li>The second item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @Execution}</li>
     *   <li>The third item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @RollbackExecution}</li>
     * </ul>
     *
     * @param changeDefinitions varargs of pairs containing change classes and their execution method parameters
     * @return a {@link PreviewPipeline} ready for preview or testing
     */
    @SafeVarargs
    public static PreviewPipeline getPreviewPipeline(Trio<Class<?>, List<Class<?>>, List<Class<?>>>... changeDefinitions) {

        List<CodePreviewChangeUnit> tasks = Arrays.stream(changeDefinitions)
                .map(trio -> {
                    Function<Class<?>, ChangeInfo> extractor = infoExtractor;
                    ChangeInfo changeInfo = extractor.apply(trio.getFirst());
                    PreviewMethod rollback = null;
                    PreviewMethod rollbackBeforeExecution = null;
                    if (trio.getThird() != null) {
                        rollback = new PreviewMethod("rollbackExecution", getParameterTypes(trio.getThird()));
                        rollbackBeforeExecution = new PreviewMethod("rollbackBeforeExecution", getParameterTypes(trio.getThird()));
                    }

                    List<CodePreviewChangeUnit> changes = new ArrayList<>();
                    changes.add(new CodePreviewChangeUnit(
                            changeInfo.changeId,
                            changeInfo.order,
                            trio.getFirst().getName(),
                            new PreviewMethod("execution", getParameterTypes(trio.getSecond())),
                            rollback,
                            new PreviewMethod("beforeExecution", getParameterTypes(trio.getSecond())),
                            rollbackBeforeExecution,
                            false,
                            changeInfo.transactional,
                            false,
                            changeInfo.targetSystem
                    ));
                    return changes;
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        PreviewStage stage = new PreviewStage(
                "stage-name",
                StageType.DEFAULT,
                "some description",
                null,
                null,
                tasks
        );

        return new PreviewPipeline(Collections.singletonList(stage));
    }

    @NotNull
    private static List<String> getParameterTypes(List<Class<?>> second) {
        return second
                .stream()
                .map(Class::getName)
                .collect(Collectors.toList());
    }

    static class ChangeInfo {
        private final String changeId;
        private final String order;
        private final String targetSystem;
        private final boolean transactional;

        public ChangeInfo(String changeId, String order, String targetSystem, boolean transactional) {
            this.changeId = changeId;
            this.order = order;
            this.targetSystem = targetSystem;
            this.transactional = transactional;
        }

        public String getChangeId() {
            return changeId;
        }

        public String getOrder() {
            return order;
        }

        public String getTargetSystem() {
            return targetSystem;
        }

        public boolean isTransactional() {
            return transactional;
        }
    }
}
