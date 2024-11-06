package io.flamingock.core.api.metadata;

public class ChangeUnitMedata {

    private String className;

    public ChangeUnitMedata() {
    }

    public ChangeUnitMedata(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    //TODO change this to inject package directly(this doens't work with inner classes)
    public String getPackage() {
        int lastIndex = className.lastIndexOf(".");
        return lastIndex > 0 ? className.substring(0, lastIndex) : "";
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChangeUnitMedata that = (ChangeUnitMedata) o;

        return className.equals(that.className);
    }

    @Override
    public int hashCode() {
        return className.hashCode();
    }

    @Override
    public String toString() {
        return "ChangeUnitMedata{" + "className='" + className + '\'' + '}';
    }
}
