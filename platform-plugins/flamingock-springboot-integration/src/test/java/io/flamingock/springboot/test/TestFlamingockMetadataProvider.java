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
package io.flamingock.springboot.test;

import io.flamingock.internal.common.core.metadata.FlamingockMetadataProvider;

/**
 * Test-only SPI provider that points the runtime at the hand-curated metadata fixture in
 * {@code src/test/resources/META-INF/flamingock/metadata.json}. Avoids running the
 * annotation processor over the test sources of this plugin module while keeping the
 * Phase 2 SPI-based discovery happy.
 */
public class TestFlamingockMetadataProvider implements FlamingockMetadataProvider {
    @Override
    public String getMetadataResourcePath() {
        return "META-INF/flamingock/metadata.json";
    }
}
