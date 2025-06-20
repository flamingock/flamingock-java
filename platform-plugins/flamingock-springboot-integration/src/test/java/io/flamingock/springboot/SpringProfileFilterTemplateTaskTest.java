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

import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.internal.common.core.template.ChangeFileDescriptor;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.internal.common.core.template.TemplateManager;
import io.flamingock.internal.common.core.preview.TemplatePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.builder.PreviewTaskBuilder;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.LoadedTaskBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringProfileFilterTemplateTaskTest {



    @BeforeAll
    static void beforeAll() {
        TemplateManager.addTemplate(TemplateSimulate.class.getSimpleName(), TemplateSimulate.class);
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[] and taskProfiles=[]")
    void trueIfActiveProfilesEmptyAndNotAnnotated() {
        assertTrue(new SpringbootProfileFilter().filter(getTemplateLoadedChangeUnit()));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and taskProfiles=[P1]")
    void trueIfActiveProfilesAndAnnotatedWhenMatched() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getTemplateLoadedChangeUnit("P1")));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1,P2] and taskProfiles=[P1]")
    void trueIfActiveProfilesContainAnnotatedProfile() {
        assertTrue(new SpringbootProfileFilter("P1", "P2").filter(getTemplateLoadedChangeUnit("P1")));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and taskProfiles=[P1,P2]")
    void trueIfAnnotatedProfilesContainActiveProfile() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getTemplateLoadedChangeUnit("P1,P2")));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P2] and taskProfiles=[!P1]")
    void trueIfAnnotatedProfileIsNegativeP1AndActiveProfileIsP2() {
        assertTrue(new SpringbootProfileFilter("P2").filter(getTemplateLoadedChangeUnit("!P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and taskProfiles=[!P1]")
    void trueIfActiveProfileEmptyAndTaskProfileNegativeP1() {
        assertTrue(new SpringbootProfileFilter().filter(getTemplateLoadedChangeUnit("!P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and taskProfiles=[P1]")
    void falseIfActiveProfileEmptyAndTaskProfileP1() {
        assertFalse(new SpringbootProfileFilter().filter(getTemplateLoadedChangeUnit("P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P2] and taskProfiles=[P1]")
    void falseIfActiveProfileAndTaskProfileDontMatch() {
        assertFalse(new SpringbootProfileFilter("P2").filter(getTemplateLoadedChangeUnit("P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1] and taskProfiles=[!P1]")
    void falseIfActiveProfileIsP1AndTaskProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1").filter(getTemplateLoadedChangeUnit("!P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1,P2] and taskProfiles=[!P1]")
    void falseIfActiveProfileIsP1P2AndTaskProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1", "P2").filter(getTemplateLoadedChangeUnit("!P1")));
    }

    private AbstractLoadedTask getTemplateLoadedChangeUnit() {
        return getTemplateLoadedChangeUnit(null);
    }

    private AbstractLoadedTask getTemplateLoadedChangeUnit(String profiles) {

        ChangeFileDescriptor changeFileDescriptor = new ChangeFileDescriptor(
                "template-base-change-id",
                "1",
                TemplateSimulate.class.getSimpleName(),
                profiles,
                true,
                null,
                null,
                null
        );

        TemplatePreviewChangeUnit preview = PreviewTaskBuilder.getTemplateBuilder(changeFileDescriptor).build();

        return LoadedTaskBuilder.getInstance(preview).build();

    }

    public static abstract class TemplateSimulate implements ChangeTemplate<Void, Object, Object> {}

    @ChangeUnit(id = "not-annotated", order = "000")
    public static class NotAnnotated {
    }

    @Profile("P1")
    @ChangeUnit(id = "annotated-p1", order = "001")
    public static class P1 {
    }

    @Profile("!P1")
    @ChangeUnit(id = "annotated-!-p1", order = "002")
    public static class NotP1 {
    }

    @Profile({"P1", "P2"})
    @ChangeUnit(id = "annotated-p1-p2", order = "003")
    public static class P1AndP2 {
    }
}