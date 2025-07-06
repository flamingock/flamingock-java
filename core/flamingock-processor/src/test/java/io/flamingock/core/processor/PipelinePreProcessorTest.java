package io.flamingock.core.processor;

import io.flamingock.api.StageType;
import io.flamingock.api.annotations.Flamingock;
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
        // Given - create annotation with stages
        Flamingock annotation = createMockAnnotationWithStages();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        
        // When - build pipeline from annotation using processor logic
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        PreviewPipeline pipeline = buildPipelineFromAnnotation(processor, annotation, changeUnits);
        
        // Then - verify the pipeline structure
        assertNotNull(pipeline, "Pipeline should be created");
        assertNotNull(pipeline.getStages(), "Pipeline should have stages");
        assertEquals(2, pipeline.getStages().size(), "Should have 2 stages");
        
        // Verify system stage
        PreviewStage systemStage = pipeline.getSystemStage();
        assertNotNull(systemStage, "Should have system stage");
        assertEquals("com.example.system", systemStage.getSourcesPackage());
        
        // Verify regular stages
        java.util.Collection<PreviewStage> stagesCollection = pipeline.getStages();
        PreviewStage[] stages = stagesCollection.toArray(new PreviewStage[0]);
        assertEquals(2, stages.length, "Should have 2 stages");
        
        PreviewStage firstStage = stages[0];
        assertEquals("init", firstStage.getName()); // Should be derived from location
        assertEquals(StageType.DEFAULT, firstStage.getType()); // Using DEFAULT as BEFORE doesn't exist
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
        Flamingock annotation = createMockAnnotationWithFile("pipeline.yaml");
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        
        // When - build pipeline from file using processor logic
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        PreviewPipeline pipeline = buildPipelineFromFile(processor, annotation, changeUnits);
        
        // Then - verify the pipeline structure
        assertNotNull(pipeline, "Pipeline should be created");
        assertNotNull(pipeline.getStages(), "Pipeline should have stages");
        assertEquals(1, pipeline.getStages().size(), "Should have 1 stage from file");
        
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
        // Given - create invalid @Flamingock annotation (neither file nor stages)
        Flamingock invalidAnnotation = createMockAnnotationWithNeitherFileNorStages();
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
        Flamingock annotation = createMockAnnotationWithStages();
        Map<String, List<AbstractPreviewTask>> changeUnits = createMockChangeUnitsMap();
        FlamingockAnnotationProcessor processor = new FlamingockAnnotationProcessor();
        PreviewPipeline pipeline = buildPipelineFromAnnotation(processor, annotation, changeUnits);
        
        // Then - verify object structure (testing the actual objects, not JSON)
        assertNotNull(pipeline, "Pipeline should be created");
        assertNotNull(pipeline.getStages(), "Pipeline should have stages");
        assertEquals(2, pipeline.getStages().size(), "Pipeline should have 2 stages");
        
        PreviewStage systemStage = pipeline.getSystemStage();
        assertNotNull(systemStage, "Pipeline should have system stage");
        assertEquals("com.example.system", systemStage.getSourcesPackage());
        
        PreviewStage[] stages = pipeline.getStages().toArray(new PreviewStage[0]);
        PreviewStage firstStage = stages[0];
        assertEquals("init", firstStage.getName());
        assertEquals(StageType.DEFAULT, firstStage.getType());
    }

    // Helper methods using reflection to test the internal pipeline building logic
    private PreviewPipeline buildPipelineFromAnnotation(FlamingockAnnotationProcessor processor, Flamingock annotation, Map<String, List<AbstractPreviewTask>> changeUnits) throws Exception {
        java.lang.reflect.Method method = FlamingockAnnotationProcessor.class.getDeclaredMethod(
            "buildPipelineFromAnnotation", Flamingock.class, Map.class);
        method.setAccessible(true);
        return (PreviewPipeline) method.invoke(processor, annotation, changeUnits);
    }

    private PreviewPipeline callGetPipelineFromProcessChanges(FlamingockAnnotationProcessor processor, Map<String, List<AbstractPreviewTask>> changeUnits, Flamingock annotation) throws Exception {
        java.lang.reflect.Method method = FlamingockAnnotationProcessor.class.getDeclaredMethod(
            "getPipelineFromProcessChanges", Map.class, Flamingock.class);
        method.setAccessible(true);
        return (PreviewPipeline) method.invoke(processor, changeUnits, annotation);
    }

    private PreviewPipeline buildPipelineFromFile(FlamingockAnnotationProcessor processor, Flamingock annotation, Map<String, List<AbstractPreviewTask>> changeUnits) throws Exception {
        // Set up minimal processor state
        setProcessorField(processor, "resourcesRoot", tempDir.toString());
        setProcessorField(processor, "sourceRoots", Collections.singletonList(tempDir.toString()));
        
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

    private void createPipelineYamlFile() throws IOException {
        Path pipelineFile = tempDir.resolve("pipeline.yaml");
        String yamlContent = "pipeline:\n" +
            "  systemStage: com.example.system\n" +
            "  stages:\n" +
            "    - location: com.example.changes\n";
        Files.write(pipelineFile, yamlContent.getBytes());
    }

    private Map<String, List<AbstractPreviewTask>> createMockChangeUnitsMap() {
        Map<String, List<AbstractPreviewTask>> map = new HashMap<>();
        // Create mock tasks for each package so stages can be built
        AbstractPreviewTask mockTask = new AbstractPreviewTask("mock-task", "001", "test-source", false, true, false) {};
        
        map.put("com.example.system", Collections.singletonList(mockTask));
        map.put("com.example.init", Collections.singletonList(mockTask));
        map.put("com.example.migrations", Collections.singletonList(mockTask));
        map.put("com.example.changes", Collections.singletonList(mockTask));
        return map;
    }

    // Mock annotation factories
    private Flamingock createMockAnnotationWithStages() {
        return new MockFlamingockBuilder()
            .withSystemStage("com.example.system")
            .withStages(
                createMockStage("", StageType.DEFAULT, "com.example.init"),
                createMockStage("", StageType.DEFAULT, "com.example.migrations")
            )
            .build();
    }

    private Flamingock createMockAnnotationWithFile(String fileName) {
        return new MockFlamingockBuilder()
            .withPipelineFile(tempDir.resolve(fileName).toString())
            .build();
    }

    private Flamingock createMockAnnotationWithNeitherFileNorStages() {
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
        private String systemStageLocation = "";
        private Stage[] stages = new Stage[0];
        private String pipelineFile = "";

        public MockFlamingockBuilder withSystemStage(String systemStageLocation) {
            this.systemStageLocation = systemStageLocation;
            return this;
        }

        public MockFlamingockBuilder withStages(Stage... stages) {
            this.stages = stages;
            return this;
        }

        public MockFlamingockBuilder withPipelineFile(String pipelineFile) {
            this.pipelineFile = pipelineFile;
            return this;
        }

        public Flamingock build() {
            return new Flamingock() {
                @Override public String systemStage() { 
                    return systemStageLocation;
                }
                @Override public Stage[] stages() { return stages; }
                @Override public String pipelineFile() { return pipelineFile; }
                @Override public io.flamingock.api.SetupType setup() { return io.flamingock.api.SetupType.DEFAULT; }
                @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Flamingock.class; }
            };
        }
    }
}