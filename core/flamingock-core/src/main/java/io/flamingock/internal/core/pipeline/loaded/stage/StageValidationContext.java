package io.flamingock.internal.core.pipeline.loaded.stage;

public class StageValidationContext {

    public enum SortType {
        UNSORTED, SEQUENTIAL_SIMPLE, SEQUENTIAL_FORMATTED;

        public boolean isSorted() {
            return this != UNSORTED;
        }
    }

    private final SortType sortType;

    public static Builder builder() {
        return new Builder();
    }

    private StageValidationContext(SortType sortType) {
        this.sortType = sortType;
    }

    public SortType getSortType() {
        return sortType;
    }

    public static class Builder {
        private SortType sorted = SortType.SEQUENTIAL_FORMATTED;

        public Builder setSorted(SortType sorted) {
            this.sorted = sorted;
            return this;
        }

        public StageValidationContext build() {
            return new StageValidationContext(sorted);
        }
    }
}
