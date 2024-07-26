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

package io.flamingock.core.pipeline;

import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.task.descriptor.ReflectionTaskDescriptorBuilder;
import io.flamingock.core.task.descriptor.TemplatedTaskDescriptorBuilder;
import io.flamingock.core.task.filter.TaskFilter;
import io.flamingock.template.TemplatedTaskDefinition;
import io.flamingock.commons.utils.FileUtil;
import io.flamingock.commons.utils.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class represents the process defined by the user in the builder, yaml, etc.
 * It doesn't necessary contain directly the tasks, it can contain the code packages, etc.
 */
public class Stage {
    //TODO should we make this i
    private String name;

    private Collection<String> codePackages;

    private Collection<String> fileDirectories;

    private Collection<TaskFilter> filters;
    private boolean parallel = false;

    public Stage() {
    }
    public Stage(String name) {
        this.name  = name;
    }

    public Stage(Stage prototype) {
        this(prototype.getName(), prototype.getCodePackages(), prototype.getFileDirectories(), prototype.getFilters(), prototype.isParallel());
    }

    public Stage(String name, Collection<String> codePackages, Collection<String> fileDirectories, Collection<TaskFilter> filters, boolean parallel) {
        this.name = name;
        this.codePackages = codePackages != null ? new LinkedHashSet<>(codePackages) : null;
        this.fileDirectories = fileDirectories != null ? new LinkedHashSet<>(fileDirectories) : null;
        this.filters = filters != null ? new LinkedHashSet<>(filters) : null;
        this.parallel = parallel;
    }



    public String getName() {
        return name;
    }

    public Stage setName(String name) {
        this.name = name;
        return this;
    }

    public Collection<String> getCodePackages() {
        return codePackages;
    }

    public Stage setCodePackages(Collection<String> codePackages) {
        this.codePackages = codePackages;
        return this;
    }

    public Collection<String> getFileDirectories() {
        return fileDirectories;
    }

    public Stage setFileDirectories(Collection<String> fileDirectories) {
        this.fileDirectories = fileDirectories;
        return this;
    }

    public boolean isParallel() {
        return parallel;
    }

    public Stage setParallel(boolean parallel) {
        this.parallel = parallel;
        return this;
    }

    private Collection<TaskFilter> getFilters() {
        return filters != null ? filters : Collections.emptyList();
    }

    public Stage addFilters(Collection<TaskFilter> filters) {
        Collection<TaskFilter> allFilters = this.filters != null ? new LinkedList<>(this.filters) : new LinkedHashSet<>();
        if (filters != null) allFilters.addAll(filters);
        if(filters != null) {
            filters.forEach(this::addFilter);
        }
        return this;
    }

    public Stage addFilter(TaskFilter filter) {
        if(filters == null) {
            filters = new LinkedHashSet<>();
        }
        filters.add(filter);
        return this;
    }

    public Stage addCodePackage(String codePackage) {
        if(codePackages == null) {
            codePackages = new LinkedHashSet<>();
        }
        codePackages.add(codePackage);
        return this;
    }

    public Stage addFileDirectory(String fileDirectory) {
        if(fileDirectories == null) {
            fileDirectories = new LinkedHashSet<>();
        }
        fileDirectories.add(fileDirectory);
        return this;
    }

    /*
     * Depending on the tasks inside the package or some field in the yaml, it returns a SingleLoadedStage
     * or ParallelSingleLoadedProcess.
     * <br />
     *
     * @return a sorted SingleLoadedStage, non-sorted SingleLoadedStage or a ParallelSingleLoadedProcess(non sorted),
     * depending on the task in the scanPackage,or some field in the yaml.

     * It loads the definition from the source(scanPackage, yaml definition, etc.) and returns the LoadedStage
     * with contain the task Definition.
     * <br />
     * This method can decide up to some level, which type of process is loaded. For example in the case of 'SingleStageDefinition',
     * depending on the tasks inside the package or some field in the yaml, it returns a SingleLoadedStage or ParallelSingleLoadedProcess.
     * <br />
     *
     * @return the LoadedStage with contain the task Definition
     */
    public LoadedStage load() {
        Collection<TaskFilter> filters = getFilters();
        Collection<TaskDescriptor> descriptors = new ArrayList<>(getFilteredDescriptorsFromCodePackages(codePackages, filters));
        descriptors.addAll(getFilteredDescriptorsFromDirectory(fileDirectories, filters));

        if (descriptors.stream().allMatch(TaskDescriptor::isSortable)) {
            //if all descriptors are sorted, we return a sorted collection
            return new LoadedStage(name,descriptors.stream().sorted().collect(Collectors.toList()), parallel);

        } else if (descriptors.parallelStream().anyMatch(TaskDescriptor::isSortable)) {
            //if at least one of them are sorted, but not all. An exception is thrown
            throw new IllegalArgumentException("Either all tasks are ordered or none is");

        } else {
            //if none of the tasks are sorted, an unsorted collection is returned
            return new LoadedStage(name, descriptors, parallel);
        }
    }

    private static Collection<TaskDescriptor> getFilteredDescriptorsFromCodePackages(Collection<String> codePackages, Collection<TaskFilter> filters) {
        if(codePackages == null) {
            return Collections.emptyList();
        }
        return codePackages.
                stream()
                .map(packagePath -> ReflectionUtil.loadClassesFromPackage(packagePath, ChangeUnit.class))
                .flatMap(Collection::stream)
                .filter(source -> filters.stream().allMatch(filter -> filter.filter(source)))
                .map(ReflectionTaskDescriptorBuilder.recycledBuilder()::setSource)
                .map(ReflectionTaskDescriptorBuilder::build)
                .collect(Collectors.toList());
    }

    private static Collection<TaskDescriptor> getFilteredDescriptorsFromDirectory(Collection<String> directories, Collection<TaskFilter> filters) {

        if(directories == null) {
            return Collections.emptyList();
        }
        return directories.stream()
                .map(directoryPath -> FileUtil.loadFilesFromDirectory(directoryPath, Stage.class.getClassLoader()))
                .flatMap(Collection::stream)
                .map(file -> FileUtil.getFromYamlFile(file, TemplatedTaskDefinition.class))
//                .filter(source -> filters.stream().allMatch(filter -> filter.filter(source)))
                .map(TemplatedTaskDescriptorBuilder.recycledBuilder()::setFromDefinition)
                .map(TemplatedTaskDescriptorBuilder::build)
                .collect(Collectors.toList());

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Stage stage = (Stage) o;

        return Objects.equals(name, stage.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }



}
