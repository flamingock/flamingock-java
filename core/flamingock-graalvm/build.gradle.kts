val generalUtilVersion: String by extra
dependencies {
    api("io.flamingock:flamingock-general-util:${generalUtilVersion}")

    // GraalVM SDK for native image support — compileOnly because it's only needed at
    // native-image build time, never at JVM runtime.
    compileOnly("org.graalvm.sdk:graal-sdk:22.3.0")
}

description = "GraalVM native image support and configuration for Flamingock applications"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
