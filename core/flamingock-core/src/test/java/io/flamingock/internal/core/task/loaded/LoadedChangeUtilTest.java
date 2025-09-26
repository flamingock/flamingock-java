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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.internal.common.core.error.FlamingockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoadedChangeUtilTest {

    @Test
    @DisplayName("Should return orderInContent when orderInContent is present and no order in fileName")
    void shouldReturnOrderInContentWhenOrderInContentPresentAndNoOrderInFileName() {
        // Given
        String changeId = "test-id";
        String orderInContent = "001";
        String fileName = "test-file.yml";

        // When
        String result = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName);

        // Then
        assertEquals("001", result);
    }

    @Test
    @DisplayName("Should return orderInContent when orderInContent matches order in fileName")
    void shouldReturnOrderInContentWhenOrderInContentMatchesOrderInFileName() {
        // Given
        String changeId = "test-id";
        String orderInContent = "001";
        String fileName = "_001__test-file.yml";

        // When
        String result = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName);

        // Then
        assertEquals("001", result);
    }

    @Test
    @DisplayName("Should throw exception when orderInContent does not match order in fileName")
    void shouldThrowExceptionWhenOrderInContentDoesNotMatchOrderInFileName() {
        // Given
        String changeId = "test-id";
        String orderInContent = "001";
        String fileName = "_002__test-file.yml";

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName)
        );

        assertEquals("Change[test-id] Order mismatch: value in template order field='001' does not match order in fileName='002'",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should return order from fileName when orderInContent is null and order in fileName is present")
    void shouldReturnOrderFromFileNameWhenOrderInContentIsNullAndOrderInFileNameIsPresent() {
        // Given
        String changeId = "test-id";
        String orderInContent = null;
        String fileName = "_003__test-file.yml";

        // When
        String result = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName);

        // Then
        assertEquals("003", result);
    }

    @Test
    @DisplayName("Should throw exception when orderInContent is empty and order in fileName is present")
    void shouldThrowExceptionWhenOrderInContentIsEmptyAndOrderInFileNameIsPresent() {
        // Given
        String changeId = "test-id";
        String orderInContent = "";
        String fileName = "_004__test-file.yml";

        // When
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName)
        );

        // Then
        assertEquals("Change[test-id] Order mismatch: value in template order field='' does not match order in fileName='004'",
            exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent is blank and order in fileName is present")
    void shouldThrowExceptionWhenOrderInContentIsBlankAndOrderInFileNameIsPresent() {
        // Given
        String changeId = "test-id";
        String orderInContent = "   ";
        String fileName = "_005__test-file.yml";

        // When
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName)
        );

        // Then
        assertEquals("Change[test-id] Order mismatch: value in template order field='   ' does not match order in fileName='005'",
            exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when both orderInContent and order in fileName are missing")
    void shouldThrowExceptionWhenBothOrderInContentAndOrderInFileNameAreMissing() {
        // Given
        String changeId = "test-id";
        String orderInContent = null;
        String fileName = "test-file.yml";

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName)
        );

        assertEquals("Change[test-id] Order is required: order must be present in the template order field or in the fileName(e.g. _0001__test-id.yaml). If present in both, they must have the same value.",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should return empty string when orderInContent is empty and no order in fileName")
    //TODO: review this behavior - should it throw or return empty?
    void shouldReturnEmptyStringWhenOrderInContentIsEmptyAndNoOrderInFileName() {
        // Given
        String changeId = "test-id";
        String orderInContent = "";
        String fileName = "test-file.yml";

        // When
        String result = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName);

        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("Should extract order from fileName with template format (order at beginning)")
    void shouldExtractOrderFromFileNameWithTemplateFormat() {
        // Test template file name formats - order must be at the beginning
        String changeId = "test-id";

        // Template format - order at beginning
        String result1 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_001__migration.sql");
        assertEquals("001", result1);

        // Template format with extension
        String result2 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_002__create-users.yaml");
        assertEquals("002", result2);

        // Non-numeric order at beginning
        String result3 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_alpha__migration.yml");
        assertEquals("alpha", result3);

        // Complex order at beginning
        String result4 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_v1.2.3__update-schema.yml");
        assertEquals("v1.2.3", result4);
    }

    @Test
    @DisplayName("Should NOT extract order from fileName when order is not at beginning")
    void shouldNotExtractOrderFromFileNameWhenOrderIsNotAtBeginning() {
        // Test cases where order is not at the beginning - should not match
        String changeId = "test-id";
        String orderInContent = "001";

        // Order in middle - should use orderInContent
        String result1 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, "migration_002_something.sql");
        assertEquals("001", result1);

        // Order at end - should use orderInContent
        String result2 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, "migration_something_003_.sql");
        assertEquals("001", result2);
    }

    @Test
    @DisplayName("Should extract order from className with package format")
    void shouldExtractOrderFromClassNameWithPackageFormat() {
        // Test class name formats - order must be at the beginning of class name after package
        String changeId = "test-id";

        // Class format with package
        String result1 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.mycompany.mypackage._001__MyChange");
        assertEquals("001", result1);

        // Class format with deeper package
        String result2 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example.migrations.v1._002__CreateUsersTable");
        assertEquals("002", result2);

        // Non-numeric order
        String result3 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.mycompany._alpha__Migration");
        assertEquals("alpha", result3);
    }

    @Test
    @DisplayName("Should extract order from inner class names (with $ separator)")
    void shouldExtractOrderFromInnerClassNames() {
        // Test inner class formats - order must be at the beginning of inner class name after $ separator
        String changeId = "test-id";

        // Inner class format
        String result1 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "io.flamingock.internal.core.task.loaded.CodeLoadedTaskBuilderTest$_100__noOrderInAnnotation");
        assertEquals("100", result1);

        // Inner class with deeper nesting
        String result2 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example.test.OuterClass$InnerClass$_002__DeepInnerChange");
        assertEquals("002", result2);

        // Static inner class with version order
        String result3 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.mycompany.MyTest$_v1.2.3__StaticInnerChange");
        assertEquals("v1.2.3", result3);

        // Multiple inner classes
        String result4 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example.OuterTest$MiddleClass$_alpha__InnerMostChange");
        assertEquals("alpha", result4);
    }

    @Test
    @DisplayName("Should handle inner class orderInAnnotation matching")
    void shouldHandleInnerClassOrderInAnnotationMatching() {
        // Test when annotation order matches inner class name order
        String changeId = "test-id";
        String orderInAnnotation = "100";
        String className = "io.flamingock.internal.core.task.loaded.CodeLoadedTaskBuilderTest$_100__noOrderInAnnotation";

        String result = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, orderInAnnotation, className);
        assertEquals("100", result);
    }

    @Test
    @DisplayName("Should throw exception when inner class order does not match annotation")
    void shouldThrowExceptionWhenInnerClassOrderDoesNotMatchAnnotation() {
        // Test when annotation order does not match inner class name order
        String changeId = "test-id";
        String orderInAnnotation = "200";
        String className = "io.flamingock.internal.core.task.loaded.CodeLoadedTaskBuilderTest$_100__noOrderInAnnotation";

        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromClassName(changeId, orderInAnnotation, className)
        );

        assertEquals("Change[test-id] Order mismatch: @Change(order='200') does not match order in className='100'",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should handle null fileName gracefully")
    void shouldHandleNullFileNameGracefully() {
        // Given
        String changeId = "test-id";
        String orderInContent = "001";
        String fileName = null;

        // When
        String result = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName);

        // Then
        assertEquals("001", result);
    }

    @Test
    @DisplayName("Should throw exception when fileName is null and orderInContent is missing")
    void shouldThrowExceptionWhenFileNameIsNullAndOrderInContentIsMissing() {
        // Given
        String changeId = "test-id";
        String orderInContent = null;
        String fileName = null;

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName)
        );

        assertTrue(exception.getMessage().contains("Change[test-id] Order is required"));
    }

    @Test
    @DisplayName("Should handle multiple underscores in fileName correctly")
    void shouldHandleMultipleUnderscoresInFileName() {
        // Given
        String changeId = "test-id";
        String orderInContent = null;
        String fileName = "_20250925_01__migrationWithUnderscores.yml";

        // When
        String result = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName);

        // Then
        // With new regex format _ORDER__DESCRIPTION, this extracts ORDER before double underscore
        assertEquals("20250925_01", result); // Should extract ORDER part before __
    }

    @Test
    @DisplayName("Should extract order with underscores from fileName")
    void shouldExtractOrderWithUnderscoresFromFileName() {
        // Given
        String changeId = "test-id";
        String orderInContent = null;

        // Test underscore in order for file names
        String result1 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_V1_2_3__MyChange.yaml");
        assertEquals("V1_2_3", result1);

        String result2 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_1_0_5_BETA__migration.yml");
        assertEquals("1_0_5_BETA", result2);

        String result3 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_RELEASE_1_2__update-schema.sql");
        assertEquals("RELEASE_1_2", result3);
    }

    @Test
    @DisplayName("Should extract order with underscores from className")
    void shouldExtractOrderWithUnderscoresFromClassName() {
        // Given
        String changeId = "test-id";
        String orderInContent = null;

        // Test underscore in order for class names
        String result1 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example._V1_2_3__MyChange");
        assertEquals("V1_2_3", result1);

        String result2 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example.OuterClass$_1_0_5_BETA__InnerChange");
        assertEquals("1_0_5_BETA", result2);

        String result3 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "io.flamingock.test._RELEASE_1_2__Migration");
        assertEquals("RELEASE_1_2", result3);
    }

    @Test
    @DisplayName("Should handle fileName without proper underscore pattern")
    void shouldHandleFileNameWithoutProperUnderscorePattern() {
        // Given
        String changeId = "test-id";
        String orderInContent = "001";
        String fileName = "migration_incomplete";

        // When
        String result = ChangeOrderUtil.getMatchedOrderFromFile(changeId, orderInContent, fileName);

        // Then
        assertEquals("001", result); // Should return orderInContent since no valid pattern in fileName
    }

    @Test
    @DisplayName("Should handle recommended format YYYYMMDD_NN__description")
    void shouldHandleRecommendedDateBasedFormat() {
        // Given
        String changeId = "test-id";

        // Test recommended date-based format
        String result1 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_20250101_01__create_users.yaml");
        assertEquals("20250101_01", result1);

        String result2 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_20250101_02__add_indexes.sql");
        assertEquals("20250101_02", result2);

        String result3 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_20241225_99__christmas_migration.yml");
        assertEquals("20241225_99", result3);
    }

    @Test
    @DisplayName("Should fail with single underscore format")
    void shouldFailWithSingleUnderscoreFormat() {
        // Single underscore should not match anymore
        String changeId = "test-id";

        FlamingockException exception1 = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_001_migration.yaml")
        );
        assertTrue(exception1.getMessage().contains("Order is required"));

        FlamingockException exception2 = assertThrows(FlamingockException.class, () ->
            ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example._001_Migration")
        );
        assertTrue(exception2.getMessage().contains("Order is required"));
    }

    @Test
    @DisplayName("Should handle multiple double underscores in description")
    void shouldHandleMultipleDoubleUnderscoresInDescription() {
        // Given
        String changeId = "test-id";

        // Test files with multiple __ in description part
        String result1 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_001__create__users__table.yaml");
        assertEquals("001", result1);

        String result2 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_V1_2_3__update__schema__structure.sql");
        assertEquals("V1_2_3", result2);

        // Test class names with multiple __ in description part
        String result3 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example._20250101_01__Create__Users__Migration");
        assertEquals("20250101_01", result3);
    }

    @Test
    @DisplayName("Should handle edge cases with minimal valid patterns")
    void shouldHandleEdgeCasesWithMinimalValidPatterns() {
        // Given
        String changeId = "test-id";

        // Minimal valid patterns
        String result1 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_a__b.yml");
        assertEquals("a", result1);

        String result2 = ChangeOrderUtil.getMatchedOrderFromFile(changeId, null, "_1__2.yaml");
        assertEquals("1", result2);

        // Class name minimal patterns
        String result3 = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, "com.example._x__Y");
        assertEquals("x", result3);
    }
}
