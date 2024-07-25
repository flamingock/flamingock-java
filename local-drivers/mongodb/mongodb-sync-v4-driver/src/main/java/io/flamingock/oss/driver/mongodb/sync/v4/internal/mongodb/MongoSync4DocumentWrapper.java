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

package io.flamingock.oss.driver.mongodb.sync.v4.internal.mongodb;

import io.flamingock.oss.driver.common.mongodb.DocumentWrapper;
import org.bson.Document;

public class MongoSync4DocumentWrapper implements DocumentWrapper {

    private final Document document;

    public MongoSync4DocumentWrapper(Document document) {
        this.document = document;
    }

    public Document getDocument() {
        return document;
    }

    @Override
    public DocumentWrapper append(String key, Object value) {
        document.append(key, value);
        return this;
    }

    @Override
    public Object get(String key) {
        return document.get(key);
    }

    @Override
    public String getString(String key) {
        return document.getString(key);
    }

    @Override
    public boolean containsKey(String key) {
        return document.containsKey(key);
    }

    @Override
    public Boolean getBoolean(String key) {
        return document.getBoolean(key);
    }

    public boolean getBoolean(Object key, boolean defaultValue) {
        return document.getBoolean(key, defaultValue);
    }

    @Override
    public int size() {
        return document.size();
    }

    @Override
    public DocumentWrapper getWithWrapper(String key) {
        return new MongoSync4DocumentWrapper((Document) get(key));
    }
}
