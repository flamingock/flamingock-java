package io.flamingock.graalvm;

import io.flamingock.core.api.metadata.ChangeUnitMedata;
import io.flamingock.core.api.metadata.FlamingockMetadata;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

import static io.flamingock.core.api.metadata.Constants.GRAALVM_REFLECT_CLASSES_PATH;


public class RegistrationFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        registerClass(FlamingockMetadata.class.getCanonicalName());
        registerClass(ChangeUnitMedata.class.getCanonicalName());
        List<String> classesToRegister= fromFile(GRAALVM_REFLECT_CLASSES_PATH);
        classesToRegister.forEach(RegistrationFeature::registerClass);
    }

    private static void registerClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            RuntimeReflection.register(clazz);
            RuntimeReflection.register(clazz.getDeclaredConstructors());
            RuntimeReflection.register(clazz.getDeclaredMethods());
            System.out.printf("Flamingock: Registered class[%s]%n", className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> fromFile(String filePath) {
        ClassLoader classLoader = RegistrationFeature.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
            if (inputStream != null) {
                List<String> classesToRegister = new LinkedList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        classesToRegister.add(line);
                    }
                }
                return classesToRegister;

            } else {
                throw new RuntimeException(String.format("Flamingock: file `%s` not found", GRAALVM_REFLECT_CLASSES_PATH));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
