package io.mongock.runner.springboot;


import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
public @interface MongockScanPackages {
    MongockScanPackage[] value();
}
