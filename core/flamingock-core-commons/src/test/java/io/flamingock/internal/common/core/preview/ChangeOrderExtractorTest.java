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
package io.flamingock.internal.common.core.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeOrderExtractorTest {

    @Test
    void shouldExtractOrderFromDefaultPackageClassName() {
        String order = ChangeOrderExtractor.extractOrderFromClassName("dummy-change", "_0001__DummyChange");

        assertEquals("0001", order);
    }

    @Test
    void shouldExtractOrderFromPackagedClassName() {
        String order = ChangeOrderExtractor.extractOrderFromClassName("dummy-change", "io.flamingock._0002__DummyChange");

        assertEquals("0002", order);
    }

    @Test
    void shouldExtractOrderFromNestedBinaryClassName() {
        String order = ChangeOrderExtractor.extractOrderFromClassName("dummy-change", "io.flamingock.ChangeGroup$_0003__DummyChange");

        assertEquals("0003", order);
    }
}
