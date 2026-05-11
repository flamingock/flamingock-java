/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.builder;

import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.CodePreviewChange;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Runtime gate enforcing the {@code @EnableFlamingock.strictStageMapping} contract: when the
 * processor parked code-changes as orphans (no covering stage at compile time) and the original
 * declaration set {@code strictStageMapping=true}, refuse to start the runner.
 */
public final class OrphanChangeValidator {

    private OrphanChangeValidator() {
    }

    /**
     * @throws BuilderException when {@code metadata.strictStageMapping == true} and
     *                          {@code metadata.orphanChanges} is non-empty
     */
    public static void validate(FlamingockMetadata metadata) {
        if (metadata == null || !metadata.isStrictStageMapping()) {
            return;
        }
        List<CodePreviewChange> orphans = metadata.getOrphanChanges();
        if (orphans == null || orphans.isEmpty()) {
            return;
        }
        String ids = orphans.stream()
                .map(CodePreviewChange::getId)
                .collect(Collectors.joining(", "));
        throw new BuilderException(
                "Strict stage mapping is enabled but the following changes are not mapped to any stage: ["
                        + ids + "]. Add a stage in @EnableFlamingock whose location covers their package, "
                        + "or set strictStageMapping=false.");
    }
}
