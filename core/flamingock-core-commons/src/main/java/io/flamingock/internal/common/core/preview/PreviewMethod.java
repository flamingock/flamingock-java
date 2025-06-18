package io.flamingock.internal.common.core.preview;

import java.util.List;

public class PreviewMethod {
    private String name;
    private List<String> parameterTypes;

    public PreviewMethod() {
    }

    public PreviewMethod(String name, List<String> parameterTypes) {
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    public String getName() {
        return name;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setParameterTypes(List<String> parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    @Override
    public String toString() {
        return name + "(" + String.join(", ", parameterTypes) + ")";
    }
}
