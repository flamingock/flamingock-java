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

package io.flamingock.core.preview;

import io.flamingock.commons.utils.FileUtil;
import io.flamingock.core.preview.builder.PreviewTaskBuilder;
import io.flamingock.core.api.template.ChangeFileDescriptor;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents the process defined by the user in the builder, yaml, etc.
 * It doesn't necessary contain directly the tasks, it can contain the code packages, etc.
 */

public class PreviewStage {

    private String name;

    private String description;

    private String sourcesPackage;

    private String resourcesDir;

    private Collection<? extends AbstractPreviewTask> tasks;

    private boolean parallel;

    public PreviewStage() {
    }

    //TODO it shouldn't be public
    public PreviewStage(String name,
                        String description,
                        String sourcesPackage,
                        String resourcesDir,
                        Collection<? extends AbstractPreviewTask> tasks,
                        boolean parallel) {
        this.name = name;
        this.description = description;
        this.sourcesPackage = sourcesPackage;
        this.resourcesDir = resourcesDir;
        this.tasks = tasks;
        this.parallel = parallel;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourcesPackage() {
        return sourcesPackage;
    }

    public void setSourcesPackage(String sourcesPackage) {
        this.sourcesPackage = sourcesPackage;
    }

    public String getResourcesDir() {
        return resourcesDir;
    }

    public void setResourcesDir(String resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    public Collection<? extends AbstractPreviewTask> getTasks() {
        return tasks;
    }

    public void setTasks(Collection<? extends AbstractPreviewTask> tasks) {
        this.tasks = tasks;
    }

    public boolean isParallel() {
        return parallel;
    }

    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreviewStage stage = (PreviewStage) o;
        return Objects.equals(name, stage.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "PreviewStage{" + "name='" + name + '\'' +
                ", tasks=" + tasks +
                ", parallel=" + parallel +
                '}';
    }


    /**
     * Builder for constructing {@link PreviewStage} instances.
     * <p>
     * A {@code PreviewStage} represents a stage containing one or more {@link AbstractPreviewTask} units,
     * which can be defined either:
     * <ul>
     *     <li>Explicitly via {@link #setChanges(Collection)}</li>
     *     <li>Implicitly by parsing templated YAML files located in a specified resources directory or source package</li>
     * </ul>
     * <p>
     * If tasks are to be loaded from a source package, both {@link #setSourcesPackage(String)} and
     * {@link #setSourcesRoots(Collection)} must be provided. Optionally, {@link #setResourcesRoot(String)} should be set
     * to specify the root of the source tree (e.g., {@code src/main/java}, {@code src/main/kotlin}, etc.).
     * <p>
     * The builder enforces consistency in task ordering: if all tasks are {@link AbstractPreviewTask#isSortable() sortable},
     * they will be sorted; if only some are sortable, an exception will be thrown.
     */
    public static class Builder {

        private String name;
        private String description;
        private String resourcesDir;
        private String sourcesPackage;
        private String resourcesRoot;
        private Collection<String> sourcesRoots;
        private Collection<? extends AbstractPreviewTask> changes;
        private boolean parallel = false;

        private Builder() {
        }

        /**
         * Sets the name of the stage. This is required before calling {@link #build()}.
         *
         * @param name the name of the stage
         * @return this builder instance
         */
        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the stage.
         *
         * @param description the description of the stage
         * @return this builder instance
         */
        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the collection of source root directories (e.g., {@code src/main/java}, {@code src/main/kotlin}).
         * Used in conjunction with {@link #setSourcesPackage(String)} to locate YAML change units.
         *
         * @param sourcesRoots collection of source root paths
         * @return this builder instance
         */
        public Builder setSourcesRoots(Collection<String> sourcesRoots) {
            this.sourcesRoots = sourcesRoots;
            return this;
        }

        /**
         * Sets the fully qualified package name where YAML change units reside (e.g., {@code com.example.changes}).
         * Must be used together with {@link #setSourcesRoots(Collection)}.
         *
         * @param sourcesPackage the package name
         * @return this builder instance
         */
        public Builder setSourcesPackage(String sourcesPackage) {
            this.sourcesPackage = sourcesPackage;
            return this;
        }

        /**
         * Sets the root directory of the resources or source files (e.g., {@code src/main/java}).
         * Used to resolve the relative path to YAML resources when combined with {@link #setResourcesDir(String)}.
         *
         * @param resourcesRoot the root directory
         * @return this builder instance
         */
        public Builder setResourcesRoot(String resourcesRoot) {
            this.resourcesRoot = resourcesRoot;
            return this;
        }

        /**
         * Sets the relative directory path inside the resources or source root where templated YAML files are located.
         *
         * @param resourcesDir the directory inside the resources root
         * @return this builder instance
         */
        public Builder setResourcesDir(String resourcesDir) {
            this.resourcesDir = resourcesDir;
            return this;
        }

        /**
         * Sets a collection of {@link AbstractPreviewTask} implementations directly.
         *
         * @param changes the collection of change unit task classes
         * @return this builder instance
         */
        public Builder setChanges(Collection<? extends AbstractPreviewTask> changes) {
            this.changes = changes;
            return this;
        }

        /**
         * Sets whether tasks should be executed in parallel.
         *
         * @param parallel {@code true} to enable parallel execution; {@code false} otherwise
         * @return this builder instance
         */
        public Builder setParallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        /**
         * Builds and returns a new {@link PreviewStage} instance based on the configured parameters.
         *
         * @return a new {@code PreviewStage}
         * @throws RuntimeException if the name is not set or no change units are provided
         */
        public PreviewStage build() {

            Collection<File> resourcesDirectories = new LinkedList<>();

            if (resourcesDir != null) {
                File resourcesDirectory = FileUtil.getFile(resourcesRoot, resourcesDir, false);
                if (FileUtil.isExistingDir(resourcesDirectory)) {
                    resourcesDirectories.add(resourcesDirectory);
                }
            }

            if (sourcesPackage != null && sourcesRoots != null) {
                String formattedSourcePackage = sourcesPackage.replace(".", "/");
                sourcesRoots.stream()
                        .map(sourceRoot -> FileUtil.getFile(sourceRoot, formattedSourcePackage, false))
                        .filter(FileUtil::isExistingDir)
                        .forEach(resourcesDirectories::add);
            }

            if (name == null || name.isEmpty()) {
                throw new RuntimeException("Stage requires name");
            }

            Collection<? extends AbstractPreviewTask> changeUnitClassesList = changes != null
                    ? changes
                    : Collections.emptyList();

            if (resourcesDirectories.isEmpty() && changeUnitClassesList.isEmpty()) {
                throw new RuntimeException("No changeUnits provided for stage: " + name);
            }

            Collection<AbstractPreviewTask> templatedTasksDescriptors = getTemplatedTaskDescriptors(resourcesDirectories);
            Collection<AbstractPreviewTask> allDescriptors = mergeDescriptors(templatedTasksDescriptors, changeUnitClassesList);

            return new PreviewStage(name, description, sourcesPackage, resourcesDir, allDescriptors, parallel);
        }

        /**
         * Merges and returns a single collection of task descriptors from both templated files and provided classes.
         * Ensures all tasks are consistently ordered if applicable.
         *
         * @param templatedDescriptors     tasks parsed from YAML files
         * @param descriptorsFromClasses   tasks provided directly
         * @return merged collection of {@link AbstractPreviewTask}
         * @throws IllegalArgumentException if only some tasks are sortable
         */
        private Collection<AbstractPreviewTask> mergeDescriptors(Collection<? extends AbstractPreviewTask> templatedDescriptors,
                                                                 Collection<? extends AbstractPreviewTask> descriptorsFromClasses) {

            Collection<AbstractPreviewTask> descriptors = Stream
                    .concat(templatedDescriptors.stream(), descriptorsFromClasses.stream())
                    .collect(Collectors.toList());

            if (descriptors.stream().allMatch(AbstractPreviewTask::isSortable)) {
                return descriptors.stream().sorted().collect(Collectors.toList());

            } else if (descriptors.parallelStream().anyMatch(AbstractPreviewTask::isSortable)) {
                throw new IllegalArgumentException("Either all tasks are ordered or none is");

            } else {
                return descriptors;
            }
        }

        /**
         * Parses and builds templated {@link AbstractPreviewTask} descriptors from YAML files located in the given directories.
         *
         * @param resourcesDirectories collection of directories to scan
         * @return collection of parsed and built {@link AbstractPreviewTask} instances
         */
        private Collection<AbstractPreviewTask> getTemplatedTaskDescriptors(Collection<File> resourcesDirectories) {
            if (resourcesDirectories == null) {
                return Collections.emptyList();
            }
            return resourcesDirectories
                    .stream()
                    .map(FileUtil::getAllYamlFiles)
                    .flatMap(Collection::stream)
                    .map(file -> FileUtil.getFromYamlFile(file, ChangeFileDescriptor.class))
                    .map(PreviewTaskBuilder::getTemplateBuilder)
                    .map(PreviewTaskBuilder::build)
                    .collect(Collectors.toList());
        }
    }



}
