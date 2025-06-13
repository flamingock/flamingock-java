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

import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.internal.core.task.loaded.CodeLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.LoadedTaskBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringProfileFilterCodeTaskTest {

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[] and taskProfiles=[]")
    void trueIfActiveProfilesEmptyAndNotAnnotated() {
        assertTrue(new SpringbootProfileFilter().filter(getCodeLoadedChangeUnit(NotAnnotated.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and taskProfiles=[P1]")
    void trueIfActiveProfilesAndAnnotatedWhenMatched() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getCodeLoadedChangeUnit(P1.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1,P2] and taskProfiles=[P1]")
    void trueIfActiveProfilesContainAnnotatedProfile() {
        assertTrue(new SpringbootProfileFilter("P1", "P2").filter(getCodeLoadedChangeUnit(P1.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and taskProfiles=[P1,P2]")
    void trueIfAnnotatedProfilesContainActiveProfile() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getCodeLoadedChangeUnit(P1AndP2.class)));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P2] and taskProfiles=[!P1]")
    void trueIfAnnotatedProfileIsNegativeP1AndActiveProfileIsP2() {
        assertTrue(new SpringbootProfileFilter("P2").filter(getCodeLoadedChangeUnit(NotP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and taskProfiles=[!P1]")
    void trueIfActiveProfileEmptyAndTaskProfileNegativeP1() {
        assertTrue(new SpringbootProfileFilter().filter(getCodeLoadedChangeUnit(NotP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and taskProfiles=[P1]")
    void falseIfActiveProfileEmptyAndTaskProfileP1() {
        assertFalse(new SpringbootProfileFilter().filter(getCodeLoadedChangeUnit(P1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P2] and taskProfiles=[P1]")
    void falseIfActiveProfileAndTaskProfileDontMatch() {
        assertFalse(new SpringbootProfileFilter("P2").filter(getCodeLoadedChangeUnit(P1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1] and taskProfiles=[!P1]")
    void falseIfActiveProfileIsP1AndTaskProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1").filter(getCodeLoadedChangeUnit(NotP1.class)));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1,P2] and taskProfiles=[!P1]")
    void falseIfActiveProfileIsP1P2AndTaskProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1", "P2").filter(getCodeLoadedChangeUnit(NotP1.class)));
    }

    private CodeLoadedChangeUnit getCodeLoadedChangeUnit(Class<?> sourceClass) {
        return LoadedTaskBuilder.getCodeBuilderInstance(sourceClass).build();
    }

    @ChangeUnit(id="not-annotated", order = "000")
    public static class NotAnnotated {
    }

    @Profile("P1")
    @ChangeUnit(id="annotated-p1", order = "001")
    public static class P1 {
    }

    @Profile("!P1")
    @ChangeUnit(id="annotated-!-p1", order = "002")
    public static class NotP1 {
    }

    @Profile({"P1", "P2"})
    @ChangeUnit(id="annotated-p1-p2", order = "003")
    public static class P1AndP2 {
    }
}