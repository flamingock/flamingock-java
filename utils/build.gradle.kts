plugins {
    id("java")
}


val jacksonVersion = "2.16.0"
dependencies {

    implementation("org.reflections:reflections:0.10.1")
//    api("org.objenesis:objenesis:3.2")
    api("org.yaml:snakeyaml:2.2")
//
    api("org.apache.httpcomponents:httpclient:4.5.14")
    
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}