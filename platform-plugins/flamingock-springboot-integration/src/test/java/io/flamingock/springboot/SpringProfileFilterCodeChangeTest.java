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
package io.flamingock.springboot;

import com.github.cloudyrock.mongock.ChangeSet;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.internal.core.change.loaded.CodeLoadedChange;
import io.flamingock.internal.core.change.loaded.LoadedChangeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringProfileFilterCodeChangeTest {

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[] and changeProfiles=[]")
    void trueIfActiveProfilesEmptyAndNotAnnotated() {
        assertTrue(new SpringbootProfileFilter().filter(getCodeLoadedChange(_000__NotAnnotated.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and changeProfiles=[P1]")
    void trueIfActiveProfilesAndAnnotatedWhenMatched() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getCodeLoadedChange(_001__P1.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1,P2] and changeProfiles=[P1]")
    void trueIfActiveProfilesContainAnnotatedProfile() {
        assertTrue(new SpringbootProfileFilter("P1", "P2").filter(getCodeLoadedChange(_001__P1.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and changeProfiles=[P1,P2]")
    void trueIfAnnotatedProfilesContainActiveProfile() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getCodeLoadedChange(_003__P1AndP2.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P2] and changeProfiles=[!P1]")
    void trueIfAnnotatedProfileIsNegativeP1AndActiveProfileIsP2() {
        assertTrue(new SpringbootProfileFilter("P2").filter(getCodeLoadedChange(_002__NotP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and changeProfiles=[!P1]")
    void trueIfActiveProfileEmptyAndChangeProfileNegativeP1() {
        assertTrue(new SpringbootProfileFilter().filter(getCodeLoadedChange(_002__NotP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and changeProfiles=[P1]")
    void falseIfActiveProfileEmptyAndChangeProfileP1() {
        assertFalse(new SpringbootProfileFilter().filter(getCodeLoadedChange(_001__P1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P2] and changeProfiles=[P1]")
    void falseIfActiveProfileAndChangeProfileDontMatch() {
        assertFalse(new SpringbootProfileFilter("P2").filter(getCodeLoadedChange(_001__P1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1] and changeProfiles=[!P1]")
    void falseIfActiveProfileIsP1AndChangeProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1").filter(getCodeLoadedChange(_002__NotP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1,P2] and changeProfiles=[!P1]")
    void falseIfActiveProfileIsP1P2AndChangeProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1", "P2").filter(getCodeLoadedChange(_002__NotP1.class)));
    }

    private CodeLoadedChange getCodeLoadedChange(Class<?> sourceClass) {
        return LoadedChangeBuilder.getCodeBuilderInstance(sourceClass).build();
    }

    private CodeLoadedChange getLegacyCodeLoadedChange(Class<?> sourceClass) {
        return LoadedChangeBuilder.getCodeBuilderInstance(sourceClass).setLegacy(true).build();
    }

    // -----------------------------------------------------------------------
    // Legacy method @Profile tests — @ChangeSet vs ChangeUnit distinction
    // -----------------------------------------------------------------------

    // _010__LegacyWithMethodProfile: legacy change setLegacy(true), method has @Profile but
    // does NOT carry @ChangeSet (simulates a ChangeUnit/@Execution-style change).
    // Method-level @Profile must be IGNORED — fall through to class-level (no class @Profile → unfiltered).

    @Test
    @DisplayName("SHOULD return true WHEN legacy non-@ChangeSet method has @Profile(P1) and activeProfiles=[P1] — method @Profile is IGNORED")
    void legacyMethodProfileIgnoredWhenActiveProfileMatches() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getLegacyCodeLoadedChange(_010__LegacyWithMethodProfile.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN legacy non-@ChangeSet method has @Profile(P1) and activeProfiles=[P2] — method @Profile is IGNORED")
    void legacyMethodProfileIgnoredWhenActiveProfileDoesNotMatch() {
        // Method-level @Profile is not honored for non-@ChangeSet methods (ChangeUnit-style),
        // so fall through to class-level: no class @Profile → unfiltered → true.
        assertTrue(new SpringbootProfileFilter("P2").filter(getLegacyCodeLoadedChange(_010__LegacyWithMethodProfile.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN legacy class has @Profile(P1) and method has no @Profile, activeProfiles=[P1]")
    void legacyClassProfileFallbackWhenNoMethodProfile() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getLegacyCodeLoadedChange(_011__LegacyClassProfileNoMethod.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN legacy class has @Profile(P1) and method has no @Profile, activeProfiles=[P2]")
    void legacyClassProfileFallbackWhenNoMethodProfileNotMatching() {
        assertFalse(new SpringbootProfileFilter("P2").filter(getLegacyCodeLoadedChange(_011__LegacyClassProfileNoMethod.class)));
    }

    // -----------------------------------------------------------------------
    // Legacy @ChangeSet method @Profile tests — @Profile must be HONORED
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SHOULD return true WHEN legacy @ChangeSet method has @Profile(P1) and activeProfiles=[P1]")
    void changeSetMethodProfileHonoredWhenMatches() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getLegacyCodeLoadedChange(_013__ChangeSetWithMethodProfile.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN legacy @ChangeSet method has @Profile(P1) and activeProfiles=[P2]")
    void changeSetMethodProfileNotHonoredWhenNotMatches() {
        assertFalse(new SpringbootProfileFilter("P2").filter(getLegacyCodeLoadedChange(_013__ChangeSetWithMethodProfile.class)));
    }

    // -----------------------------------------------------------------------
    // Native method @Profile tests — method-level @Profile must be IGNORED
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SHOULD return true WHEN native @Apply method has @Profile(P1) but no class @Profile, activeProfiles=[]")
    void nativeMethodProfileNotHonoredWhenActiveProfileEmpty() {
        // No class-level @Profile means the change is always unfiltered.
        // Method-level @Profile on native @Apply is NOT honored.
        assertTrue(new SpringbootProfileFilter().filter(getCodeLoadedChange(_012__NativeWithMethodProfile.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN native @Apply method has @Profile(P1) but no class @Profile, activeProfiles=[P1]")
    void nativeMethodProfileNotHonoredWhenActiveProfileMatches() {
        // Even when active profiles happen to match, the method-level @Profile is
        // not checked — it must not gate execution for native changes.
        assertTrue(new SpringbootProfileFilter("P1").filter(getCodeLoadedChange(_012__NativeWithMethodProfile.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN native @Apply method has @Profile(P1) but no class @Profile, activeProfiles=[P2]")
    void nativeMethodProfileNotHonoredWhenActiveProfileDoesNotMatch() {
        // Method-level @Profile must be entirely ignored for native changes.
        // Without class-level @Profile the change is unfiltered even when P2 is active.
        assertTrue(new SpringbootProfileFilter("P2").filter(getCodeLoadedChange(_012__NativeWithMethodProfile.class)));
    }

    // =======================================================================
    // Test change classes
    // =======================================================================

    @Change(id="not-annotated", author = "aperezdieppa")
    public static class _000__NotAnnotated {
        @Apply
        public void apply() {
            // testing purpose
        }
    }

    @Profile("P1")
    @Change(id="annotated-p1", author = "aperezdieppa")
    public static class _001__P1 {
        @Apply
        public void apply() {
            // testing purpose
        }
    }

    @Profile("!P1")
    @Change(id="annotated-!-p1", author = "aperezdieppa")
    public static class _002__NotP1 {
        @Apply
        public void apply() {
            // testing purpose
        }
    }

    @Profile({"P1", "P2"})
    @Change(id="annotated-p1-p2", author = "aperezdieppa")
    public static class _003__P1AndP2 {
        @Apply
        public void apply() {
            // testing purpose
        }
    }

    // Legacy-style change: method-level @Profile on the apply method
    @Change(id="legacy-method-profile", author = "test")
    public static class _010__LegacyWithMethodProfile {
        @Apply
        @Profile("P1")
        public void apply() {
            // testing purpose
        }
    }

    // Legacy-style change: only class-level @Profile, method has none
    @Profile("P1")
    @Change(id="legacy-class-fallback", author = "test")
    public static class _011__LegacyClassProfileNoMethod {
        @Apply
        public void apply() {
            // testing purpose
        }
    }

    // Native Flamingock change: method-level @Profile should be IGNORED by the filter
    @Change(id="native-method-profile", author = "test")
    public static class _012__NativeWithMethodProfile {
        @Apply
        @Profile("P1")
        public void apply() {
            // testing purpose
        }
    }

    // Legacy @ChangeSet change: method-level @Profile should be HONORED by the filter
    @SuppressWarnings("deprecation")
    @Change(id="legacy-changeset-method-profile", author = "test")
    public static class _013__ChangeSetWithMethodProfile {
        @Apply
        @ChangeSet(author = "test", id = "test-id", order = "1")
        @Profile("P1")
        public void apply() {
            // testing purpose
        }
    }
}