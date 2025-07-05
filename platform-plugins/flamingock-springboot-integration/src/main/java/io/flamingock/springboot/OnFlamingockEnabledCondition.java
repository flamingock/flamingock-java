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

package io.flamingock.springboot;

import io.flamingock.api.SetupType;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Optional;
import java.util.function.Supplier;

public final class OnFlamingockEnabledCondition extends SpringBootCondition {

    private static final Supplier<Optional<FlamingockMetadata>> DEFAULT_METADATA_SUPPLIER = () -> {
        try {
            return Optional.ofNullable(Deserializer.readMetadataFromFile());      // wrap so we still get a metadata object
        } catch (Exception ex) {
            return Optional.empty();
        }
    };

    private static Supplier<Optional<FlamingockMetadata>> metadataSupplier = DEFAULT_METADATA_SUPPLIER;

    // ---- TEST HOOK -------------------------------------------------------
    static void setMetadataSupplier(Supplier<Optional<FlamingockMetadata>> s) {
        metadataSupplier = s;
    }

    static void restoreMetadataSupplier() {
        metadataSupplier = DEFAULT_METADATA_SUPPLIER;
    }
    // ---------------------------------------------------------------------

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata md) {

        ConditionMessage.Builder msg = ConditionMessage.forCondition("Flamingock auto-configuration");

        // global toggle
        if (!Boolean.parseBoolean(context.getEnvironment().getProperty("flamingock.enabled", "true"))) {
            return ConditionOutcome.noMatch(msg.because("flamingock.enabled=false"));
        }

        Optional<FlamingockMetadata> metadataOpt = metadataSupplier.get();

        if (metadataOpt.isEmpty()) {
            return ConditionOutcome.noMatch(msg.because("no metadata file found"));
        }

        String setupStr = Optional.ofNullable(metadataOpt.get().getSetup()).orElse(SetupType.DEFAULT.name());

        if (SetupType.BUILDER.name().equalsIgnoreCase(setupStr)) {
            return ConditionOutcome.noMatch(msg.because("setup=BUILDER → manual builder expected"));
        }

        return ConditionOutcome.match(msg.because("setup=" + setupStr));
    }
}
