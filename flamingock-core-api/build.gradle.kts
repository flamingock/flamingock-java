val jacksonVersion = "2.16.0"
dependencies {
    implementation(project(":utils"))

    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
}

description = "${project.name}'s description"