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

package io.flamingock.oss.driver.mongodb.springdata.v4;

import java.util.function.Function;
import com.mongodb.client.MongoDatabase;

import io.flamingock.commons.utils.Pair;
import io.flamingock.commons.utils.Trio;
import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.internal.core.engine.audit.writer.AuditEntry;
import io.flamingock.commons.utils.TimeUtil;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import io.flamingock.core.preview.CodePreviewLegacyChangeUnit;
import io.flamingock.core.preview.PreviewMethod;
import io.flamingock.core.preview.PreviewPipeline;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.oss.driver.common.mongodb.MongoDBAuditMapper;
import io.flamingock.oss.driver.mongodb.springdata.v4.internal.mongodb.SpringDataMongoV4DocumentWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import static io.flamingock.internal.core.community.AuditEntryField.KEY_CHANGE_ID;
import static io.flamingock.internal.core.community.AuditEntryField.KEY_TIMESTAMP;

public class MongoDBTestHelper {
    public final MongoDatabase mongoDatabase;
    private final MongoDBAuditMapper<SpringDataMongoV4DocumentWrapper> mapper = new MongoDBAuditMapper<>(() -> new SpringDataMongoV4DocumentWrapper(new Document()));

    private static final Function<Class<?>, Trio<String, String, Boolean>> infoExtractor = c-> {
        ChangeUnit ann = c.getAnnotation(ChangeUnit.class);
        return new Trio<>(ann.id(), ann.order(), ann.transactional());
    };

    private static final Function<Class<?>, Trio<String, String, Boolean>> infoExtractorLegacy = c-> {
        io.mongock.api.annotations.ChangeUnit ann = c.getAnnotation(io.mongock.api.annotations.ChangeUnit.class);
        return new Trio<>("[" + ann.author() + "]" + ann.id(), ann.order(), ann.transactional());
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
                .map(SpringDataMongoV4DocumentWrapper::new)
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
                    boolean isNewChangeUnit = trio.getFirst().isAnnotationPresent(ChangeUnit.class);
                    Function<Class<?>, Trio<String, String, Boolean>> extractor = isNewChangeUnit
                            ? infoExtractor
                            : infoExtractorLegacy;
                    Trio<String, String, Boolean> changeInfo = extractor.apply(trio.getFirst());
                    PreviewMethod rollback = null;
                    if (trio.getThird() != null) {
                        rollback = new PreviewMethod("rollbackExecution", getParameterTypes(trio.getThird()));
                    }

                    List<CodePreviewChangeUnit> changes = new ArrayList<>();

                    if(isNewChangeUnit) {
                        changes.add(new CodePreviewChangeUnit(
                                changeInfo.getFirst(),
                                changeInfo.getSecond(),
                                trio.getFirst().getName(),
                                new PreviewMethod("execution", getParameterTypes(trio.getSecond())),
                                rollback,
                                false,
                                changeInfo.getThird(),
                                false
                        ));
                    } else {

                        //we are assuming, for testing purpose, that if it's legacy, it provides beforeExecution,
                        // with same parameterTypes and, if 'rollbackBeforeExecution' provided too, it is with the same
                        //parameters
                        PreviewMethod rollbackBeforeExecution = null;
                        if (trio.getThird() != null) {
                            rollbackBeforeExecution = new PreviewMethod("rollbackBeforeExecution", getParameterTypes(trio.getThird()));
                        }
                        changes.add(new CodePreviewLegacyChangeUnit(
                                changeInfo.getFirst(),
                                changeInfo.getSecond(),
                                trio.getFirst().getName(),
                                new PreviewMethod("execution", getParameterTypes(trio.getSecond())),
                                rollback,
                                new PreviewMethod("beforeExecution", getParameterTypes(trio.getSecond())),
                                rollbackBeforeExecution,
                                false,
                                changeInfo.getThird(),
                                false
                        ));

                    }
                    return changes;
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        PreviewStage stage = new PreviewStage(
                "stage-name",
                "some description",
                null,
                null,
                tasks,
                false
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
}
