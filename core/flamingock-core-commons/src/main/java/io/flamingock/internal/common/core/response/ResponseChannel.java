/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.response;

/**
 * Interface for writing operation results to a communication channel.
 * This abstraction allows different implementations for file-based,
 * stdout-based, or network-based result communication.
 */
public interface ResponseChannel {

    /**
     * Writes the response envelope to the channel.
     *
     * @param envelope the response envelope to write
     * @throws ResponseChannelException if writing fails
     */
    void write(ResponseEnvelope envelope) throws ResponseChannelException;

    /**
     * Closes the channel and releases any resources.
     */
    void close();
}
