package io.flamingock.internal.core.task.loaded;

import io.flamingock.internal.common.core.error.FlamingockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoadedChangeUnitUtilTest {

    @Test
    @DisplayName("Should return orderInContent when orderInContent is present and no order in fileName")
    void shouldReturnOrderInContentWhenOrderInContentPresentAndNoOrderInFileName() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "001";
        String fileName = "test-file.yml";

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("001", result);
    }

    @Test
    @DisplayName("Should return orderInContent when orderInContent matches order in fileName")
    void shouldReturnOrderInContentWhenOrderInContentMatchesOrderInFileName() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "001";
        String fileName = "_001_test-file.yml";

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("001", result);
    }

    @Test
    @DisplayName("Should throw exception when orderInContent does not match order in fileName")
    void shouldThrowExceptionWhenOrderInContentDoesNotMatchOrderInFileName() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "001";
        String fileName = "_002_test-file.yml";

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName)
        );

        assertEquals("ChangeUnit[test-id] Order mismatch: value in template order field='001' does not match order in fileName='002'",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should return order from fileName when orderInContent is null and order in fileName is present")
    void shouldReturnOrderFromFileNameWhenOrderInContentIsNullAndOrderInFileNameIsPresent() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = null;
        String fileName = "_003_test-file.yml";

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("003", result);
    }

    @Test
    @DisplayName("Should return order from fileName when orderInContent is empty and order in fileName is present")
    void shouldReturnOrderFromFileNameWhenOrderInContentIsEmptyAndOrderInFileNameIsPresent() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "";
        String fileName = "_004_test-file.yml";

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("004", result);
    }

    @Test
    @DisplayName("Should return order from fileName when orderInContent is blank and order in fileName is present")
    void shouldReturnOrderFromFileNameWhenOrderInContentIsBlankAndOrderInFileNameIsPresent() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "   ";
        String fileName = "_005_test-file.yml";

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("005", result);
    }

    @Test
    @DisplayName("Should throw exception when both orderInContent and order in fileName are missing")
    void shouldThrowExceptionWhenBothOrderInContentAndOrderInFileNameAreMissing() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = null;
        String fileName = "test-file.yml";

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName)
        );

        assertEquals("ChangeUnit[test-id] Order is required: order must be present in the template order field or in the fileName(e.g. _0001_test-id.yaml). If present in both, they must have the same value.",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent is empty and no order in fileName")
    void shouldThrowExceptionWhenOrderInContentIsEmptyAndNoOrderInFileName() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "";
        String fileName = "test-file.yml";

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName)
        );

        assertTrue(exception.getMessage().contains("ChangeUnit[test-id] Order is required"));
    }

    @Test
    @DisplayName("Should extract order from fileName with template format (order at beginning)")
    void shouldExtractOrderFromFileNameWithTemplateFormat() {
        // Test template file name formats - order must be at the beginning
        String changeUnitId = "test-id";
        
        // Template format - order at beginning
        String result1 = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, null, "_001_migration.sql");
        assertEquals("001", result1);
        
        // Template format with extension
        String result2 = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, null, "_002_create-users.yaml");
        assertEquals("002", result2);
        
        // Non-numeric order at beginning
        String result3 = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, null, "_alpha_migration.yml");
        assertEquals("alpha", result3);
        
        // Complex order at beginning
        String result4 = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, null, "_v1.2.3_update-schema.yml");
        assertEquals("v1.2.3", result4);
    }

    @Test
    @DisplayName("Should NOT extract order from fileName when order is not at beginning")
    void shouldNotExtractOrderFromFileNameWhenOrderIsNotAtBeginning() {
        // Test cases where order is not at the beginning - should not match
        String changeUnitId = "test-id";
        String orderInContent = "001";
        
        // Order in middle - should use orderInContent
        String result1 = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, "migration_002_something.sql");
        assertEquals("001", result1);
        
        // Order at end - should use orderInContent  
        String result2 = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, "migration_something_003_.sql");
        assertEquals("001", result2);
    }

    @Test
    @DisplayName("Should extract order from className with package format")
    void shouldExtractOrderFromClassNameWithPackageFormat() {
        // Test class name formats - order must be at the beginning of class name after package
        String changeUnitId = "test-id";
        
        // Class format with package
        String result1 = LoadedChangeUnitUtil.getMatchedOrderFromClassName(changeUnitId, null, "com.mycompany.mypackage._001_MyChangeUnit");
        assertEquals("001", result1);
        
        // Class format with deeper package
        String result2 = LoadedChangeUnitUtil.getMatchedOrderFromClassName(changeUnitId, null, "com.example.migrations.v1._002_CreateUsersTable");
        assertEquals("002", result2);
        
        // Non-numeric order
        String result3 = LoadedChangeUnitUtil.getMatchedOrderFromClassName(changeUnitId, null, "com.mycompany._alpha_Migration");
        assertEquals("alpha", result3);
    }

    @Test
    @DisplayName("Should handle null fileName gracefully")
    void shouldHandleNullFileNameGracefully() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "001";
        String fileName = null;

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("001", result);
    }

    @Test
    @DisplayName("Should throw exception when fileName is null and orderInContent is missing")
    void shouldThrowExceptionWhenFileNameIsNullAndOrderInContentIsMissing() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = null;
        String fileName = null;

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () ->
            LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName)
        );

        assertTrue(exception.getMessage().contains("ChangeUnit[test-id] Order is required"));
    }

    @Test
    @DisplayName("Should handle multiple underscores in fileName correctly")
    void shouldHandleMultipleUnderscoresInFileName() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = null;
        String fileName = "_001_migration_with_underscores.yml";

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("001", result); // Should extract order from beginning
    }

    @Test
    @DisplayName("Should handle fileName without proper underscore pattern")
    void shouldHandleFileNameWithoutProperUnderscorePattern() {
        // Given
        String changeUnitId = "test-id";
        String orderInContent = "001";
        String fileName = "migration_incomplete";

        // When
        String result = LoadedChangeUnitUtil.getMatchedOrderFromFile(changeUnitId, orderInContent, fileName);

        // Then
        assertEquals("001", result); // Should return orderInContent since no valid pattern in fileName
    }
}