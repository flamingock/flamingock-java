package io.flamingock.graalvm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.core.metadata.Constants.FULL_GRAALVM_REFLECT_CLASSES_PATH;

public final class FileUtil {

    private FileUtil(){}

    static List<String> getClassesForRegistration() {
        List<String> classesToRegister = fromFile(FULL_GRAALVM_REFLECT_CLASSES_PATH);
        if (!classesToRegister.isEmpty()) {
            return classesToRegister;
        }
        throw new RuntimeException("Flamingock: No valid reflection file found");
    }

    private static List<String> fromFile(String filePath) {
        ClassLoader classLoader = RegistrationFeature.class.getClassLoader();

        try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
            if (inputStream == null) {
                return Collections.emptyList();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }

        } catch (IOException e) {
            throw new RuntimeException(String.format("Error reading file `%s`", filePath), e);
        }
    }
}
