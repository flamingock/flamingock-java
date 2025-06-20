package io.flamingock.api.task;

public interface ChangeCategoryAware {

    boolean hasCategory(ChangeCategory property);

    default boolean hasAnyCategory(ChangeCategory... properties) {
        for (ChangeCategory property : properties) {
            if (hasCategory(property)) {
                return true;
            }
        }
        return false;
    }

    default boolean hasAllCategories(ChangeCategory... properties) {
        for (ChangeCategory property : properties) {
            if (!hasCategory(property)) {
                return false;
            }
        }
        return true;
    }
}
