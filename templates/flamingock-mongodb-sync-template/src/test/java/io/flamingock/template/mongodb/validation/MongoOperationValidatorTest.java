/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.mongodb.validation;

import io.flamingock.template.mongodb.model.MongoOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoOperationValidatorTest {

    private static final String ENTITY_ID = "test-change";

    @Nested
    @DisplayName("Common Validation Tests")
    class CommonValidationTests {

        @Test
        @DisplayName("WHEN operation is null THEN validation fails")
        void nullOperationTest() {
            List<ValidationError> errors = MongoOperationValidator.validate(null, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("WHEN operation type is null THEN validation fails")
        void nullOperationTypeTest() {
            MongoOperation op = new MongoOperation();
            op.setType(null);
            op.setCollection("test");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("type is required"));
        }

        @Test
        @DisplayName("WHEN operation type is empty THEN validation fails")
        void emptyOperationTypeTest() {
            MongoOperation op = new MongoOperation();
            op.setType("");
            op.setCollection("test");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("type is required"));
        }

        @Test
        @DisplayName("WHEN operation type is unknown THEN validation fails")
        void unknownOperationTypeTest() {
            MongoOperation op = new MongoOperation();
            op.setType("unknownType");
            op.setCollection("test");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("Unknown operation type"));
        }

        @Test
        @DisplayName("WHEN collection is null THEN validation fails")
        void nullCollectionTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createCollection");
            op.setCollection(null);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("Collection name is required"));
        }

        @Test
        @DisplayName("WHEN collection is empty THEN validation fails")
        void emptyCollectionTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createCollection");
            op.setCollection("");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("cannot be empty"));
        }

        @Test
        @DisplayName("WHEN collection contains $ THEN validation fails")
        void collectionWithDollarSignTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createCollection");
            op.setCollection("test$collection");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("cannot contain '$'"));
        }

        @Test
        @DisplayName("WHEN collection contains null char THEN validation fails")
        void collectionWithNullCharTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createCollection");
            op.setCollection("test\0collection");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("null character"));
        }
    }

    @Nested
    @DisplayName("Insert Operation Tests")
    class InsertOperationTests {

        @Test
        @DisplayName("WHEN insert missing parameters THEN validation fails")
        void insertMissingParametersTest() {
            MongoOperation op = new MongoOperation();
            op.setType("insert");
            op.setCollection("test");
            op.setParameters(null);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'parameters'"));
        }

        @Test
        @DisplayName("WHEN insert missing documents THEN validation fails")
        void insertMissingDocumentsTest() {
            MongoOperation op = new MongoOperation();
            op.setType("insert");
            op.setCollection("test");
            op.setParameters(new HashMap<>());

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'documents'"));
        }

        @Test
        @DisplayName("WHEN insert has empty documents array THEN validation fails")
        void insertEmptyDocumentsTest() {
            MongoOperation op = new MongoOperation();
            op.setType("insert");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("documents", new ArrayList<>());
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("cannot be empty"));
        }

        @Test
        @DisplayName("WHEN insert has null element in documents THEN validation fails")
        void insertNullDocumentElementTest() {
            MongoOperation op = new MongoOperation();
            op.setType("insert");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            List<Map<String, Object>> docs = new ArrayList<>();
            docs.add(null);
            params.put("documents", docs);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("index 0 is null"));
        }

        @Test
        @DisplayName("WHEN insert has documents as wrong type THEN validation fails")
        void insertDocumentsWrongTypeTest() {
            MongoOperation op = new MongoOperation();
            op.setType("insert");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("documents", "not a list");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("must be a list"));
        }

        @Test
        @DisplayName("WHEN insert is valid THEN validation passes")
        void insertValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("insert");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            List<Map<String, Object>> docs = new ArrayList<>();
            Map<String, Object> doc = new HashMap<>();
            doc.put("name", "Test");
            docs.add(doc);
            params.put("documents", docs);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Update Operation Tests")
    class UpdateOperationTests {

        @Test
        @DisplayName("WHEN update missing parameters THEN validation fails")
        void updateMissingParametersTest() {
            MongoOperation op = new MongoOperation();
            op.setType("update");
            op.setCollection("test");
            op.setParameters(null);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'parameters'"));
        }

        @Test
        @DisplayName("WHEN update missing filter THEN validation fails")
        void updateMissingFilterTest() {
            MongoOperation op = new MongoOperation();
            op.setType("update");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> update = new HashMap<>();
            update.put("$set", new HashMap<>());
            params.put("update", update);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'filter'"));
        }

        @Test
        @DisplayName("WHEN update missing update param THEN validation fails")
        void updateMissingUpdateParamTest() {
            MongoOperation op = new MongoOperation();
            op.setType("update");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("filter", new HashMap<>());
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'update'"));
        }

        @Test
        @DisplayName("WHEN update param is wrong type THEN validation fails")
        void updateParamWrongTypeTest() {
            MongoOperation op = new MongoOperation();
            op.setType("update");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("filter", new HashMap<>());
            params.put("update", "not a document");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("must be a document"));
        }

        @Test
        @DisplayName("WHEN update missing both filter and update THEN both errors reported")
        void updateMissingBothTest() {
            MongoOperation op = new MongoOperation();
            op.setType("update");
            op.setCollection("test");
            op.setParameters(new HashMap<>());

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(2, errors.size());
        }

        @Test
        @DisplayName("WHEN update is valid THEN validation passes")
        void updateValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("update");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("filter", new HashMap<>());
            Map<String, Object> update = new HashMap<>();
            Map<String, Object> setFields = new HashMap<>();
            setFields.put("status", "active");
            update.put("$set", setFields);
            params.put("update", update);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("WHEN update with multi option is valid THEN validation passes")
        void updateWithMultiValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("update");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("filter", new HashMap<>());
            Map<String, Object> update = new HashMap<>();
            update.put("$set", new HashMap<>());
            params.put("update", update);
            params.put("multi", true);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Delete Operation Tests")
    class DeleteOperationTests {

        @Test
        @DisplayName("WHEN delete missing filter THEN validation fails")
        void deleteMissingFilterTest() {
            MongoOperation op = new MongoOperation();
            op.setType("delete");
            op.setCollection("test");
            op.setParameters(new HashMap<>());

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'filter'"));
        }

        @Test
        @DisplayName("WHEN delete has empty filter THEN validation passes")
        void deleteEmptyFilterTest() {
            MongoOperation op = new MongoOperation();
            op.setType("delete");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("filter", new HashMap<>());
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("WHEN delete is valid THEN validation passes")
        void deleteValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("delete");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> filter = new HashMap<>();
            filter.put("name", "Test");
            params.put("filter", filter);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("CreateIndex Operation Tests")
    class CreateIndexOperationTests {

        @Test
        @DisplayName("WHEN createIndex missing parameters THEN validation fails")
        void createIndexMissingParametersTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createIndex");
            op.setCollection("test");
            op.setParameters(null);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'parameters'"));
        }

        @Test
        @DisplayName("WHEN createIndex missing keys THEN validation fails")
        void createIndexMissingKeysTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createIndex");
            op.setCollection("test");
            op.setParameters(new HashMap<>());

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'keys'"));
        }

        @Test
        @DisplayName("WHEN createIndex has empty keys THEN validation fails")
        void createIndexEmptyKeysTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createIndex");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("keys", new HashMap<>());
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("cannot be empty"));
        }

        @Test
        @DisplayName("WHEN createIndex keys is wrong type THEN validation fails")
        void createIndexKeysWrongTypeTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createIndex");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("keys", "not a map");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("must be a map"));
        }

        @Test
        @DisplayName("WHEN createIndex is valid THEN validation passes")
        void createIndexValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createIndex");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> keys = new HashMap<>();
            keys.put("email", 1);
            params.put("keys", keys);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("DropIndex Operation Tests")
    class DropIndexOperationTests {

        @Test
        @DisplayName("WHEN dropIndex missing both indexName and keys THEN validation fails")
        void dropIndexMissingBothTest() {
            MongoOperation op = new MongoOperation();
            op.setType("dropIndex");
            op.setCollection("test");
            op.setParameters(new HashMap<>());

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("'indexName' or 'keys'"));
        }

        @Test
        @DisplayName("WHEN dropIndex has indexName THEN validation passes")
        void dropIndexWithIndexNameTest() {
            MongoOperation op = new MongoOperation();
            op.setType("dropIndex");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("indexName", "email_index");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("WHEN dropIndex has keys THEN validation passes")
        void dropIndexWithKeysTest() {
            MongoOperation op = new MongoOperation();
            op.setType("dropIndex");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> keys = new HashMap<>();
            keys.put("email", 1);
            params.put("keys", keys);
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("RenameCollection Operation Tests")
    class RenameCollectionOperationTests {

        @Test
        @DisplayName("WHEN renameCollection missing target THEN validation fails")
        void renameCollectionMissingTargetTest() {
            MongoOperation op = new MongoOperation();
            op.setType("renameCollection");
            op.setCollection("test");
            op.setParameters(new HashMap<>());

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'target'"));
        }

        @Test
        @DisplayName("WHEN renameCollection target is empty THEN validation fails")
        void renameCollectionEmptyTargetTest() {
            MongoOperation op = new MongoOperation();
            op.setType("renameCollection");
            op.setCollection("test");
            Map<String, Object> params = new HashMap<>();
            params.put("target", "");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("WHEN renameCollection is valid THEN validation passes")
        void renameCollectionValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("renameCollection");
            op.setCollection("oldName");
            Map<String, Object> params = new HashMap<>();
            params.put("target", "newName");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("CreateView Operation Tests")
    class CreateViewOperationTests {

        @Test
        @DisplayName("WHEN createView missing parameters THEN validation fails")
        void createViewMissingParametersTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createView");
            op.setCollection("testView");
            op.setParameters(null);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'parameters'"));
        }

        @Test
        @DisplayName("WHEN createView missing viewOn THEN validation fails")
        void createViewMissingViewOnTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createView");
            op.setCollection("testView");
            Map<String, Object> params = new HashMap<>();
            params.put("pipeline", Collections.singletonList(new HashMap<>()));
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'viewOn'"));
        }

        @Test
        @DisplayName("WHEN createView missing pipeline THEN validation fails")
        void createViewMissingPipelineTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createView");
            op.setCollection("testView");
            Map<String, Object> params = new HashMap<>();
            params.put("viewOn", "sourceCollection");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("requires 'pipeline'"));
        }

        @Test
        @DisplayName("WHEN createView pipeline is wrong type THEN validation fails")
        void createViewPipelineWrongTypeTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createView");
            op.setCollection("testView");
            Map<String, Object> params = new HashMap<>();
            params.put("viewOn", "sourceCollection");
            params.put("pipeline", "not a list");
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("must be a list"));
        }

        @Test
        @DisplayName("WHEN createView is valid THEN validation passes")
        void createViewValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createView");
            op.setCollection("testView");
            Map<String, Object> params = new HashMap<>();
            params.put("viewOn", "sourceCollection");
            params.put("pipeline", Collections.singletonList(new HashMap<>()));
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Simple Operation Tests")
    class SimpleOperationTests {

        @Test
        @DisplayName("WHEN createCollection is valid THEN validation passes")
        void createCollectionValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("createCollection");
            op.setCollection("test");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("WHEN dropCollection is valid THEN validation passes")
        void dropCollectionValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("dropCollection");
            op.setCollection("test");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("WHEN dropView is valid THEN validation passes")
        void dropViewValidTest() {
            MongoOperation op = new MongoOperation();
            op.setType("dropView");
            op.setCollection("testView");

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Multiple Errors Tests")
    class MultipleErrorsTests {

        @Test
        @DisplayName("WHEN multiple validation errors exist THEN all are collected")
        void multipleErrorsTest() {
            MongoOperation op = new MongoOperation();
            op.setType("insert");
            op.setCollection("test$invalid");
            Map<String, Object> params = new HashMap<>();
            params.put("documents", new ArrayList<>());
            op.setParameters(params);

            List<ValidationError> errors = MongoOperationValidator.validate(op, ENTITY_ID);

            assertEquals(2, errors.size()); // collection contains $ and documents is empty
        }
    }
}
