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

package io.flamingock.internal.core.pipeline;

/**
 * This class represents the process defined by the user in the builder, yaml, etc.
 * It doesn't necessary contain directly the tasks, it can contain the code packages, etc.
 */
public class Stage {
//
//    //TODO should we make this i
//    private String name;
//    private Collection<String> codePackages;
//    private Collection<String> fileDirectories;
//    private Collection<Class<?>> classes;
//    private Collection<TaskFilter> filters;
//    private boolean parallel = false;
//    public Stage() {
//    }
//
//    public Stage(String name) {
//        this.name = name;
//    }
//
//    public Stage(Stage prototype) {
//        this(prototype.getName(),
//                prototype.getCodePackages(),
//                prototype.getFileDirectories(),
//                prototype.getClasses(),
//                prototype.getFilters(),
//                prototype.isParallel());
//    }
//
//    public Stage(String name,
//                 Collection<String> codePackages,
//                 Collection<String> fileDirectories,
//                 Collection<Class<?>> classes,
//                 Collection<TaskFilter> filters,
//                 boolean parallel) {
//        this.name = name;
//        this.codePackages = codePackages != null ? new LinkedHashSet<>(codePackages) : null;
//        this.fileDirectories = fileDirectories != null ? new LinkedHashSet<>(fileDirectories) : null;
//        this.classes = classes != null ? new LinkedHashSet<>(classes) : null;
//        this.filters = filters != null ? new LinkedHashSet<>(filters) : null;
//        this.parallel = parallel;
//    }
//
//    private static Predicate<AbstractLoadedTask> getFilterOperator(Collection<TaskFilter> filters) {
//        return loadedTask -> filters.stream().allMatch(filter -> filter.filter(loadedTask)) || loadedTask.isSystem();
//
//    }
//
//    private static Collection<AbstractLoadedTask> getFilteredDescriptorsFromCodePackages(Collection<String> codePackages,
//                                                                                         Predicate<AbstractLoadedTask> filterOperator) {
//        if (codePackages == null) {
//            return Collections.emptyList();
//        }
//
//        Collection<Class<?>> classes = codePackages.
//                stream()
//                .map(ExecutionUtils::loadExecutionClassesFromPackage)
//                .flatMap(Collection::stream)
//                .collect(Collectors.toList());
////        if(metadata != null) {
////            classes = codePackages
////                    .stream()
////                    .map(metadata::getChangeUnitsByPackage)
////                    .flatMap(Collection::stream)
////                    .map(changeUnitMedata -> {
////                        try {
////                            return Stage.class.getClassLoader().loadClass(changeUnitMedata.getClassName());
////                        } catch (ClassNotFoundException e) {
////                            throw new RuntimeException(e);
////                        }
////                    })
////                    .collect(Collectors.toList());
////        } else {
////            classes = codePackages.
////                    stream()
////                    .map(ExecutionUtils::loadExecutionClassesFromPackage)
////                    .flatMap(Collection::stream)
////                    .collect(Collectors.toList());
////        }
//
//
//        return getFilteredDescriptorsFromClasses(classes, filterOperator);
//    }
//
//    private static Collection<AbstractLoadedTask> getFilteredDescriptorsFromClasses(Collection<Class<?>> classes, Predicate<AbstractLoadedTask> filterOperator) {
//        if (classes == null) {
//            return Collections.emptyList();
//        }
//        return null;
////        return classes.
////                stream()
////                .filter(ExecutionUtils::isExecutableClass)
////                .map(LoadedTaskBuilder::fromCode)
////                .map(LoadedTaskBuilder::build)
////                .filter(filterOperator)
////                .collect(Collectors.toList());
//    }
//
//    //TODO add filter
//    private static Collection<AbstractLoadedTask> getFilteredDescriptorsFromDirectory(Collection<String> directories, Predicate<AbstractLoadedTask> filterOperator) {
//
//        if (directories == null) {
//            return Collections.emptyList();
//        }
//        return null;
////        return directories.stream()
////                .map(directoryPath -> FileUtil.loadFilesFromDirectory(directoryPath, Stage.class.getClassLoader()))
////                .flatMap(Collection::stream)
////                .map(file -> FileUtil.getFromYamlFile(file, TemplateTaskDefinition.class))
////                .map(LoadedTaskBuilder::fromTemplateDefinition)
////                .map(LoadedTaskBuilder::build)
////                .filter(filterOperator)
////                .collect(Collectors.toList());
//    }
//
//    public String getName() {
//        return name;
//    }
//
//    public Stage setName(String name) {
//        this.name = name;
//        return this;
//    }
//
//    public Collection<String> getCodePackages() {
//        return codePackages;
//    }
//
//    public Stage setCodePackages(Collection<String> codePackages) {
//        this.codePackages = codePackages;
//        return this;
//    }
//
//    public Collection<String> getFileDirectories() {
//        return fileDirectories;
//    }
//
//    public Stage setFileDirectories(Collection<String> fileDirectories) {
//        this.fileDirectories = fileDirectories;
//        return this;
//    }
//
//    public Collection<Class<?>> getClasses() {
//        return classes;
//    }
//
//    public Stage setClasses(Collection<Class<?>> classes) {
//        this.classes = classes;
//        return this;
//    }
//
//    public boolean isParallel() {
//        return parallel;
//    }
//
//    public Stage setParallel(boolean parallel) {
//        this.parallel = parallel;
//        return this;
//    }
//
//    private Collection<TaskFilter> getFilters() {
//        return filters != null ? filters : Collections.emptyList();
//    }
//
//    public Stage addFilters(Collection<TaskFilter> filters) {
//        if (filters != null) {
//            filters.forEach(this::addFilter);
//        }
//        return this;
//    }
//
//    public Stage addFilter(TaskFilter filter) {
//        if (filters == null) {
//            filters = new LinkedHashSet<>();
//        }
//        filters.add(filter);
//        return this;
//    }
//
//    public Stage addCodePackage(String codePackage) {
//        if (codePackages == null) {
//            codePackages = new LinkedHashSet<>();
//        }
//        codePackages.add(codePackage);
//        return this;
//    }
//
//    public Stage addFileDirectory(String fileDirectory) {
//        if (fileDirectories == null) {
//            fileDirectories = new LinkedHashSet<>();
//        }
//        fileDirectories.add(fileDirectory);
//        return this;
//    }
//
//    /*
//     * Depending on the tasks inside the package or some field in the yaml, it returns a SingleLoadedStage
//     * or ParallelSingleLoadedProcess.
//     *
//     *
//     * @return a sorted SingleLoadedStage, non-sorted SingleLoadedStage or a ParallelSingleLoadedProcess(non sorted),
//     * depending on the task in the scanPackage,or some field in the yaml.
//
//     * It loads the definition from the source(scanPackage, yaml definition, etc.) and returns the LoadedStage
//     * with contain the task Definition.
//     *
//     * This method can decide up to some level, which type of process is loaded. For example in the case of 'SingleStageDefinition',
//     * depending on the tasks inside the package or some field in the yaml, it returns a SingleLoadedStage or ParallelSingleLoadedProcess.
//     *
//     *
//     * @return the LoadedStage with contain the task Definition
//     */
//    public LoadedStage load() {
//        Predicate<AbstractLoadedTask> filterOperator = getFilterOperator(getFilters());
//
//        Collection<AbstractLoadedTask> descriptors = new ArrayList<>(getFilteredDescriptorsFromCodePackages(codePackages, filterOperator));
//
//        descriptors.addAll(getFilteredDescriptorsFromClasses(classes, filterOperator));
//
//        descriptors.addAll(getFilteredDescriptorsFromDirectory(fileDirectories, filterOperator));
//
//        if (descriptors.stream().allMatch(TaskDescriptor::isSortable)) {
//            //if all descriptors are sorted, we return a sorted collection
//            return new LoadedStage(name, descriptors.stream().sorted().collect(Collectors.toList()), parallel);
//
//        } else if (descriptors.parallelStream().anyMatch(TaskDescriptor::isSortable)) {
//            //if at least one of them are sorted, but not all. An exception is thrown
//            throw new IllegalArgumentException("Either all tasks are ordered or none is");
//
//        } else {
//            //if none of the tasks are sorted, an unsorted collection is returned
//            return new LoadedStage(name, descriptors, parallel);
//        }
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        Stage stage = (Stage) o;
//        return Objects.equals(name, stage.name);
//    }
//
//    @Override
//    public int hashCode() {
//        return name != null ? name.hashCode() : 0;
//    }
//

}
