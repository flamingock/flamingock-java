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

package io.flamingock.importer.mongodb.legacy;

import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.api.annotations.Execution;
import io.mongock.api.annotations.BeforeExecution;

@ChangeUnit(id = "client-updater", order = "2", author = "mongock")
public class ClientUpdater {

    @BeforeExecution
    public void beforeExecution() {
        System.out.println("Client Initializer");
    }

    @Execution
    public void execution() {
        System.out.println("Client Initializer");
    }
}
