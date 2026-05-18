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
package io.flamingock.internal.common.core.util;

import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.util.JsonObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Thin Jackson wrapper for {@link FlamingockMetadata}. The fixed-path discovery used in
 * Phase 1 has been replaced by per-module SPI discovery in
 * {@code io.flamingock.internal.common.core.metadata.MetadataLoader}; this class now exists
 * only to host the deserialization step that the loader composes with.
 */
public final class Deserializer {

    private Deserializer() {
    }

    /**
     * Read a {@link FlamingockMetadata} JSON document from a stream. Caller owns the stream
     * and is responsible for closing it.
     */
    public static FlamingockMetadata readFromStream(InputStream stream) throws IOException {
        return JsonObjectMapper.DEFAULT_INSTANCE.readValue(stream, FlamingockMetadata.class);
    }
}
