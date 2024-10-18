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

package io.flamingock.core.engine.local;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.LocalSystemModule;
import io.flamingock.core.engine.ConnectionEngine;

import java.util.Optional;

public interface LocalConnectionEngine extends ConnectionEngine {
    void initialize(RunnerId runnerId);

    Auditor getAuditor();

    default Optional<? extends LocalSystemModule> getMongockLegacyImporterModule() {
        return Optional.empty();
//        throw new RuntimeException("Mongock legacy importer requested, but it is not implemented for the specified driver. " +
//                "Please reach out to the community for further assistance or feature requests.");
    }

}
