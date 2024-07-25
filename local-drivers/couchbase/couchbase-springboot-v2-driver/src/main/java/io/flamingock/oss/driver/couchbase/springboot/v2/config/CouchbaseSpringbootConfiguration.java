/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.oss.driver.couchbase.springboot.v2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.flamingock.oss.driver.couchbase.CouchbaseConfiguration;

@Configuration
@ConfigurationProperties("flamingock.couchbase")
public class CouchbaseSpringbootConfiguration extends CouchbaseConfiguration {

    public static CouchbaseSpringbootConfiguration getDefault() {
        return new CouchbaseSpringbootConfiguration();
    }

    /**
     * The custom scope to be used by the Mongock.
     * Can be used for Couchbase Server 7+ to set custom scope on the stored data.
     * 
     * Note: If scope is set the collection needs to be set as well.
     */
    private String scope;
    /**
     * The custom collection to be used by the Mongock.
     * Can be used for Couchbase Server 7+ to set custom collection on the stored
     * data.
     */
    private String collection;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }
}
