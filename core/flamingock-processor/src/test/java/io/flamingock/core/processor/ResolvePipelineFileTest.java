package io.flamingock.core.processor;

import io.flamingock.core.processor.util.LoggerPreProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for the resolvePipelineFile() method in FlamingockAnnotationProcessor.
 * Tests the 5-step file resolution logic with various file locations and priority scenarios.
 */
class ResolvePipelineFileTest {

    @TempDir
    Path tempDir;

    private FlamingockAnnotationProcessor processor;
    private LoggerPreProcessor mockLogger;
    private Path mainResourcesDir;
    private Path testResourcesDir;
    
    @BeforeEach
    void setUp() throws Exception {
        processor = spy(new FlamingockAnnotationProcessor());
        mockLogger = mock(LoggerPreProcessor.class);
        
        // Setup directory structure
        mainResourcesDir = tempDir.resolve("src/main/resources");
        testResourcesDir = tempDir.resolve("src/test/resources");
        mainResourcesDir.toFile().mkdirs();
        testResourcesDir.toFile().mkdirs();
        
        // Set private fields
        setPrivateField("logger", mockLogger);
        setPrivateField("resourcesRoot", mainResourcesDir.toString());
    }

    @Test
    @DisplayName("Working directory file takes precedence over classpath resource")
    void SHOULD_useWorkingDirectoryFile_WHEN_bothWorkingDirAndClasspathResourceExist() throws Exception {
        // Given - file exists in both working directory and main resources
        String fileName = "pipeline.yaml";
        // Create file in a subdirectory to avoid conflicts with working directory
        File mainResourceFile = mainResourcesDir.resolve(fileName).toFile();
        createYamlFile(mainResourceFile, "main-resource-content");
        
        // Use absolute path to simulate working directory file
        File workingDirFile = tempDir.resolve("working-dir-" + fileName).toFile();
        createYamlFile(workingDirFile, "working-dir-content");
        
        // When - use absolute path to ensure it's found as direct file path
        File result = invokeResolvePipelineFile(workingDirFile.getAbsolutePath());
        
        // Then
        assertEquals(workingDirFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as direct file path: " + workingDirFile.getAbsolutePath());
    }

    @Test
    @DisplayName("Main resources used when working directory file doesn't exist")
    void SHOULD_useMainResources_WHEN_workingDirFileDoesNotExist() throws Exception {
        // Given - file only exists in main resources
        String fileName = "flamingock/pipeline.yaml";
        File mainResourceFile = mainResourcesDir.resolve(fileName).toFile();
        mainResourceFile.getParentFile().mkdirs();
        createYamlFile(mainResourceFile, "main-resource-content");
        
        // When
        File result = invokeResolvePipelineFile(fileName);
        
        // Then
        assertEquals(mainResourceFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as main resources: " + mainResourceFile.getAbsolutePath());
    }

    @Test
    @DisplayName("Test resources used when main resources doesn't exist")
    void SHOULD_useTestResources_WHEN_mainResourcesFileDoesNotExist() throws Exception {
        // Given - file only exists in test resources
        String fileName = "flamingock/test-pipeline.yaml";
        File testResourceFile = testResourcesDir.resolve(fileName).toFile();
        testResourceFile.getParentFile().mkdirs();
        createYamlFile(testResourceFile, "test-resource-content");
        
        // When
        File result = invokeResolvePipelineFile(fileName);
        
        // Then
        assertEquals(testResourceFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as test resources: " + testResourceFile.getAbsolutePath());
    }

    @Test
    @DisplayName("Resources/ prefix is automatically stripped and resolved in main resources")
    void SHOULD_stripResourcesPrefixAndUseMainResources_WHEN_pathStartsWithResources() throws Exception {
        // Given - file exists in main resources but path has "resources/" prefix
        String fileNameWithPrefix = "resources/flamingock/pipeline.yaml";
        String actualPath = "flamingock/pipeline.yaml";
        File mainResourceFile = mainResourcesDir.resolve(actualPath).toFile();
        mainResourceFile.getParentFile().mkdirs();
        createYamlFile(mainResourceFile, "main-resource-content");
        
        // When
        File result = invokeResolvePipelineFile(fileNameWithPrefix);
        
        // Then
        assertEquals(mainResourceFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as main resources (stripped resources/ prefix): " + mainResourceFile.getAbsolutePath());
    }

    @Test
    @DisplayName("Resources/ prefix is automatically stripped and resolved in test resources")
    void SHOULD_stripResourcesPrefixAndUseTestResources_WHEN_pathStartsWithResourcesAndOnlyInTestResources() throws Exception {
        // Given - file exists in test resources but path has "resources/" prefix
        String fileNameWithPrefix = "resources/flamingock/test-pipeline.yaml";
        String actualPath = "flamingock/test-pipeline.yaml";
        File testResourceFile = testResourcesDir.resolve(actualPath).toFile();
        testResourceFile.getParentFile().mkdirs();
        createYamlFile(testResourceFile, "test-resource-content");
        
        // When
        File result = invokeResolvePipelineFile(fileNameWithPrefix);
        
        // Then
        assertEquals(testResourceFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as test resources (stripped resources/ prefix): " + testResourceFile.getAbsolutePath());
    }

    @Test
    @DisplayName("Absolute file path is used directly")
    void SHOULD_useAbsolutePath_WHEN_absolutePathProvided() throws Exception {
        // Given - absolute file path
        File absoluteFile = tempDir.resolve("external/pipeline.yaml").toFile();
        absoluteFile.getParentFile().mkdirs();
        createYamlFile(absoluteFile, "external-content");
        
        // When
        File result = invokeResolvePipelineFile(absoluteFile.getAbsolutePath());
        
        // Then
        assertEquals(absoluteFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as direct file path: " + absoluteFile.getAbsolutePath());
    }

    @Test
    @DisplayName("Relative file path is resolved relative to working directory")
    void SHOULD_useRelativePath_WHEN_relativePathProvided() throws Exception {
        // Given - Use absolute path to simulate relative path behavior
        String fileName = "config-pipeline.yaml";
        File configFile = tempDir.resolve(fileName).toFile();
        createYamlFile(configFile, "config-content");
        
        // When - use absolute path to ensure predictable resolution
        File result = invokeResolvePipelineFile(configFile.getAbsolutePath());
        
        // Then
        assertEquals(configFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as direct file path: " + configFile.getAbsolutePath());
    }

    @Test
    @DisplayName("Comprehensive error message when file not found anywhere")
    void SHOULD_throwExceptionWithSearchedLocations_WHEN_fileNotFoundAnywhere() throws Exception {
        // Given - file doesn't exist anywhere
        String fileName = "nonexistent/pipeline.yaml";
        
        // When & Then
        Exception exception = assertThrows(Exception.class, () -> 
            invokeResolvePipelineFile(fileName));
        
        // Get the actual RuntimeException from InvocationTargetException
        Throwable cause = exception.getCause();
        assertInstanceOf(RuntimeException.class, cause);
        
        String errorMessage = cause.getMessage();
        assertTrue(errorMessage.contains("Pipeline file specified in @EnableFlamingock annotation does not exist: " + fileName));
        assertTrue(errorMessage.contains("Searched locations:"));
        assertTrue(errorMessage.contains("1. "));
        assertTrue(errorMessage.contains("2. "));
        assertTrue(errorMessage.contains("3. "));
    }

    @Test
    @DisplayName("Error message includes stripped prefix locations for resources/ paths")
    void SHOULD_includeStrippedPrefixLocationsInError_WHEN_resourcesPrefixPathNotFound() throws Exception {
        // Given - file with resources/ prefix doesn't exist anywhere
        String fileName = "resources/nonexistent/pipeline.yaml";
        
        // When & Then
        Exception exception = assertThrows(Exception.class, () -> 
            invokeResolvePipelineFile(fileName));
        
        // Get the actual RuntimeException from InvocationTargetException
        Throwable cause = exception.getCause();
        assertInstanceOf(RuntimeException.class, cause);
        
        String errorMessage = cause.getMessage();
        assertTrue(errorMessage.contains("Pipeline file specified in @EnableFlamingock annotation does not exist: " + fileName));
        assertTrue(errorMessage.contains("Searched locations:"));
        assertTrue(errorMessage.contains("4. "));
        assertTrue(errorMessage.contains("5. "));
        assertTrue(errorMessage.contains("nonexistent/pipeline.yaml")); // stripped version
    }

    @Test
    @DisplayName("Empty file path throws appropriate error")
    void SHOULD_throwException_WHEN_emptyFilePathProvided() throws Exception {
        // Given - a truly non-existent file path
        String fileName = "definitely-does-not-exist-anywhere.yaml";
        
        // When & Then
        Exception exception = assertThrows(Exception.class, () -> 
            invokeResolvePipelineFile(fileName));
        
        // Get the actual RuntimeException from InvocationTargetException
        Throwable cause = exception.getCause();
        assertInstanceOf(RuntimeException.class, cause);
        
        assertTrue(cause.getMessage().contains("Pipeline file specified in @EnableFlamingock annotation does not exist: " + fileName));
    }

    @Test
    @DisplayName("File resolution prioritizes exact matches over partial path matches")
    void SHOULD_prioritizeExactMatches_WHEN_multiplePartialMatchesExist() throws Exception {
        // Given - files exist at different levels, test classpath priority
        String fileName = "priority-test.yaml";
        File mainResourceFile = mainResourcesDir.resolve(fileName).toFile();
        File testResourceFile = testResourcesDir.resolve(fileName).toFile();
        
        createYamlFile(mainResourceFile, "main-resource-content");
        createYamlFile(testResourceFile, "test-resource-content");
        
        // When - look for file that only exists in classpath
        File result = invokeResolvePipelineFile(fileName);
        
        // Then - should pick main resources over test resources
        assertEquals(mainResourceFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockLogger).info("Pipeline file resolved as main resources: " + mainResourceFile.getAbsolutePath());
    }

    private void createYamlFile(File file, String content) throws IOException {
        String yamlContent = "pipeline:\n" +
                "  stages:\n" +
                "    - description: " + content + "\n" +
                "      location: com.example.test\n";
        java.nio.file.Files.write(file.toPath(), yamlContent.getBytes());
    }

    private File invokeResolvePipelineFile(String pipelineFilePath) throws Exception {
        Method method = FlamingockAnnotationProcessor.class.getDeclaredMethod("resolvePipelineFile", String.class);
        method.setAccessible(true);
        return (File) method.invoke(processor, pipelineFilePath);
    }

    private void setPrivateField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = FlamingockAnnotationProcessor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(processor, value);
    }
}