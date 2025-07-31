/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
