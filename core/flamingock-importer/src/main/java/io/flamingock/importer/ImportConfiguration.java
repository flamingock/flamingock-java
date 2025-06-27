package io.flamingock.importer;

public class ImportConfiguration {

    private String origin = "mongockChangeLog";
    private boolean failOnEmptyOrigin = true;

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public boolean isFailOnEmptyOrigin() {
        return failOnEmptyOrigin;
    }

    public void setFailOnEmptyOrigin(boolean failOnEmptyOrigin) {
        this.failOnEmptyOrigin = failOnEmptyOrigin;
    }
}
