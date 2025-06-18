package io.flamingock.internal.common.core.error.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ValidationResult {
    private final String title;
    private final List<ValidationError> errors = new ArrayList<>();

    public ValidationResult(String title) {
        this.title = title;
    }

    public void add(ValidationError error) {
        errors.add(error);
    }

    public void addAll(Collection<ValidationError> errorList) {
        errors.addAll(errorList);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public String formatMessage() {
        String body = String.join("\n\t- ", errors.stream().map(ValidationError::getMessage).collect(Collectors.toList()));
        return String.format("%s:\n\t- %s", title, body);
    }
}

