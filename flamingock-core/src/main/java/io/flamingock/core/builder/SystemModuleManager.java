package io.flamingock.core.builder;

import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.system.SystemModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface SystemModuleManager<T extends SystemModule> {

    void add(T module);

    Iterable<T> getModules();

    default Iterable<Dependency> getDependencies() {
        Set<Dependency> dependencies = new HashSet<>();
        getModules().forEach(m -> dependencies.addAll(m.getDependencies()));
        return new LinkedList<>(dependencies);
    }

    default List<PreviewStage> getSortedSystemStagesBefore() {
        return Helper.getSortedSystemStages(getModules(), true);
    }

    default List<PreviewStage> getSortedSystemStagesAfter() {
        return Helper.getSortedSystemStages(getModules(), false);
    }

    final class Helper {

        private Helper() {
        }

        private static <T extends SystemModule> List<PreviewStage> getSortedSystemStages(Iterable<T> modulesIterable, boolean isBefore) {

            Collection<T> modules = Collection.class.isAssignableFrom(modulesIterable.getClass())
                    ? (Collection<T>) modulesIterable
                    : fromIterableToCollection(modulesIterable);

            List<SystemModule> sortedModules = new ArrayList<>(modules);
            Collections.sort(sortedModules);
            Stream<SystemModule> stream = sortedModules.stream();
            stream = isBefore ? stream.filter(SystemModule::isBeforeUserStages) : stream.filter(m -> !m.isBeforeUserStages());

            return stream.map(SystemModule::getStage).collect(Collectors.toList());
        }

        private static <T extends SystemModule> ArrayList<T> fromIterableToCollection(Iterable<T> modulesIterable) {
            return StreamSupport.stream(modulesIterable.spliterator(), false).collect(Collectors.toCollection(ArrayList::new));
        }
    }

}
