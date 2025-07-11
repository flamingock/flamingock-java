package io.flamingock.core.processor;

import io.flamingock.api.StageType;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.core.processor.util.AnnotationFinder;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.SystemPreviewStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Output-focused tests for pipeline processing functionality.
 * Tests verify actual pipeline structure and behavior rather than internal implementation details.
 */
public class PipelinePreProcessorTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    /**
     * Test annotation-based pipeline configuration creates correct pipeline structure.
     */
    @Test
    @DisplayName("Should create correct pipeline structure for annotation-based configuration")
    void shouldCreateCorrectPipelineStructureForAnnotationConfiguration() throws Exception {
        // Given - create annotation with stages including system stage
        EnableFlamingock annotation = createMockAnnotationWithStages();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        
        // When - build pipeline from annotation using processor logic
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        PreviewPipeline pipeline = buildPipelineFromAnnotation(processor, annotation, changeUnits);
        
        // Then - verify the pipeline structure
        assertNotNull(pipeline, "Pipeline should be created");
        assertNotNull(pipeline.getStages(), "Pipeline should have stages");
        assertEquals(2, pipeline.getStages().size(), "Should have 2 regular stages");
        
        // Verify system stage
        PreviewStage systemStage = pipeline.getSystemStage();
        assertNotNull(systemStage, "Should have system stage");
        assertEquals("com.example.system", systemStage.getSourcesPackage());
        assertEquals("SystemStage", systemStage.getName());
        
        // Verify regular stages
        java.util.Collection<PreviewStage> stagesCollection = pipeline.getStages();
        PreviewStage[] stages = stagesCollection.toArray(new PreviewStage[0]);
        assertEquals(2, stages.length, "Should have 2 regular stages");
        
        PreviewStage firstStage = stages[0];
        assertEquals("init", firstStage.getName()); // Should be derived from location
        assertEquals(StageType.LEGACY, firstStage.getType());
        assertEquals("com.example.init", firstStage.getSourcesPackage());
        
        PreviewStage secondStage = stages[1];
        assertEquals("migrations", secondStage.getName()); // Should be derived from location
        assertEquals(StageType.DEFAULT, secondStage.getType());
        assertEquals("com.example.migrations", secondStage.getSourcesPackage());
    }

    /**
     * Test file-based pipeline configuration creates correct pipeline structure.
     */
    @Test
    @DisplayName("Should create correct pipeline structure for file-based configuration")
    void shouldCreateCorrectPipelineStructureForFileConfiguration() throws Exception {
        // Given - create pipeline YAML file
        createPipelineYamlFile();
        EnableFlamingock annotation = createMockAnnotationWithFile("pipeline.yaml");
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        
        // When - build pipeline from file using processor logic
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        PreviewPipeline pipeline = buildPipelineFromFile(processor, annotation, changeUnits);
        
        // Then - verify the pipeline structure
        assertNotNull(pipeline, "Pipeline should be created");
        assertNotNull(pipeline.getStages(), "Pipeline should have stages");
        assertEquals(1, pipeline.getStages().size(), "Should have 1 regular stage from file");
        
        // Verify system stage
        PreviewStage systemStage = pipeline.getSystemStage();
        assertNotNull(systemStage, "Should have system stage");
        assertEquals("com.example.system", systemStage.getSourcesPackage());
        assertEquals("SystemStage", systemStage.getName());
        
        // Verify regular stages
        PreviewStage[] stages = pipeline.getStages().toArray(new PreviewStage[0]);
        PreviewStage stage = stages[0];
        assertEquals("changes", stage.getName()); // Should be auto-derived from location
        assertEquals("com.example.changes", stage.getSourcesPackage());
    }

    /**
     * Test error handling for invalid annotation configuration.
     */
    @Test
    @DisplayName("Should throw error for invalid annotation configuration")
    void shouldThrowErrorForInvalidAnnotationConfiguration() throws Exception {
        // Given - create invalid @EnableFlamingock annotation (neither file nor stages)
        EnableFlamingock invalidAnnotation = createMockAnnotationWithNeitherFileNorStages();
        Map<String, List<AbstractPreviewTask>> changeUnits = new HashMap<>();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        
        // When & Then - should throw RuntimeException from the main validation method
        Exception exception = assertThrows(Exception.class, () -> 
            callGetPipelineFromProcessChanges(processor, changeUnits, invalidAnnotation));
        
        // Check if it's wrapped in InvocationTargetException
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) {
            assertTrue(cause.getMessage().contains("must specify either pipelineFile OR stages"),
                    "Should have error about missing configuration");
        } else {
            assertTrue(exception.getMessage().contains("must specify either pipelineFile OR stages"),
                    "Should have error about missing configuration");
        }
    }

    /**
     * Test pipeline object structure is correct (without JSON serialization issues).
     */
    @Test
    @DisplayName("Should create pipeline with correct object structure")
    void shouldCreatePipelineWithCorrectObjectStructure() throws Exception {
        // Given - create a pipeline
        EnableFlamingock annotation = createMockAnnotationWithStages();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        PreviewPipeline pipeline = buildPipelineFromAnnotation(processor, annotation, changeUnits);
        
        // Then - verify object structure (testing the actual objects, not JSON)
        assertNotNull(pipeline, "Pipeline should be created");
        assertNotNull(pipeline.getStages(), "Pipeline should have stages");
        assertEquals(2, pipeline.getStages().size(), "Pipeline should have 2 regular stages");
        
        PreviewStage systemStage = pipeline.getSystemStage();
        assertNotNull(systemStage, "Pipeline should have system stage");
        assertEquals("com.example.system", systemStage.getSourcesPackage());
        
        PreviewStage[] stages = pipeline.getStages().toArray(new PreviewStage[0]);
        PreviewStage firstStage = stages[0];
        assertEquals("init", firstStage.getName());
        assertEquals(StageType.LEGACY, firstStage.getType());
    }

    /**
     * Test validation that only one SYSTEM stage is allowed in annotation configuration.
     */
    @Test
    @DisplayName("Should throw error for multiple SYSTEM stages in annotation configuration")
    void shouldThrowErrorForMultipleSystemStagesInAnnotation() throws Exception {
        // Given - create annotation with multiple system stages
        EnableFlamingock annotation = new MockFlamingockBuilder()
            .withStages(
                createMockStage("", StageType.SYSTEM, "com.example.system1"),
                createMockStage("", StageType.SYSTEM, "com.example.system2"),
                createMockStage("", StageType.DEFAULT, "com.example.migrations")
            )
            .build();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        
        // When & Then - should throw RuntimeException
        Exception exception = assertThrows(Exception.class, () -> 
            callGetPipelineFromProcessChanges(processor, changeUnits, annotation));
        
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) {
            assertTrue(cause.getMessage().contains("Multiple SYSTEM stages are not allowed"),
                    "Should have error about multiple SYSTEM stages");
        } else {
            assertTrue(exception.getMessage().contains("Multiple SYSTEM stages are not allowed"),
                    "Should have error about multiple SYSTEM stages");
        }
    }

    /**
     * Test validation that only one LEGACY stage is allowed in annotation configuration.
     */
    @Test
    @DisplayName("Should throw error for multiple LEGACY stages in annotation configuration")
    void shouldThrowErrorForMultipleLegacyStagesInAnnotation() throws Exception {
        // Given - create annotation with multiple legacy stages
        EnableFlamingock annotation = new MockFlamingockBuilder()
            .withStages(
                createMockStage("", StageType.LEGACY, "com.example.legacy1"),
                createMockStage("", StageType.LEGACY, "com.example.legacy2"),
                createMockStage("", StageType.DEFAULT, "com.example.migrations")
            )
            .build();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        
        // When & Then - should throw RuntimeException
        Exception exception = assertThrows(Exception.class, () -> 
            callGetPipelineFromProcessChanges(processor, changeUnits, annotation));
        
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) {
            assertTrue(cause.getMessage().contains("Multiple LEGACY stages are not allowed"),
                    "Should have error about multiple LEGACY stages");
        } else {
            assertTrue(exception.getMessage().contains("Multiple LEGACY stages are not allowed"),
                    "Should have error about multiple LEGACY stages");
        }
    }

    /**
     * Test validation that multiple DEFAULT stages are allowed.
     */
    @Test
    @DisplayName("Should allow multiple DEFAULT stages in annotation configuration")
    void shouldAllowMultipleDefaultStagesInAnnotation() throws Exception {
        // Given - create annotation with multiple default stages
        EnableFlamingock annotation = new MockFlamingockBuilder()
            .withStages(
                createMockStage("", StageType.DEFAULT, "com.example.migrations1"),
                createMockStage("", StageType.DEFAULT, "com.example.migrations2"),
                createMockStage("", StageType.DEFAULT, "com.example.migrations3")
            )
            .build();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        
        // When - build pipeline (should not throw exception)
        PreviewPipeline pipeline = callGetPipelineFromProcessChanges(processor, changeUnits, annotation);
        
        // Then - verify multiple default stages are allowed
        assertNotNull(pipeline, "Pipeline should be created");
        assertEquals(3, pipeline.getStages().size(), "Should have 3 default stages");
        assertNull(pipeline.getSystemStage(), "Should not have system stage");
    }

    /**
     * Test that stages are ordered correctly regardless of declaration order.
     */
    @Test
    @DisplayName("Should order stages by type priority: LEGACY before DEFAULT")
    void shouldOrderStagesByTypePriorityLegacyBeforeDefault() throws Exception {
        // Given - create annotation with stages in reverse order (DEFAULT first, LEGACY second)
        EnableFlamingock annotation = new MockFlamingockBuilder()
            .withStages(
                createMockStage("", StageType.DEFAULT, "com.example.migrations"),
                createMockStage("", StageType.LEGACY, "com.example.init"),
                createMockStage("", StageType.DEFAULT, "com.example.cleanup")
            )
            .build();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        
        // When - build pipeline from annotation
        PreviewPipeline pipeline = buildPipelineFromAnnotation(processor, annotation, changeUnits);
        
        // Then - verify stages are sorted by type priority
        assertNotNull(pipeline, "Pipeline should be created");
        assertEquals(3, pipeline.getStages().size(), "Should have 3 stages");
        
        PreviewStage[] stages = pipeline.getStages().toArray(new PreviewStage[0]);
        
        // First stage should be LEGACY (highest priority)
        assertEquals(StageType.LEGACY, stages[0].getType());
        assertEquals("init", stages[0].getName());
        assertEquals("com.example.init", stages[0].getSourcesPackage());
        
        // Second and third stages should be DEFAULT (lower priority)
        assertEquals(StageType.DEFAULT, stages[1].getType());
        assertEquals("migrations", stages[1].getName());
        assertEquals("com.example.migrations", stages[1].getSourcesPackage());
        
        assertEquals(StageType.DEFAULT, stages[2].getType());
        assertEquals("cleanup", stages[2].getName());
        assertEquals("com.example.cleanup", stages[2].getSourcesPackage());
    }

    /**
     * Test that YAML stages are ordered correctly regardless of declaration order.
     */
    @Test
    @DisplayName("Should order YAML stages by type priority: LEGACY before DEFAULT")
    void shouldOrderYamlStagesByTypePriorityLegacyBeforeDefault() throws Exception {
        // Given - create YAML file with stages in reverse order (DEFAULT first, LEGACY second)
        Path pipelineFile = tempDir.resolve("pipeline.yaml");
        String yamlContent = "pipeline:\n" +
            "  stages:\n" +
            "    - location: com.example.migrations\n" +
            "    - location: com.example.init\n" +
            "      type: legacy\n" +
            "    - location: com.example.cleanup\n";
        Files.write(pipelineFile, yamlContent.getBytes());
        
        EnableFlamingock annotation = createMockAnnotationWithFile("pipeline.yaml");
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        
        // When - build pipeline from file
        PreviewPipeline pipeline = buildPipelineFromFile(processor, annotation, changeUnits);
        
        // Then - verify stages are sorted by type priority
        assertNotNull(pipeline, "Pipeline should be created");
        assertEquals(3, pipeline.getStages().size(), "Should have 3 stages");
        
        PreviewStage[] stages = pipeline.getStages().toArray(new PreviewStage[0]);
        
        // First stage should be LEGACY (highest priority)
        assertEquals(StageType.LEGACY, stages[0].getType());
        assertEquals("init", stages[0].getName());
        assertEquals("com.example.init", stages[0].getSourcesPackage());
        
        // Second and third stages should be DEFAULT (lower priority)
        assertEquals(StageType.DEFAULT, stages[1].getType());
        assertEquals("migrations", stages[1].getName());
        assertEquals("com.example.migrations", stages[1].getSourcesPackage());
        
        assertEquals(StageType.DEFAULT, stages[2].getType());
        assertEquals("cleanup", stages[2].getName());
        assertEquals("com.example.cleanup", stages[2].getSourcesPackage());
    }

    /**
     * Test validation for YAML pipeline with multiple SYSTEM stages.
     */
    @Test
    @DisplayName("Should throw error for multiple SYSTEM stages in YAML configuration")
    void shouldThrowErrorForMultipleSystemStagesInYaml() throws Exception {
        // Given - create YAML file with multiple system stages
        Path pipelineFile = tempDir.resolve("pipeline.yaml");
        String yamlContent = "pipeline:\n" +
            "  stages:\n" +
            "    - location: com.example.system1\n" +
            "      type: importer\n" +
            "    - location: com.example.system2\n" +
            "      type: importer\n" +
            "    - location: com.example.changes\n";
        Files.write(pipelineFile, yamlContent.getBytes());
        
        EnableFlamingock annotation = createMockAnnotationWithFile("pipeline.yaml");
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        
        // When & Then - should throw RuntimeException
        Exception exception = assertThrows(Exception.class, () -> 
            callGetPipelineFromProcessChanges(processor, changeUnits, annotation));
        
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) {
            assertTrue(cause.getMessage().contains("Multiple SYSTEM stages are not allowed"),
                    "Should have error about multiple SYSTEM stages in YAML");
        } else {
            assertTrue(exception.getMessage().contains("Multiple SYSTEM stages are not allowed"),
                    "Should have error about multiple SYSTEM stages in YAML");
        }
    }

    // Helper methods using reflection to test the internal pipeline building logic
    private PreviewPipeline buildPipelineFromAnnotation(FlamingockAnnotationProcessor processor, EnableFlamingock annotation, Map<String, List<AbstractPreviewTask>> changeUnits) throws Exception {
        // Set up minimal processor state
        setProcessorField(processor, "resourcesRoot", tempDir.toString());
        setProcessorField(processor, "sourceRoots", Collections.singletonList(tempDir.toString()));
        
        // Initialize logger field
        javax.annotation.processing.ProcessingEnvironment mockEnv = createMockProcessingEnvironment();
        setProcessorField(processor, "logger", new io.flamingock.core.processor.util.LoggerPreProcessor(mockEnv));
        
        java.lang.reflect.Method method = FlamingockAnnotationProcessor.class.getDeclaredMethod(
            "buildPipelineFromAnnotation", EnableFlamingock.class, Map.class);
        method.setAccessible(true);
        return (PreviewPipeline) method.invoke(processor, annotation, changeUnits);
    }

    private PreviewPipeline callGetPipelineFromProcessChanges(FlamingockAnnotationProcessor processor, Map<String, List<AbstractPreviewTask>> changeUnits, EnableFlamingock annotation) throws Exception {
        // Set up minimal processor state
        setProcessorField(processor, "resourcesRoot", tempDir.toString());
        setProcessorField(processor, "sourceRoots", Collections.singletonList(tempDir.toString()));
        
        // Initialize logger field
        javax.annotation.processing.ProcessingEnvironment mockEnv = createMockProcessingEnvironment();
        setProcessorField(processor, "logger", new io.flamingock.core.processor.util.LoggerPreProcessor(mockEnv));
        
        java.lang.reflect.Method method = FlamingockAnnotationProcessor.class.getDeclaredMethod(
            "getPipelineFromProcessChanges", Map.class, EnableFlamingock.class);
        method.setAccessible(true);
        return (PreviewPipeline) method.invoke(processor, changeUnits, annotation);
    }

    private PreviewPipeline buildPipelineFromFile(FlamingockAnnotationProcessor processor, EnableFlamingock annotation, Map<String, List<AbstractPreviewTask>> changeUnits) throws Exception {
        // Set up minimal processor state
        setProcessorField(processor, "resourcesRoot", tempDir.toString());
        setProcessorField(processor, "sourceRoots", Collections.singletonList(tempDir.toString()));
        
        // Initialize logger field
        javax.annotation.processing.ProcessingEnvironment mockEnv = createMockProcessingEnvironment();
        setProcessorField(processor, "logger", new io.flamingock.core.processor.util.LoggerPreProcessor(mockEnv));
        
        java.lang.reflect.Method method = FlamingockAnnotationProcessor.class.getDeclaredMethod(
            "buildPipelineFromSpecifiedFile", File.class, Map.class);
        method.setAccessible(true);
        
        File pipelineFile = tempDir.resolve("pipeline.yaml").toFile();
        return (PreviewPipeline) method.invoke(processor, pipelineFile, changeUnits);
    }

    private void setProcessorField(FlamingockAnnotationProcessor processor, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = FlamingockAnnotationProcessor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(processor, value);
    }
    
    private javax.annotation.processing.ProcessingEnvironment createMockProcessingEnvironment() {
        return new javax.annotation.processing.ProcessingEnvironment() {
            @Override
            public java.util.Map<String, String> getOptions() {
                return new java.util.HashMap<>();
            }
            
            @Override
            public javax.annotation.processing.Messager getMessager() {
                return new javax.annotation.processing.Messager() {
                    @Override
                    public void printMessage(javax.tools.Diagnostic.Kind kind, CharSequence msg) {
                        // Mock implementation - do nothing
                    }
                    
                    @Override
                    public void printMessage(javax.tools.Diagnostic.Kind kind, CharSequence msg, javax.lang.model.element.Element e) {
                        // Mock implementation - do nothing
                    }
                    
                    @Override
                    public void printMessage(javax.tools.Diagnostic.Kind kind, CharSequence msg, javax.lang.model.element.Element e, javax.lang.model.element.AnnotationMirror a) {
                        // Mock implementation - do nothing
                    }
                    
                    @Override
                    public void printMessage(javax.tools.Diagnostic.Kind kind, CharSequence msg, javax.lang.model.element.Element e, javax.lang.model.element.AnnotationMirror a, javax.lang.model.element.AnnotationValue v) {
                        // Mock implementation - do nothing
                    }
                };
            }
            
            @Override
            public javax.annotation.processing.Filer getFiler() {
                return null;
            }
            
            @Override
            public javax.lang.model.util.Elements getElementUtils() {
                return null;
            }
            
            @Override
            public javax.lang.model.util.Types getTypeUtils() {
                return null;
            }
            
            @Override
            public javax.lang.model.SourceVersion getSourceVersion() {
                return javax.lang.model.SourceVersion.RELEASE_8;
            }
            
            @Override
            public java.util.Locale getLocale() {
                return java.util.Locale.getDefault();
            }
        };
    }

    private void createPipelineYamlFile() throws IOException {
        Path pipelineFile = tempDir.resolve("pipeline.yaml");
        String yamlContent = "pipeline:\n" +
            "  stages:\n" +
            "    - location: com.example.system\n" +
            "      type: importer\n" +
            "    - location: com.example.changes\n";
        Files.write(pipelineFile, yamlContent.getBytes());
    }

    private Map<String, List<AbstractPreviewTask>> createMockChangeUnitsMap() {
        Map<String, List<AbstractPreviewTask>> map = new HashMap<>();
        // Create mock tasks for each package so stages can be built
        AbstractPreviewTask mockTask = new AbstractPreviewTask("mock-task", "001", "test-source", false, true, false) {};
        
        map.put("com.example.system", Collections.singletonList(mockTask));
        map.put("com.example.system1", Collections.singletonList(mockTask));
        map.put("com.example.system2", Collections.singletonList(mockTask));
        map.put("com.example.init", Collections.singletonList(mockTask));
        map.put("com.example.legacy1", Collections.singletonList(mockTask));
        map.put("com.example.legacy2", Collections.singletonList(mockTask));
        map.put("com.example.migrations", Collections.singletonList(mockTask));
        map.put("com.example.migrations1", Collections.singletonList(mockTask));
        map.put("com.example.migrations2", Collections.singletonList(mockTask));
        map.put("com.example.migrations3", Collections.singletonList(mockTask));
        map.put("com.example.changes", Collections.singletonList(mockTask));
        map.put("com.example.cleanup", Collections.singletonList(mockTask));
        return map;
    }

    // Mock annotation factories
    private EnableFlamingock createMockAnnotationWithStages() {
        return new MockFlamingockBuilder()
            .withStages(
                createMockStage("", StageType.SYSTEM, "com.example.system"),
                createMockStage("", StageType.LEGACY, "com.example.init"),
                createMockStage("", StageType.DEFAULT, "com.example.migrations")
            )
            .build();
    }

    private EnableFlamingock createMockAnnotationWithFile(String fileName) {
        return new MockFlamingockBuilder()
            .withPipelineFile(tempDir.resolve(fileName).toString())
            .build();
    }

    private EnableFlamingock createMockAnnotationWithNeitherFileNorStages() {
        return new MockFlamingockBuilder().build();
    }

    private Stage createMockStage(String name, StageType type, String location) {
        return new Stage() {
            @Override public String name() { return name; }
            @Override public String description() { return ""; }
            @Override public StageType type() { return type; }
            @Override public String location() { return location; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Stage.class; }
        };
    }


    private static class MockFlamingockBuilder {
        private Stage[] stages = new Stage[0];
        private String pipelineFile = "";

        public MockFlamingockBuilder withStages(Stage... stages) {
            this.stages = stages;
            return this;
        }

        public MockFlamingockBuilder withPipelineFile(String pipelineFile) {
            this.pipelineFile = pipelineFile;
            return this;
        }

        public EnableFlamingock build() {
            return new EnableFlamingock() {
                @Override public Stage[] stages() { return stages; }
                @Override public String pipelineFile() { return pipelineFile; }
                @Override public io.flamingock.api.SetupType setup() { return io.flamingock.api.SetupType.DEFAULT; }
                @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return EnableFlamingock.class; }
            };
        }
    }
}