package io.flamingock.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.flamingock.core.api.annotations.BuildTimeProcessable;
import io.flamingock.core.api.metadata.ChangeUnitMedata;
import io.flamingock.core.api.metadata.Constants;
import io.flamingock.core.api.metadata.FlamingockMetadata;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class FlamingockAnnotationProcessor extends AbstractProcessor {

    private final String logPrefix = "Flamingock annotation processor: ";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(BuildTimeProcessable.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (annotations.isEmpty()) {
            return false;
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, logPrefix + "starting");

        try {

            List<ChangeUnitMedata> changeUnitMetadataList = roundEnv.getElementsAnnotatedWith(BuildTimeProcessable.class)
                    .stream()
                    .map(this::mapToMetadata)
                    .collect(Collectors.toList());

            FlamingockMetadata metadata = new FlamingockMetadata(true, changeUnitMetadataList);
            buildFlamingockMetadataFile(metadata);
            buildRegistrationClasses(metadata);

        } catch (Exception e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, logPrefix + "Failed to write AnnotatedClasses file: " + e.getMessage());
            throw new RuntimeException(logPrefix + "Failed to write AnnotatedClasses file: " + e.getMessage());
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, logPrefix + "Successfully finished Flamingock annotation processor");

        return true;
    }

    private void buildRegistrationClasses(FlamingockMetadata metadata) {
        writeToFile(Constants.GRAALVM_REFLECT_CLASSES_PATH, writer -> {
            for (ChangeUnitMedata changeUnit : metadata.getChangeUnits()) {
                try {
                    writer.write(changeUnit.getClassName());
                    writer.write(System.lineSeparator());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void buildFlamingockMetadataFile(FlamingockMetadata metadata) {
        writeToFile(FlamingockMetadata.FILE_PATH, writer -> {
            try {
                writer.write(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(metadata));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ChangeUnitMedata mapToMetadata(Element element) {
        if (element.getKind() == ElementKind.CLASS) {
            TypeElement typeElement = (TypeElement) element;
            String className = typeElement.getQualifiedName().toString();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, logPrefix + "Processed class: " + className);
            PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
            String packageName = packageElement.getQualifiedName().toString();
//            extractAnnotations(element);
            return new ChangeUnitMedata(className, packageName);
        } else {
            return null;
        }
    }

    private void writeToFile(String filePath, Consumer<Writer> writerConsumer) {

        FileObject file;
        try {
            file = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", filePath);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, logPrefix + "Failed to creating flamingock metadata file: " + e.getMessage());
            throw new RuntimeException(e);
        }
        try (Writer writer = file.openWriter()) {
            writerConsumer.accept(writer);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, logPrefix + "Failed to write AnnotatedClasses file: " + e.getMessage());
            throw new RuntimeException(logPrefix + "Failed to write AnnotatedClasses file: " + e.getMessage());
        }
    }

    private void extractAnnotations(Element element) {
        // Get all annotations on the element
        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();

        for (AnnotationMirror annotationMirror : annotationMirrors) {
            // Get the annotation type (e.g., com.yourcompany.annotations.AdditionalAnnotation)
            TypeMirror annotationType = annotationMirror.getAnnotationType();
            String annotationName = annotationType.toString();

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, logPrefix + "Found annotation[ " + annotationName + "] on element: " + element.getSimpleName());

            // Extract annotation values if needed
            if (annotationName.equals("io.mongock.api.annotations.ChangeUnit")) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirror.getElementValues();

                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                    String key = entry.getKey().getSimpleName().toString();
                    String value = entry.getValue().getValue().toString();
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Annotation value: " + key + " = " + value);
                }
            }
        }
    }

}
