/*
 * Copyright 2024 Flamingock (https://www.flamingock.io)
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
 */
package io.flamingock.gradle

/**
 * Extension for configuring Flamingock in a Gradle project.
 *
 * Usage:
 * ```
 * flamingock {
 *     community()
 *     sql()
 *     mongodb()
 *     dynamodb()
 *     couchbase()
 *     mongock()
 *     springboot()
 *     graalvm()
 * }
 * ```
 */
open class FlamingockExtension {

    internal var isCloudEnabled: Boolean = false
        private set

    internal var isCommunityEnabled: Boolean = false
        private set

    internal var isMongockEnabled: Boolean = false
        private set

    internal var isSpringbootEnabled: Boolean = false
        private set

    internal var isGraalvmEnabled: Boolean = false
        private set

    internal var isSqlEnabled: Boolean = false
        private set

    internal var isMongodbEnabled: Boolean = false
        private set

    internal var isDynamodbEnabled: Boolean = false
        private set

    internal var isCouchbaseEnabled: Boolean = false
        private set

    /**
     * Enables the Cloud edition of Flamingock.
     *
     * This is the default edition. If neither [cloud] nor [community] is called,
     * cloud is activated automatically.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-cloud")`
     */
    fun cloud() {
        isCloudEnabled = true
    }

    /**
     * Enables the Community edition of Flamingock.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-community")`
     */
    fun community() {
        isCommunityEnabled = true
    }

    /**
     * Enables Mongock compatibility for migrating from Mongock to Flamingock.
     *
     * Adds:
     * - `implementation("io.flamingock:mongock-support")`
     * - `annotationProcessor("io.flamingock:mongock-support")`
     */
    fun mongock() {
        isMongockEnabled = true
    }

    /**
     * Enables Spring Boot integration.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-springboot-integration")`
     *
     * Also switches test support from `flamingock-test-support` to
     * `flamingock-springboot-test-support` (which transitively includes the basic one).
     */
    fun springboot() {
        isSpringbootEnabled = true
    }

    /**
     * Enables GraalVM native image support.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-graalvm")`
     */
    fun graalvm() {
        isGraalvmEnabled = true
    }

    /**
     * Enables SQL support.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-sql-template")`
     * - `implementation("io.flamingock:flamingock-sql-targetsystem")`
     */
    fun sql() {
        isSqlEnabled = true
    }

    /**
     * Enables MongoDB support.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-mongodb-sync-template")`
     * - `implementation("io.flamingock:flamingock-mongodb-sync-targetsystem")`
     *
     * If [springboot] is also enabled, additionally adds:
     * - `implementation("io.flamingock:flamingock-mongodb-springdata-targetsystem")`
     */
    fun mongodb() {
        isMongodbEnabled = true
    }

    /**
     * Enables DynamoDB support.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-dynamodb-targetsystem")`
     */
    fun dynamodb() {
        isDynamodbEnabled = true
    }

    /**
     * Enables Couchbase support.
     *
     * Adds:
     * - `implementation("io.flamingock:flamingock-couchbase-targetsystem")`
     */
    fun couchbase() {
        isCouchbaseEnabled = true
    }
}
