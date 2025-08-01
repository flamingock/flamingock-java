plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        target("src/**/*.java")
        licenseHeader("""/*
 * Copyright ${'$'}YEAR Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */""")
    }
    
    kotlin {
        target("src/**/*.kt")
        licenseHeader("""/*
 * Copyright ${'$'}YEAR Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */""")
    }
}

// Disconnect spotless from build lifecycle - make it manual only
afterEvaluate {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn.removeIf { it.toString().contains("spotless") }
    }
    tasks.matching { it.name.startsWith("spotless") && it.name.contains("Check") }.configureEach {
        group = "verification"
        description = "Check license headers (manual task - not part of build)"
    }
}