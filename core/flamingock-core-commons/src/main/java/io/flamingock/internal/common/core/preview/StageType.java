package io.flamingock.internal.common.core.preview;

public enum StageType {
    DEFAULT,
    LEGACY("legacy"),
    SYSTEM("importer");

    private final String alias;

    StageType(String alias) {
        this.alias = alias;
    }

    StageType() {
        this.alias = null;
    }

    public static StageType from(String name) {
        if (name == null || name.isEmpty()) {
            return DEFAULT;
        }
        for (StageType stageType : StageType.values()) {
            if (name.equals(stageType.alias)) {
                return stageType;
            }
        }
        throw new IllegalArgumentException("No such stage type: " + name);
    }
}
