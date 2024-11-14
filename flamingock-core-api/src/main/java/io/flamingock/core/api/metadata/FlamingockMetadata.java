package io.flamingock.core.api.metadata;


import io.flamingock.commons.utils.JsonObjectMapper;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class FlamingockMetadata {

    public static final String FILE_PATH = "META-INF/flamingock/metadata.json";

    private static FlamingockMetadata instance;
    private static boolean alreadyTriedToLoad = false;

    private boolean suppressedProxies;

    private Collection<ChangeUnitMedata> changeUnits;

    public FlamingockMetadata() {
    }

    public FlamingockMetadata(boolean suppressedProxies, Collection<ChangeUnitMedata> changeUnits) {
        this.suppressedProxies = suppressedProxies;
        this.changeUnits = changeUnits;
    }

    public static synchronized Optional<FlamingockMetadata> getInstance() {
        if (!alreadyTriedToLoad) {
            alreadyTriedToLoad = true;
            instance = loadFromFile();
        }
        return Optional.ofNullable(instance);
    }

    @Nullable
    private static FlamingockMetadata loadFromFile() {
        ClassLoader classLoader = FlamingockMetadata.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(FlamingockMetadata.FILE_PATH)) {
            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return JsonObjectMapper.DEFAULT_INSTANCE.readValue(sb.toString(), FlamingockMetadata.class);
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving file " + FlamingockMetadata.FILE_PATH, e);
        }
    }

    public boolean isSuppressedProxies() {
        return suppressedProxies;
    }

    public void setSuppressedProxies(boolean suppressedProxies) {
        this.suppressedProxies = suppressedProxies;
    }

    public Collection<ChangeUnitMedata> getChangeUnits() {
        return changeUnits;
    }

    public void setChangeUnits(Collection<ChangeUnitMedata> changeUnits) {
        this.changeUnits = changeUnits;
    }

    public Collection<ChangeUnitMedata> getChangeUnitsByPackage(String packagePath) {
        return changeUnits.stream()
                .filter(changeUnitMetadata -> packagePath.equals(changeUnitMetadata.getPackageName()))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "FlamingockMetadata{" + "suppressedProxies=" + suppressedProxies +
                ", changeUnits=" + changeUnits +
                '}';
    }
}
