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
package io.flamingock.cloud.api.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flamingock.api.StageCategory;
import io.flamingock.cloud.api.vo.CloudChangeStatus;
import io.flamingock.cloud.api.vo.CloudStageStatus;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-contract serialization tests for {@link ClientSubmissionRequest}. Pins the JSON shape
 * consumed by the cloud server. A divergence here is the canonical "wire mismatch" symptom.
 */
class ClientSubmissionRequestSerializationTest {

    // Match production mapper configuration (see JsonObjectMapper.DEFAULT_INSTANCE in
    // flamingock-java-general-util): unknown properties are silently ignored on the wire.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    @DisplayName("Serializes blocks in input order with category + stages")
    void serializesBlocksWithCategoryAndStages() throws Exception {
        StageRequest sysStage = new StageRequest(
                "system-stage", 0, CloudStageStatus.NOT_STARTED,
                Collections.singletonList(new ChangeRequest("sys-c1", CloudTargetSystemAuditMarkType.NONE, null, false, false)));
        StageRequest userStage = new StageRequest(
                "user-stage", 1, CloudStageStatus.NOT_STARTED,
                Collections.singletonList(new ChangeRequest("user-c1", CloudTargetSystemAuditMarkType.NONE, null, false, false)));

        ClientSubmissionRequest request = new ClientSubmissionRequest(Arrays.asList(
                new StageBlockRequest(StageCategory.SYSTEM, Collections.singletonList(sysStage)),
                new StageBlockRequest(StageCategory.DEFAULT, Collections.singletonList(userStage))));

        JsonNode json = MAPPER.valueToTree(request);

        assertNotNull(json.get("blocks"));
        assertEquals(2, json.get("blocks").size());

        JsonNode block0 = json.get("blocks").get(0);
        assertEquals("SYSTEM", block0.get("category").asText());
        assertEquals(1, block0.get("stages").size());
        assertEquals("system-stage", block0.get("stages").get(0).get("name").asText());

        JsonNode block1 = json.get("blocks").get(1);
        assertEquals("DEFAULT", block1.get("category").asText());
        assertEquals(1, block1.get("stages").size());
        assertEquals("user-stage", block1.get("stages").get(0).get("name").asText());
    }

    @Test
    @DisplayName("Round-trips through Jackson preserving block order and contents")
    void roundTripsPreservingBlockOrderAndContents() throws Exception {
        ClientSubmissionRequest original = new ClientSubmissionRequest(Arrays.asList(
                new StageBlockRequest(StageCategory.LEGACY, Collections.singletonList(
                        new StageRequest("legacy", 0, CloudStageStatus.COMPLETED,
                                Collections.singletonList(new ChangeRequest("legacy-c1", CloudTargetSystemAuditMarkType.APPLIED, null, true, false))))),
                new StageBlockRequest(StageCategory.DEFAULT, Arrays.asList(
                        new StageRequest("user-a", 1, CloudStageStatus.STARTED,
                                Collections.singletonList(new ChangeRequest("user-a-c1", CloudTargetSystemAuditMarkType.NONE, null, false, false))),
                        new StageRequest("user-b", 2, CloudStageStatus.NOT_STARTED,
                                Collections.singletonList(new ChangeRequest("user-b-c1", CloudTargetSystemAuditMarkType.NONE, null, false, false)))))));

        String json = MAPPER.writeValueAsString(original);
        ClientSubmissionRequest deserialized = MAPPER.readValue(json, ClientSubmissionRequest.class);

        assertEquals(original, deserialized);
        // Block order is significant and preserved verbatim.
        assertEquals(2, deserialized.getBlocks().size());
        assertEquals(StageCategory.LEGACY, deserialized.getBlocks().get(0).getCategory());
        assertEquals(StageCategory.DEFAULT, deserialized.getBlocks().get(1).getCategory());
        assertEquals(2, deserialized.getBlocks().get(1).getStages().size());
        assertEquals("user-a", deserialized.getBlocks().get(1).getStages().get(0).getName());
        assertEquals("user-b", deserialized.getBlocks().get(1).getStages().get(1).getName());
    }

    @Test
    @DisplayName("Same StageCategory repeated across multiple blocks is preserved on the wire (multi-block-same-category lock-in)")
    void sameStageCategoryAcrossMultipleBlocksIsPreserved() throws Exception {
        StageRequest a = new StageRequest("user-a", 0, CloudStageStatus.NOT_STARTED,
                Collections.singletonList(new ChangeRequest("a-c1", CloudTargetSystemAuditMarkType.NONE, null, false, false)));
        StageRequest b = new StageRequest("user-b", 1, CloudStageStatus.NOT_STARTED,
                Collections.singletonList(new ChangeRequest("b-c1", CloudTargetSystemAuditMarkType.NONE, null, false, false)));

        ClientSubmissionRequest request = new ClientSubmissionRequest(Arrays.asList(
                new StageBlockRequest(StageCategory.DEFAULT, Collections.singletonList(a)),
                new StageBlockRequest(StageCategory.DEFAULT, Collections.singletonList(b))));

        String json = MAPPER.writeValueAsString(request);
        ClientSubmissionRequest deserialized = MAPPER.readValue(json, ClientSubmissionRequest.class);

        // Two distinct blocks of the same StageCategory, NOT collapsed into one.
        assertEquals(2, deserialized.getBlocks().size());
        assertEquals(StageCategory.DEFAULT, deserialized.getBlocks().get(0).getCategory());
        assertEquals(StageCategory.DEFAULT, deserialized.getBlocks().get(1).getCategory());
        assertEquals("user-a", deserialized.getBlocks().get(0).getStages().get(0).getName());
        assertEquals("user-b", deserialized.getBlocks().get(1).getStages().get(0).getName());
    }

    @Test
    @DisplayName("Empty blocks list serializes and deserializes cleanly")
    void emptyBlocksListRoundTrips() throws Exception {
        ClientSubmissionRequest original = new ClientSubmissionRequest(Collections.<StageBlockRequest>emptyList());

        String json = MAPPER.writeValueAsString(original);
        ClientSubmissionRequest deserialized = MAPPER.readValue(json, ClientSubmissionRequest.class);

        assertNotNull(deserialized.getBlocks());
        assertTrue(deserialized.getBlocks().isEmpty());
    }

    @Test
    @DisplayName("ChangeRequest round-trips runAlways flag")
    void changeRequestRoundTripsRunAlwaysFlag() throws Exception {
        ChangeRequest change = new ChangeRequest(
                "repeatable-c1",
                CloudTargetSystemAuditMarkType.APPLIED,
                CloudChangeStatus.ALREADY_APPLIED,
                false,
                true);

        String json = MAPPER.writeValueAsString(change);
        ChangeRequest deserialized = MAPPER.readValue(json, ChangeRequest.class);

        assertTrue(deserialized.isRunAlways());
        assertEquals(change, deserialized);
    }

    @Test
    @DisplayName("Old flat 'stages' field at top level is silently ignored (clean cut — no fallback)")
    void oldFlatStagesFieldIsIgnored() throws Exception {
        // Wire format from a hypothetical old client: top-level `stages` instead of `blocks`.
        // The new server-side DTO has no `stages` getter/setter, so the field is dropped.
        String oldFormat = "{\"stages\":[{\"name\":\"legacy-flat\",\"order\":0}]}";
        ClientSubmissionRequest deserialized = MAPPER.readValue(oldFormat, ClientSubmissionRequest.class);

        // No fallback — blocks is null (or empty) because the old field was ignored.
        assertNull(deserialized.getBlocks());
    }
}
