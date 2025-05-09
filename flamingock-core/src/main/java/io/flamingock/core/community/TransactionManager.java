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

package io.flamingock.core.community;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class TransactionManager<CLIENT_SESSION> {

    private final Map<String, CLIENT_SESSION> sessionMap;
    private final Supplier<CLIENT_SESSION> clientSessionSupplier;

    public TransactionManager(Supplier<CLIENT_SESSION> clientSessionSupplier) {
        this.clientSessionSupplier = clientSessionSupplier;
        sessionMap = new HashMap<>();
    }

    /**
     * Starts a session, if not already created or is closed. Otherwise, it returns the existing one.
     *
     * @param sessionId ClientSession identifier. Will be the taskId most of the time(if not always)
     * @return ClientSession
     */
    public CLIENT_SESSION startSession(String sessionId) {
        return sessionMap.compute(sessionId, (k, currentSession) ->
                currentSession == null ? clientSessionSupplier.get() : currentSession
        );
    }

    public void closeSession(String sessionId) {
        sessionMap.remove(sessionId);
    }

    public Optional<CLIENT_SESSION> getSession(String sessionId) {
        return Optional.ofNullable(sessionMap.get(sessionId));
    }
}
