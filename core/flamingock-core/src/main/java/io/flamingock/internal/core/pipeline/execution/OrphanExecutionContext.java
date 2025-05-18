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

package io.flamingock.internal.core.pipeline.execution;

import java.util.Map;

public class OrphanExecutionContext {

    private final String hostname;

    private final String author;

    private final Map<String, Object> metadata;


    public OrphanExecutionContext(String hostname, String author, Map<String, Object> metadata) {
        this.hostname = hostname;
        this.author = author;
        this.metadata = metadata;
    }

    public String getHostname() {
        return hostname;
    }

    public String getAuthor() {
        return author;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
