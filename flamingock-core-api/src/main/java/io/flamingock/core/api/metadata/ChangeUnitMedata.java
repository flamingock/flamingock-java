package io.flamingock.core.api.metadata;

public class ChangeUnitMedata {

    private String packageName;
    private String className;

    public ChangeUnitMedata() {
    }

    public ChangeUnitMedata(String className, String packageName) {
        this.className = className;
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChangeUnitMedata that = (ChangeUnitMedata) o;

        if (!packageName.equals(that.packageName)) return false;
        return className.equals(that.className);
    }

    @Override
    public int hashCode() {
        int result = packageName.hashCode();
        result = 31 * result + className.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ChangeUnitMedata{" + "packageName='" + packageName + '\'' +
                ", className='" + className + '\'' +
                '}';
    }
}
