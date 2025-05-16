package io.flamingock.core.system;

import io.flamingock.core.context.ContextContributor;
import io.flamingock.core.context.ContextInitializable;
import io.flamingock.core.context.DependencyContext;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.context.Dependency;

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

public interface SystemModuleManager extends ContextInitializable, ContextContributor {

    void add(SystemModule module);

    Iterable<SystemModule> getModules();

    void initialize(DependencyContext dependencyContext);

    default List<PreviewStage> getSortedSystemStagesBefore() {
        return Helper.getSortedSystemStages(getModules(), true);
    }

    default List<PreviewStage> getSortedSystemStagesAfter() {
        return Helper.getSortedSystemStages(getModules(), false);
    }

    final class Helper {

        private Helper() {
        }

        private static List<PreviewStage> getSortedSystemStages(Iterable<SystemModule> modulesIterable, boolean isBefore) {

            Collection<SystemModule> modules = Collection.class.isAssignableFrom(modulesIterable.getClass())
                    ? (Collection<SystemModule>) modulesIterable
                    : fromIterableToCollection(modulesIterable);

            List<SystemModule> sortedModules = new ArrayList<>(modules);
            Collections.sort(sortedModules);
            Stream<SystemModule> stream = sortedModules.stream();
            stream = isBefore ? stream.filter(SystemModule::isBeforeUserStages) : stream.filter(m -> !m.isBeforeUserStages());

            return stream.map(SystemModule::getStage).collect(Collectors.toList());
        }

        private static  ArrayList<SystemModule> fromIterableToCollection(Iterable<SystemModule> modulesIterable) {
            return StreamSupport.stream(modulesIterable.spliterator(), false).collect(Collectors.toCollection(ArrayList::new));
        }
    }

}
