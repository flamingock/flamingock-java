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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrphanChangeValidatorTest {

    @Test
    void throwsWhenStrictAndOrphansPresent() {
        FlamingockMetadata md = new FlamingockMetadata();
        md.setStrictStageMapping(true);
        md.setOrphanChanges(Arrays.asList(
                change("id-a", "com.example.A"),
                change("id-b", "com.example.B")));

        BuilderException ex = assertThrows(BuilderException.class,
                () -> OrphanChangeValidator.validate(md));
        assertTrue(ex.getMessage().contains("id-a"));
        assertTrue(ex.getMessage().contains("id-b"));
        assertTrue(ex.getMessage().contains("strictStageMapping"),
                "message should reference the flag for the user");
    }

    @Test
    void doesNotThrowWhenStrictAndOrphansEmpty() {
        FlamingockMetadata md = new FlamingockMetadata();
        md.setStrictStageMapping(true);
        md.setOrphanChanges(Collections.emptyList());
        assertDoesNotThrow(() -> OrphanChangeValidator.validate(md));
    }

    @Test
    void doesNotThrowWhenStrictAndOrphansNull() {
        FlamingockMetadata md = new FlamingockMetadata();
        md.setStrictStageMapping(true);
        // orphanChanges left null
        assertDoesNotThrow(() -> OrphanChangeValidator.validate(md));
    }

    @Test
    void doesNotThrowWhenNotStrictEvenWithOrphans() {
        FlamingockMetadata md = new FlamingockMetadata();
        md.setStrictStageMapping(false);
        md.setOrphanChanges(Collections.singletonList(change("id-a", "com.example.A")));
        assertDoesNotThrow(() -> OrphanChangeValidator.validate(md));
    }

    @Test
    void doesNotThrowOnNullMetadata() {
        assertDoesNotThrow(() -> OrphanChangeValidator.validate(null));
    }

    private static CodePreviewChange change(String id, String fqcn) {
        return new CodePreviewChange(id, null, "test", fqcn, fqcn,
                null, null, null, false, null, false, null, null, false);
    }
}
