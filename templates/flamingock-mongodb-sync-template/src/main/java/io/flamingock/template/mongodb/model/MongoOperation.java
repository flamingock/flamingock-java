/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.mongodb.model;

import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.NonLockGuardedType;
import io.flamingock.template.mongodb.model.operator.MongoOperator;
import org.bson.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@NonLockGuarded(NonLockGuardedType.NONE)
public class MongoOperation {
    private String type;
    private String collection;
    private Map<String, Object> parameters;
    private MongoOperation rollback;

    public String getType() { return type; }

    public String getCollection() { return collection; }

    public Map<String, Object> getParameters() { return parameters; }

    public void setType(String type) { this.type = type; }

    public void setCollection(String collection) { this.collection = collection; }

    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public MongoOperation getRollback() { return rollback; }

    public void setRollback(MongoOperation rollback) { this.rollback = rollback; }

    @SuppressWarnings("unchecked")
    public List<Document> getDocuments() {
        return ((List<Map<String, Object>>) parameters.get("documents"))
                .stream().map(Document::new)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public Document getKeys() {
        return new Document((Map<String, Object>) parameters.get("keys"));
    }



    @SuppressWarnings("unchecked")
    public Document getOptions() {
        return parameters.containsKey("options")
                ? new Document((Map<String, Object>) parameters.get("options"))
                : new Document();
    }

    @SuppressWarnings("unchecked")
    public Document getFilter() {
        return new Document((Map<String, Object>) parameters.get("filter"));
    }

    public String getIndexName() {
        Object value = parameters.get("indexName");
        return value != null ? (String) value : null;
    }

    public String getTarget() {
        return (String) parameters.get("target");
    }

    @SuppressWarnings("unchecked")
    public Document getValidator() {
        Object value = parameters.get("validator");
        return value != null ? new Document((Map<String, Object>) value) : null;
    }

    public String getValidationLevel() {
        return (String) parameters.get("validationLevel");
    }

    public String getValidationAction() {
        return (String) parameters.get("validationAction");
    }

    public String getViewOn() {
        return (String) parameters.get("viewOn");
    }

    @SuppressWarnings("unchecked")
    public List<Document> getPipeline() {
        List<Map<String, Object>> rawPipeline = (List<Map<String, Object>>) parameters.get("pipeline");
        return rawPipeline != null
                ? rawPipeline.stream().map(Document::new).collect(Collectors.toList())
                : null;
    }

    @SuppressWarnings("unchecked")
    public Document getUpdate() {
        return new Document((Map<String, Object>) parameters.get("update"));
    }

    public boolean isMulti() {
        Object multi = parameters.get("multi");
        return multi != null && (Boolean) multi;
    }

    public MongoOperator getOperator(MongoDatabase db) {
        return MongoOperationType.getFromValue(getType()).getOperator(db, this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MongoOperation{");
        sb.append("type='").append(type).append('\'');
        sb.append(", collection='").append(collection).append('\'');
        sb.append(", parameters=").append(parameters);
        if (rollback != null) {
            sb.append(", rollback=").append(rollback);
        }
        sb.append('}');
        return sb.toString();
    }
}