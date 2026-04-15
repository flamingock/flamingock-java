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

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.annotations.RollbackTemplate;
import io.flamingock.internal.common.core.template.ChangeTemplateFileContent;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.wrappers.TemplateString;
import io.flamingock.api.template.wrappers.TemplateVoid;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.preview.builder.PreviewChangeBuilder;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.LoadedChangeBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringProfileFilterTemplateChangeTest {



    @BeforeAll
    static void beforeAll() {
        ChangeTemplateManager.addTemplate(TemplateSimulate.class);
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[] and changeProfiles=[]")
    void trueIfActiveProfilesEmptyAndNotAnnotated() {
        assertTrue(new SpringbootProfileFilter().filter(getTemplateLoadedChange()));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and changeProfiles=[P1]")
    void trueIfActiveProfilesAndAnnotatedWhenMatched() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getTemplateLoadedChange("P1")));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1,P2] and changeProfiles=[P1]")
    void trueIfActiveProfilesContainAnnotatedProfile() {
        assertTrue(new SpringbootProfileFilter("P1", "P2").filter(getTemplateLoadedChange("P1")));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P1] and changeProfiles=[P1,P2]")
    void trueIfAnnotatedProfilesContainActiveProfile() {
        assertTrue(new SpringbootProfileFilter("P1").filter(getTemplateLoadedChange("P1,P2")));
    }

    @Test
    @DisplayName("SHOULD return true WHEN activeProfiles=[P2] and changeProfiles=[!P1]")
    void trueIfAnnotatedProfileIsNegativeP1AndActiveProfileIsP2() {
        assertTrue(new SpringbootProfileFilter("P2").filter(getTemplateLoadedChange("!P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and changeProfiles=[!P1]")
    void trueIfActiveProfileEmptyAndChangeProfileNegativeP1() {
        assertTrue(new SpringbootProfileFilter().filter(getTemplateLoadedChange("!P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[] and changeProfiles=[P1]")
    void falseIfActiveProfileEmptyAndChangeProfileP1() {
        assertFalse(new SpringbootProfileFilter().filter(getTemplateLoadedChange("P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P2] and changeProfiles=[P1]")
    void falseIfActiveProfileAndChangeProfileDontMatch() {
        assertFalse(new SpringbootProfileFilter("P2").filter(getTemplateLoadedChange("P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1] and changeProfiles=[!P1]")
    void falseIfActiveProfileIsP1AndChangeProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1").filter(getTemplateLoadedChange("!P1")));
    }

    @Test
    @DisplayName("SHOULD return false WHEN activeProfiles=[P1,P2] and changeProfiles=[!P1]")
    void falseIfActiveProfileIsP1P2AndChangeProfileNegativeP1() {
        assertFalse(new SpringbootProfileFilter("P1", "P2").filter(getTemplateLoadedChange("!P1")));
    }

    private AbstractLoadedChange getTemplateLoadedChange() {
        return getTemplateLoadedChange(null);
    }

    private AbstractLoadedChange getTemplateLoadedChange(String profiles) {

        ChangeTemplateFileContent changeFileDescriptor = new ChangeTemplateFileContent(
                "template-base-change-id",
                "test-author",
                "template-simulate",
                profiles,
                true,
                null,
                "dummy-apply",
                null,
                null,
                RecoveryDescriptor.getDefault()
        );

        TemplatePreviewChange preview = PreviewChangeBuilder.getTemplateBuilder("_0001__ChangeUsingTemplate.yaml", changeFileDescriptor).build();

        return LoadedChangeBuilder.getInstance(preview).build();

    }

    @ChangeTemplate(name = "template-simulate")
    public static class TemplateSimulate extends AbstractChangeTemplate<TemplateVoid, TemplateString, TemplateString> {
        public TemplateSimulate() {
            super();
        }

        @RollbackTemplate
        public void rollback() {
        }
    }

    @Change(id = "not-annotated", author = "aperezdieppa")
    public static class _000__NotAnnotated {
    }

    @Profile("P1")
    @Change(id = "annotated-p1", author = "aperezdieppa")
    public static class _001__P1 {
    }

    @Profile("!P1")
    @Change(id = "annotated-!-p1", author = "aperezdieppa")
    public static class _002__NotP1 {
    }

    @Profile({"P1", "P2"})
    @Change(id = "annotated-p1-p2", author = "aperezdieppa")
    public static class _003__P1AndP2 {
    }
}
