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

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and method has @Profile(P1)")
    void trueIfMethodProfileMatchesActiveProfile() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getCodeLoadedChange(_004__MethodP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P2] and method has @Profile(P1)")
    void falseIfMethodProfileDoesNotMatchActiveProfile() {
        assertFalse(new SpringbootProfileFilter("P2").filter(getCodeLoadedChange(_004__MethodP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN class @Profile(P1) and method @Profile(P2) with active=[P1]")
    void falseIfMethodProfileOverridesClassProfile() {
        assertFalse(new SpringbootProfileFilter("P1").filter(getCodeLoadedChange(_005__ClassP1MethodP2.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1] and method has @Profile(!P1)")
    void falseIfMethodNegativeProfileExcludesActiveProfile() {
        assertFalse(new SpringbootProfileFilter("P1").filter(getCodeLoadedChange(_006__MethodNotP1.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P2] and method has @Profile(!P1)")
    void trueIfMethodNegativeProfileDoesNotExcludeActiveProfile() {
        assertTrue(new SpringbootProfileFilter("P2").filter(getCodeLoadedChange(_006__MethodNotP1.class)));
    }

    private CodeLoadedChange getCodeLoadedChange(Class<?> sourceClass) {
        return LoadedChangeBuilder.getCodeBuilderInstance(sourceClass).build();
    }

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

    @Change(id = "method-p1", author = "aperezdieppa")
    public static class _004__MethodP1 {
        @Apply
        @Profile("P1")
        public void apply() {
            // testing purpose
        }
    }

    @Profile("P1")
    @Change(id = "class-p1-method-p2", author = "aperezdieppa")
    public static class _005__ClassP1MethodP2 {
        @Apply
        @Profile("P2")
        public void apply() {
            // testing purpose
        }
    }

    @Change(id = "method-not-p1", author = "aperezdieppa")
    public static class _006__MethodNotP1 {
        @Apply
        @Profile("!P1")
        public void apply() {
            // testing purpose
        }
    }
}